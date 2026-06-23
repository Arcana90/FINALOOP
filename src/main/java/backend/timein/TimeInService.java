package backend.timein;

import backend.events.EmployeeReturnedEvent;
import backend.events.EventPublisher;
import backend.shared.DurationCalculator;
import backend.timein.ReturnStatusUpdater.ReturnStatusUpdaterException;
import backend.timein.TimeInValidator.TimeInValidationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

import backend.db.ConnectionPoolManager;

/**
 * Service class orchestrating the complete Time-In (employee return) workflow.
 *
 * <p>Enforces the correct sequence of operations:
 * <ol>
 *   <li>Validate that the selected pass slip is in the {@code OUT} state via {@link TimeInValidator}.</li>
 *   <li>Capture the current system clock as the inbound timestamp.</li>
 *   <li>Retrieve the slip's original {@code time_out} from the database for duration calculation.</li>
 *   <li>Compute the elapsed duration via {@link DurationCalculator}.</li>
 *   <li>Persist the return data atomically via {@link ReturnStatusUpdater}.</li>
 *   <li>Broadcast an {@link EmployeeReturnedEvent} through the {@link EventPublisher}.</li>
 * </ol>
 * </p>
 */
public class TimeInService {

    private static final Logger LOGGER = Logger.getLogger(TimeInService.class.getName());

    private static final String SELECT_SLIP_DETAILS =
            "SELECT employee_id, time_out FROM pass_slips WHERE slip_id = ?";

    private final TimeInValidator validator;
    private final ReturnStatusUpdater returnStatusUpdater;
    private final EventPublisher eventPublisher;
    private final ConnectionPoolManager poolManager;

    /**
     * Constructs the TimeInService with all required collaborators.
     *
     * @param validator           Pre-condition validator for Time-In eligibility.
     * @param returnStatusUpdater Atomic database updater for return state persistence.
     * @param eventPublisher      System-wide event broadcaster.
     * @param poolManager         Connection pool for auxiliary database reads.
     */
    public TimeInService(TimeInValidator validator,
                         ReturnStatusUpdater returnStatusUpdater,
                         EventPublisher eventPublisher,
                         ConnectionPoolManager poolManager) {
        if (validator == null) throw new IllegalArgumentException("TimeInValidator must not be null.");
        if (returnStatusUpdater == null) throw new IllegalArgumentException("ReturnStatusUpdater must not be null.");
        if (eventPublisher == null) throw new IllegalArgumentException("EventPublisher must not be null.");
        if (poolManager == null) throw new IllegalArgumentException("ConnectionPoolManager must not be null.");

        this.validator = validator;
        this.returnStatusUpdater = returnStatusUpdater;
        this.eventPublisher = eventPublisher;
        this.poolManager = poolManager;
    }

    /**
     * Executes the full Time-In workflow for the given pass slip ID.
     *
     * @param slipId The unique identifier of the pass slip selected in the UI table.
     * @return A {@link TimeInResult} indicating success or the specific reason for failure.
     */
    public TimeInResult processTimeIn(String slipId) {
        LOGGER.info(String.format("Time-In requested for slip [%s].", slipId));

        TimeInValidationResult validationResult = validator.validate(slipId);
        if (!validationResult.isValid()) {
            LOGGER.warning(String.format(
                    "Time-In validation failed for slip [%s]: %s",
                    slipId, validationResult.getErrorMessage()
            ));
            return TimeInResult.validationFailure(validationResult.getErrorMessage());
        }

        SlipDetails slipDetails;
        try {
            slipDetails = fetchSlipDetails(slipId);
        } catch (SQLException | InterruptedException e) {
            LOGGER.log(Level.SEVERE,
                    "Failed to retrieve slip details for slip [" + slipId + "].", e);
            return TimeInResult.systemError(
                    "Failed to retrieve pass slip data for Time-In processing. " +
                    "Details: " + e.getMessage()
            );
        }

        LocalDateTime timeIn = LocalDateTime.now();

        String totalDuration;
        try {
            totalDuration = DurationCalculator.calculate(slipDetails.timeOut(), timeIn);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE,
                    "Duration calculation failed for slip [" + slipId + "].", e);
            return TimeInResult.systemError(
                    "Duration calculation error for slip [" + slipId + "]: " + e.getMessage()
            );
        }

        try {
            returnStatusUpdater.markAsReturned(
                    slipId, slipDetails.employeeId(), timeIn, totalDuration
            );
        } catch (ReturnStatusUpdaterException e) {
            LOGGER.log(Level.SEVERE,
                    "Return status update failed for slip [" + slipId + "].", e);
            return TimeInResult.systemError(
                    "Database error during Time-In processing. Transaction rolled back. " +
                    "Details: " + e.getMessage()
            );
        }

        EmployeeReturnedEvent event = new EmployeeReturnedEvent(
                slipId, slipDetails.employeeId(), timeIn, totalDuration
        );
        eventPublisher.publish(event);

        LOGGER.info(String.format(
                "Time-In completed: slip=[%s], employee=[%s], duration=[%s]. Event published.",
                slipId, slipDetails.employeeId(), totalDuration
        ));

        return TimeInResult.success(slipId, slipDetails.employeeId(), timeIn, totalDuration);
    }

    /**
     * Fetches the employee ID and TimeOut timestamp for a given pass slip from the database.
     * This is a read-only query using auto-commit; no transaction management is required here.
     *
     * @param slipId The slip identifier to look up.
     * @return A {@link SlipDetails} record with the employee ID and timeOut timestamp.
     * @throws SQLException         If the query fails or the slip is not found.
     * @throws InterruptedException If the connection acquisition is interrupted.
     */
    private SlipDetails fetchSlipDetails(String slipId) throws SQLException, InterruptedException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();

            try (PreparedStatement stmt = connection.prepareStatement(SELECT_SLIP_DETAILS)) {
                stmt.setString(1, slipId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException(
                                "Pass slip [" + slipId + "] not found when fetching details for Time-In."
                        );
                    }

                    String employeeId = rs.getString("employee_id");
                    Timestamp timeOutTimestamp = rs.getTimestamp("time_out");

                    if (timeOutTimestamp == null) {
                        throw new SQLException(
                                "Pass slip [" + slipId + "] has a null time_out value. " +
                                "Data integrity error."
                        );
                    }

                    return new SlipDetails(employeeId, timeOutTimestamp.toLocalDateTime());
                }
            }
        } finally {
            poolManager.release(connection);
        }
    }

    /**
     * Private record bundling the data fetched from the pass slip row needed for the
     * Time-In workflow.
     *
     * @param employeeId The employee ID linked to the slip.
     * @param timeOut    The departure timestamp stored when the slip was issued.
     */
    private record SlipDetails(String employeeId, LocalDateTime timeOut) {}

    /**
     * Encapsulates the outcome of a Time-In processing attempt.
     */
    public static final class TimeInResult {

        /**
         * Enumeration of possible Time-In processing outcomes.
         */
        public enum Outcome {
            /** Time-In was processed and persisted successfully. */
            SUCCESS,
            /** The target slip failed pre-condition validation. */
            VALIDATION_FAILURE,
            /** An infrastructure or database error prevented processing. */
            SYSTEM_ERROR
        }

        private final Outcome outcome;
        private final String slipId;
        private final String employeeId;
        private final LocalDateTime timeIn;
        private final String totalDuration;
        private final String errorMessage;

        private TimeInResult(Outcome outcome, String slipId, String employeeId,
                             LocalDateTime timeIn, String totalDuration, String errorMessage) {
            this.outcome = outcome;
            this.slipId = slipId;
            this.employeeId = employeeId;
            this.timeIn = timeIn;
            this.totalDuration = totalDuration;
            this.errorMessage = errorMessage;
        }

        static TimeInResult success(String slipId, String employeeId,
                                    LocalDateTime timeIn, String totalDuration) {
            return new TimeInResult(Outcome.SUCCESS, slipId, employeeId,
                                    timeIn, totalDuration, null);
        }

        static TimeInResult validationFailure(String errorMessage) {
            return new TimeInResult(Outcome.VALIDATION_FAILURE, null, null,
                                    null, null, errorMessage);
        }

        static TimeInResult systemError(String errorMessage) {
            return new TimeInResult(Outcome.SYSTEM_ERROR, null, null,
                                    null, null, errorMessage);
        }

        public Outcome getOutcome() { return outcome; }

        public boolean isSuccess() { return outcome == Outcome.SUCCESS; }

        public String getSlipId() { return slipId; }

        public String getEmployeeId() { return employeeId; }

        public LocalDateTime getTimeIn() { return timeIn; }

        public String getTotalDuration() { return totalDuration; }

        public String getErrorMessage() { return errorMessage; }
    }
}
