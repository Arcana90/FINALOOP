package backend.passslip;

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
 * JDBC repository handling all database operations for the Pass Slip Issuance module.
 *
 * <p>The core operation {@link #issuePassSlip(PassSlipDTO)} executes an atomic two-statement
 * transaction: inserting the new pass slip row and updating the employee's status to OUT.
 * If either operation fails, the entire transaction is rolled back, preserving data integrity.</p>
 *
 * <p>All connections are acquired from the shared {@link ConnectionPoolManager} and
 * released in {@code finally} blocks to prevent pool starvation under exception conditions.</p>
 */
public class PassSlipRepository {

    private static final Logger LOGGER = Logger.getLogger(PassSlipRepository.class.getName());

    private static final String INSERT_PASS_SLIP =
            "INSERT INTO pass_slips (slip_id, employee_id, destination, reason, time_out, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_EMPLOYEE_STATUS_OUT =
            "UPDATE employees SET status = ? WHERE employee_id = ?";

    private static final String SELECT_EMPLOYEE_STATUS =
            "SELECT status FROM employees WHERE employee_id = ?";

    private final ConnectionPoolManager poolManager;

    /**
     * Constructs a PassSlipRepository using the provided connection pool manager.
     *
     * @param poolManager The application-wide connection pool. Must not be null.
     */
    public PassSlipRepository(ConnectionPoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("ConnectionPoolManager must not be null.");
        }
        this.poolManager = poolManager;
    }

    /**
     * Atomically inserts a new pass slip record and transitions the associated employee's
     * status to {@code OUT} within a single database transaction.
     *
     * <p>Transaction flow:
     * <ol>
     *   <li>Disable auto-commit.</li>
     *   <li>INSERT into {@code pass_slips} with slip ID, employee ID, destination,
     *       reason, TimeOut timestamp, and initial status.</li>
     *   <li>UPDATE {@code employees.status} to 'OUT' for the target employee.</li>
     *   <li>Commit both operations together.</li>
     *   <li>Roll back both if any statement throws.</li>
     * </ol>
     * </p>
     *
     * @param dto The fully populated DTO containing all fields required for the INSERT.
     *            Must have slipId, employeeId, destination, reason, timeOut, and status set.
     * @throws PassSlipRepositoryException if either SQL operation fails or the transaction
     *                                     cannot be committed.
     */
    public void issuePassSlip(PassSlipDTO dto) throws PassSlipRepositoryException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();
            connection.setAutoCommit(false);

            insertPassSlipRecord(connection, dto);
            updateEmployeeStatusToOut(connection, dto.getEmployeeId());

            connection.commit();

            LOGGER.info(String.format(
                    "Pass slip [%s] issued for employee [%s]. Transaction committed.",
                    dto.getSlipId(), dto.getEmployeeId()
            ));

        } catch (SQLException | InterruptedException e) {
            rollbackSilently(connection, dto.getSlipId());
            throw new PassSlipRepositoryException(
                    "Failed to issue pass slip for employee [" + dto.getEmployeeId() + "]. " +
                    "Transaction rolled back.", e
            );
        } finally {
            poolManager.release(connection);
        }
    }

    /**
     * Queries and returns the current {@link EmployeeStatus} for the given employee ID.
     *
     * <p>Used by the service layer to enforce the lifecycle rule that an employee in
     * {@code OUT} state cannot receive a new pass slip.</p>
     *
     * @param employeeId The employee identifier to look up.
     * @return The current {@link EmployeeStatus} of the employee.
     * @throws PassSlipRepositoryException if the employee is not found or a SQL error occurs.
     */
    public EmployeeStatus getEmployeeStatus(String employeeId) throws PassSlipRepositoryException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();

            try (PreparedStatement stmt = connection.prepareStatement(SELECT_EMPLOYEE_STATUS)) {
                stmt.setString(1, employeeId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new PassSlipRepositoryException(
                                "Employee not found in database: [" + employeeId + "]."
                        );
                    }
                    String statusString = rs.getString("status");
                    return EmployeeStatus.valueOf(statusString.toUpperCase());
                }
            }

        } catch (SQLException | InterruptedException e) {
            throw new PassSlipRepositoryException(
                    "Failed to retrieve status for employee [" + employeeId + "].", e
            );
        } finally {
            poolManager.release(connection);
        }
    }

    /**
     * Executes the INSERT statement for the pass slip record within the given connection.
     *
     * @param connection Active, transaction-enrolled connection.
     * @param dto        Source data for all column values.
     * @throws SQLException If the INSERT statement fails.
     */
    private void insertPassSlipRecord(Connection connection, PassSlipDTO dto) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(INSERT_PASS_SLIP)) {
            stmt.setString(1, dto.getSlipId());
            stmt.setString(2, dto.getEmployeeId());
            stmt.setString(3, dto.getDestination());
            stmt.setString(4, dto.getReason());
            stmt.setTimestamp(5, Timestamp.valueOf(dto.getTimeOut()));
            stmt.setString(6, dto.getStatus());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row inserted for pass slip [%s], but got %d.",
                        dto.getSlipId(), rowsAffected
                ));
            }

            LOGGER.fine(() -> String.format(
                    "INSERT pass_slips: slip_id=[%s], employee_id=[%s].",
                    dto.getSlipId(), dto.getEmployeeId()
            ));
        }
    }

    /**
     * Executes the UPDATE statement transitioning the employee's status to OUT
     * within the given connection.
     *
     * @param connection Active, transaction-enrolled connection.
     * @param employeeId The employee to update.
     * @throws SQLException If the UPDATE statement fails or no rows are modified.
     */
    private void updateEmployeeStatusToOut(Connection connection, String employeeId)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_EMPLOYEE_STATUS_OUT)) {
            stmt.setString(1, EmployeeStatus.OUT.name());
            stmt.setString(2, employeeId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row updated for employee [%s], but got %d. " +
                        "Employee may not exist.",
                        employeeId, rowsAffected
                ));
            }

            LOGGER.fine(() -> String.format(
                    "UPDATE employees: employee_id=[%s] -> status=[OUT].", employeeId
            ));
        }
    }

    /**
     * Attempts to roll back the given connection. Logs a warning if rollback itself fails
     * but never re-throws, ensuring the rollback attempt never masks the originating exception.
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
            LOGGER.warning(String.format(
                    "Transaction rolled back for pass slip [%s].", slipId
            ));
        } catch (SQLException rollbackEx) {
            LOGGER.log(Level.SEVERE,
                    "Critical: rollback failed for pass slip [" + slipId + "].", rollbackEx);
        }
    }

    /**
     * Checked exception representing any failure within the PassSlipRepository layer.
     */
    public static class PassSlipRepositoryException extends Exception {

        public PassSlipRepositoryException(String message) {
            super(message);
        }

        public PassSlipRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
