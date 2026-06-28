package backend.passslip;

import backend.db.ConnectionPoolManager;
import backend.employee.EmployeeStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JDBC repository handling all database operations for the Pass Slip Issuance module.
 */
public class PassSlipRepository {

    private static final Logger LOGGER = Logger.getLogger(PassSlipRepository.class.getName());

    // 1. FIXED SQL: We use Manila time directly in the query. Notice there is no '?' for time_requested.
// 🟢 ADDED: date_issued explicitly forced to Manila time
    private static final String INSERT_PASS_SLIP = """
    INSERT INTO pass_slips 
    (pass_slip_id, employee_id, destination, reason_for_leaving, status, date_issued, time_requested) 
    VALUES (?, ?, ?, ?, ?, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::date, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time)
    """;

    private static final String UPDATE_EMPLOYEE_STATUS =
            "UPDATE employees SET status = ? WHERE employee_id = ?";

    private static final String SELECT_EMPLOYEE_STATUS =
            "SELECT status FROM employees WHERE employee_id = ?";

    private final ConnectionPoolManager poolManager;

    public PassSlipRepository(ConnectionPoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("ConnectionPoolManager must not be null.");
        }
        this.poolManager = poolManager;
    }

    public void issuePassSlip(PassSlipDTO dto) throws PassSlipRepositoryException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();
            connection.setAutoCommit(false);

            insertPassSlipRecord(connection, dto);
            updateEmployeeStatusToForApproval(connection, dto.getEmployeeId());

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

    // Update the method inside PassSlipRepository.java
    private void insertPassSlipRecord(Connection connection, PassSlipDTO dto) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_PASS_SLIP)) {
            // Ensure the ID parsing matches your logic
            int numericSlipId = Integer.parseInt(dto.getSlipId().replace("PS-", ""));

            statement.setInt(1, numericSlipId);
            statement.setString(2, dto.getEmployeeId());
            statement.setString(3, dto.getDestination());
            statement.setString(4, dto.getReason());
            statement.setString(5, dto.getStatus());

            // You do not need to set the 6th parameter because the SQL handles it
            statement.executeUpdate();
        }
    }

    private void updateEmployeeStatusToForApproval(Connection connection, String employeeId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_EMPLOYEE_STATUS)) {
            stmt.setString(1, "FOR_APPROVAL");
            stmt.setString(2, employeeId);
            stmt.executeUpdate();
        }
    }

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

    public static class PassSlipRepositoryException extends Exception {
        public PassSlipRepositoryException(String message) {
            super(message);
        }
        public PassSlipRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}