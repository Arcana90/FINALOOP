package backend.timein;

import backend.db.ConnectionPoolManager;
import backend.employee.EmployeeStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Validator that confirms a target pass slip exists and is currently in the {@code OUT}
 * state before allowing Time-In processing to proceed.
 *
 * <p>Enforces the lifecycle rule: only slips in the {@code OUT} state may be resolved
 * via Time-In. Slips in {@code AVAILABLE} or {@code RETURNED} state are rejected with
 * a descriptive error message.</p>
 *
 * <p>Database access is performed through the shared {@link ConnectionPoolManager}.
 * Connections are always released in {@code finally} blocks.</p>
 */
public class TimeInValidator {

    private static final Logger LOGGER = Logger.getLogger(TimeInValidator.class.getName());

    private static final String SELECT_SLIP_STATUS =
            "SELECT status FROM pass_slips WHERE slip_id = ?";

    private final ConnectionPoolManager poolManager;

    /**
     * Constructs a TimeInValidator using the shared connection pool.
     *
     * @param poolManager The application-wide connection pool. Must not be null.
     */
    public TimeInValidator(ConnectionPoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("ConnectionPoolManager must not be null.");
        }
        this.poolManager = poolManager;
    }

    /**
     * Validates that the given pass slip ID refers to an existing transaction currently
     * in the {@link EmployeeStatus#OUT} state.
     *
     * @param slipId The unique identifier of the pass slip to validate. Must not be blank.
     * @return A {@link TimeInValidationResult} indicating success or the specific reason
     *         for rejection.
     */
    public TimeInValidationResult validate(String slipId) {
        if (slipId == null || slipId.isBlank()) {
            return TimeInValidationResult.failure("Pass Slip ID must not be empty.");
        }

        Connection connection = null;
        try {
            connection = poolManager.acquire();

            try (PreparedStatement stmt = connection.prepareStatement(SELECT_SLIP_STATUS)) {
                stmt.setString(1, slipId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        LOGGER.warning(String.format(
                                "Time-In validation failed: slip [%s] not found.", slipId
                        ));
                        return TimeInValidationResult.failure(
                                "Pass slip [" + slipId + "] does not exist in the system."
                        );
                    }

                    String statusString = rs.getString("status");
                    EmployeeStatus currentStatus;

                    try {
                        currentStatus = EmployeeStatus.valueOf(statusString.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        LOGGER.warning(String.format(
                                "Time-In validation failed: unrecognized status [%s] for slip [%s].",
                                statusString, slipId
                        ));
                        return TimeInValidationResult.failure(
                                "Pass slip [" + slipId + "] has an unrecognized status: " + statusString
                        );
                    }

                    if (currentStatus != EmployeeStatus.OUT) {
                        LOGGER.warning(String.format(
                                "Time-In validation failed: slip [%s] is in state [%s], not OUT.",
                                slipId, currentStatus
                        ));
                        return TimeInValidationResult.failure(String.format(
                                "Pass slip [%s] cannot be resolved because it is currently in " +
                                "state [%s]. Only slips in OUT state are eligible for Time-In.",
                                slipId, currentStatus
                        ));
                    }

                    LOGGER.fine(() -> String.format(
                            "Time-In validation passed for slip [%s] (status: OUT).", slipId
                    ));
                    return TimeInValidationResult.success();
                }
            }

        } catch (SQLException | InterruptedException e) {
            LOGGER.severe(String.format(
                    "Database error during Time-In validation for slip [%s]: %s",
                    slipId, e.getMessage()
            ));
            return TimeInValidationResult.failure(
                    "Database error while validating pass slip [" + slipId + "]: " + e.getMessage()
            );
        } finally {
            poolManager.release(connection);
        }
    }

    /**
     * Encapsulates the result of a Time-In pre-validation check.
     */
    public static final class TimeInValidationResult {

        private final boolean valid;
        private final String errorMessage;

        private TimeInValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        /**
         * Creates a successful validation result.
         *
         * @return A valid result with no error message.
         */
        public static TimeInValidationResult success() {
            return new TimeInValidationResult(true, null);
        }

        /**
         * Creates a failed validation result with a descriptive error message.
         *
         * @param errorMessage The human-readable reason the validation failed.
         * @return An invalid result with the provided error message.
         */
        public static TimeInValidationResult failure(String errorMessage) {
            return new TimeInValidationResult(false, errorMessage);
        }

        /**
         * Returns {@code true} if the pass slip is eligible for Time-In processing.
         *
         * @return Whether validation passed.
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Returns the error message explaining why validation failed.
         * Returns {@code null} when {@link #isValid()} is {@code true}.
         *
         * @return Descriptive failure reason, or null on success.
         */
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
