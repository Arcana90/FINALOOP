package backend.timein;

import backend.db.ConnectionPoolManager;
import backend.employee.EmployeeStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Specialized database repository class responsible for atomically resolving an active
 * pass slip transaction by transitioning both the pass slip and the associated employee
 * record to the {@link EmployeeStatus#RETURNED} state.
 *
 * <p>The single core operation {@link #markAsReturned(String, String, LocalDateTime, String)}
 * executes a three-statement atomic transaction:
 * <ol>
 *   <li>UPDATE {@code pass_slips}: set {@code time_in}, {@code total_duration}, and
 *       {@code status} to RETURNED for the target slip.</li>
 *   <li>SELECT the {@code employee_id} from the resolved pass slip row.</li>
 *   <li>UPDATE {@code employees}: set {@code status} to RETURNED for the identified employee.</li>
 * </ol>
 * All three operations commit together or roll back entirely.</p>
 */
public class ReturnStatusUpdater {

    private static final Logger LOGGER = Logger.getLogger(ReturnStatusUpdater.class.getName());

    private static final String UPDATE_PASS_SLIP_RETURNED =
            "UPDATE pass_slips " +
            "SET time_in = ?, total_duration = ?, status = ? " +
            "WHERE slip_id = ?";

    private static final String SELECT_EMPLOYEE_ID_FROM_SLIP =
            "SELECT employee_id FROM pass_slips WHERE slip_id = ?";

    private static final String UPDATE_EMPLOYEE_STATUS_RETURNED =
            "UPDATE employees SET status = ? WHERE employee_id = ?";

    private final ConnectionPoolManager poolManager;

    /**
     * Constructs a ReturnStatusUpdater using the shared connection pool.
     *
     * @param poolManager The application-wide connection pool. Must not be null.
     */
    public ReturnStatusUpdater(ConnectionPoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("ConnectionPoolManager must not be null.");
        }
        this.poolManager = poolManager;
    }

    /**
     * Atomically marks a pass slip and its associated employee as {@code RETURNED},
     * persisting the Time-In timestamp and computed duration string.
     *
     * <p>Transaction sequence:
     * <ol>
     *   <li>Disable auto-commit on the acquired connection.</li>
     *   <li>UPDATE pass slip row: set {@code time_in}, {@code total_duration}, {@code status=RETURNED}.</li>
     *   <li>SELECT the employee ID linked to the slip (required for the employee UPDATE).</li>
     *   <li>UPDATE employee row: set {@code status=RETURNED}.</li>
     *   <li>Commit all changes atomically.</li>
     *   <li>Roll back the entire transaction on any failure.</li>
     * </ol>
     * </p>
     *
     * @param slipId        The unique identifier of the pass slip to resolve.
     * @param employeeId    The employee ID to transition to RETURNED (used as a secondary guard).
     * @param timeIn        The system-captured timestamp of the employee's return.
     * @param totalDuration The pre-computed duration string in "Xh Ym" format.
     * @throws ReturnStatusUpdaterException if any SQL operation fails or the transaction
     *                                      cannot be committed.
     */
    public void markAsReturned(String slipId,
                               String employeeId,
                               LocalDateTime timeIn,
                               String totalDuration) throws ReturnStatusUpdaterException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();
            connection.setAutoCommit(false);

            updatePassSlipToReturned(connection, slipId, timeIn, totalDuration);

            String resolvedEmployeeId = fetchEmployeeIdFromSlip(connection, slipId);

            updateEmployeeStatusToReturned(connection, resolvedEmployeeId);

            connection.commit();

            LOGGER.info(String.format(
                    "Time-In committed: slip=[%s], employee=[%s], duration=[%s]. " +
                    "Transaction committed.",
                    slipId, resolvedEmployeeId, totalDuration
            ));

        } catch (SQLException | InterruptedException e) {
            rollbackSilently(connection, slipId);
            throw new ReturnStatusUpdaterException(
                    "Failed to mark slip [" + slipId + "] as RETURNED. Transaction rolled back.", e
            );
        } finally {
            poolManager.release(connection);
        }
    }

    /**
     * Updates the pass slip row with the return timestamp, duration, and RETURNED status.
     *
     * @param connection    Active, transaction-enrolled connection.
     * @param slipId        The slip to update.
     * @param timeIn        The inbound timestamp.
     * @param totalDuration The formatted elapsed duration string.
     * @throws SQLException If the UPDATE fails or no rows are modified.
     */
    private void updatePassSlipToReturned(Connection connection,
                                          String slipId,
                                          LocalDateTime timeIn,
                                          String totalDuration) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_PASS_SLIP_RETURNED)) {
            stmt.setTimestamp(1, Timestamp.valueOf(timeIn));
            stmt.setString(2, totalDuration);
            stmt.setString(3, EmployeeStatus.RETURNED.name());
            stmt.setString(4, slipId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row updated for pass slip [%s], but got %d.",
                        slipId, rowsAffected
                ));
            }

            LOGGER.fine(() -> String.format(
                    "UPDATE pass_slips: slip_id=[%s] -> status=[RETURNED], time_in=[%s].",
                    slipId, timeIn
            ));
        }
    }

    /**
     * Fetches the employee ID associated with the given slip from the database.
     * This provides a reliable link even if the caller's parameter differs.
     *
     * @param connection Active connection within the current transaction.
     * @param slipId     The slip to look up.
     * @return The employee ID string from the database.
     * @throws SQLException If the SELECT fails or the slip is not found.
     */
    private String fetchEmployeeIdFromSlip(Connection connection, String slipId)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_EMPLOYEE_ID_FROM_SLIP)) {
            stmt.setString(1, slipId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Cannot resolve employee ID: pass slip [" + slipId + "] not found " +
                            "after UPDATE. Data integrity issue detected."
                    );
                }
                return rs.getString("employee_id");
            }
        }
    }

    /**
     * Updates the employee row to the RETURNED status within the active transaction.
     *
     * @param connection Active, transaction-enrolled connection.
     * @param employeeId The employee to transition.
     * @throws SQLException If the UPDATE fails or no rows are modified.
     */
    private void updateEmployeeStatusToReturned(Connection connection, String employeeId)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_EMPLOYEE_STATUS_RETURNED)) {
            stmt.setString(1, EmployeeStatus.RETURNED.name());
            stmt.setString(2, employeeId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row updated for employee [%s], but got %d.",
                        employeeId, rowsAffected
                ));
            }

            LOGGER.fine(() -> String.format(
                    "UPDATE employees: employee_id=[%s] -> status=[RETURNED].", employeeId
            ));
        }
    }

    /**
     * Silently rolls back the transaction on the given connection.
     * Logs a severe error if rollback itself fails; never re-throws.
     *
     * @param connection The connection to roll back. Null is safely ignored.
     * @param slipId     The slip ID for log context.
     */
    private void rollbackSilently(Connection connection, String slipId) {
        if (connection == null) {
            return;
        }
        try {
            connection.rollback();
            LOGGER.warning(String.format("Transaction rolled back for slip [%s].", slipId));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE,
                    "Critical: rollback failed for Time-In on slip [" + slipId + "].", e);
        }
    }

    /**
     * Checked exception representing failures within the ReturnStatusUpdater layer.
     */
    public static class ReturnStatusUpdaterException extends Exception {

        public ReturnStatusUpdaterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
