package backend.timein;

import backend.employee.EmployeeStatus;
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

public class TimeInService {

    private static final Logger LOGGER = Logger.getLogger(TimeInService.class.getName());

    // 🟢 MODIFIED: Also fetches reason_for_leaving to identify Emergency slips
    private static final String SELECT_SLIP_DETAILS =
            "SELECT employee_id, time_out, reason_for_leaving FROM pass_slips WHERE slip_id = ?";

    private final TimeInValidator validator;
    private final ReturnStatusUpdater returnStatusUpdater;
    private final EventPublisher eventPublisher;
    private final ConnectionPoolManager poolManager;

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
                    "Failed to retrieve pass slip data for Time-In processing. Details: " + e.getMessage()
            );
        }

        LocalDateTime timeIn = LocalDateTime.now();
        String totalDuration;

        try {
            totalDuration = DurationCalculator.calculate(slipDetails.timeOut(), timeIn);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.SEVERE, "Duration calculation failed for slip [" + slipId + "].", e);
            return TimeInResult.systemError("Duration calculation error for slip [" + slipId + "]: " + e.getMessage());
        }

        // 🟢 Pass "RETURNED" since they successfully physically timed in
        String finalStatus = EmployeeStatus.RETURNED.name();

        try {
            // 🟢 MODIFIED: Now passes the dynamic status
            returnStatusUpdater.markAsReturned(
                    slipId, slipDetails.employeeId(), timeIn, totalDuration, finalStatus
            );
        } catch (ReturnStatusUpdaterException e) {
            LOGGER.log(Level.SEVERE, "Return status update failed for slip [" + slipId + "].", e);
            return TimeInResult.systemError("Database error during Time-In processing. Details: " + e.getMessage());
        }

        EmployeeReturnedEvent event = new EmployeeReturnedEvent(
                slipId, slipDetails.employeeId(), timeIn, totalDuration
        );
        eventPublisher.publish(event);

        LOGGER.info(String.format(
                "Time-In completed: slip=[%s], employee=[%s], duration=[%s].",
                slipId, slipDetails.employeeId(), totalDuration
        ));

        return TimeInResult.success(slipId, slipDetails.employeeId(), timeIn, totalDuration);
    }

    private SlipDetails fetchSlipDetails(String slipId) throws SQLException, InterruptedException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();

            try (PreparedStatement stmt = connection.prepareStatement(SELECT_SLIP_DETAILS)) {
                stmt.setString(1, slipId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Pass slip [" + slipId + "] not found.");
                    }

                    String employeeId = rs.getString("employee_id");
                    Timestamp timeOutTimestamp = rs.getTimestamp("time_out");
                    String reason = rs.getString("reason_for_leaving"); // 🟢 ADDED

                    if (timeOutTimestamp == null) {
                        throw new SQLException("Pass slip [" + slipId + "] has a null time_out value.");
                    }

                    return new SlipDetails(employeeId, timeOutTimestamp.toLocalDateTime(), reason);
                }
            }
        } finally {
            poolManager.release(connection);
        }
    }

    // 🟢 MODIFIED: Added reason to the record
    private record SlipDetails(String employeeId, LocalDateTime timeOut, String reason) {}

    public static final class TimeInResult {
        public enum Outcome { SUCCESS, VALIDATION_FAILURE, SYSTEM_ERROR }

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

        static TimeInResult success(String slipId, String employeeId, LocalDateTime timeIn, String totalDuration) {
            return new TimeInResult(Outcome.SUCCESS, slipId, employeeId, timeIn, totalDuration, null);
        }

        static TimeInResult validationFailure(String errorMessage) {
            return new TimeInResult(Outcome.VALIDATION_FAILURE, null, null, null, null, errorMessage);
        }

        static TimeInResult systemError(String errorMessage) {
            return new TimeInResult(Outcome.SYSTEM_ERROR, null, null, null, null, errorMessage);
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