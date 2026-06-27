package backend.timein;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReturnStatusUpdater {

    private static final Logger LOGGER = Logger.getLogger(ReturnStatusUpdater.class.getName());

    // 🟢 MODIFIED: Status is now passed in dynamically, using PostgreSQL casting if needed
    private static final String UPDATE_PASS_SLIP_RETURNED =
            "UPDATE pass_slips " +
                    "SET time_in = ?, total_duration = ?, status = ?::slip_status " +
                    "WHERE slip_id = ?";

    private static final String SELECT_EMPLOYEE_ID_FROM_SLIP =
            "SELECT employee_id FROM pass_slips WHERE slip_id = ?";

    // 🟢 MODIFIED
    private static final String UPDATE_EMPLOYEE_STATUS_RETURNED =
            "UPDATE employees SET status = ?::employee_status WHERE employee_id = ?";

    private final ConnectionPoolManager poolManager;

    public ReturnStatusUpdater(ConnectionPoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("ConnectionPoolManager must not be null.");
        }
        this.poolManager = poolManager;
    }

    /**
     * 🟢 MODIFIED: Added finalStatus parameter.
     */
    public void markAsReturned(String slipId,
                               String employeeId,
                               LocalDateTime timeIn,
                               String totalDuration,
                               String finalStatus) throws ReturnStatusUpdaterException {
        Connection connection = null;
        try {
            connection = poolManager.acquire();
            connection.setAutoCommit(false);

            updatePassSlipToReturned(connection, slipId, timeIn, totalDuration, finalStatus);

            String resolvedEmployeeId = fetchEmployeeIdFromSlip(connection, slipId);

            updateEmployeeStatusToReturned(connection, resolvedEmployeeId, finalStatus);

            connection.commit();

            LOGGER.info(String.format(
                    "Time-In committed: slip=[%s], employee=[%s], duration=[%s], status=[%s].",
                    slipId, resolvedEmployeeId, totalDuration, finalStatus
            ));

        } catch (SQLException | InterruptedException e) {
            rollbackSilently(connection, slipId);
            throw new ReturnStatusUpdaterException(
                    "Failed to mark slip [" + slipId + "] as " + finalStatus + ". Transaction rolled back.", e
            );
        } finally {
            poolManager.release(connection);
        }
    }

    private void updatePassSlipToReturned(Connection connection,
                                          String slipId,
                                          LocalDateTime timeIn,
                                          String totalDuration,
                                          String finalStatus) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_PASS_SLIP_RETURNED)) {

            // 🟢 FIXED: Automatically generate 9:00 PM timestamp for Excused passes
            if (timeIn == null) {
                if ("Excused".equalsIgnoreCase(finalStatus)) {
                    LocalDateTime forcedTimeIn = LocalDateTime.now().withHour(21).withMinute(0).withSecond(0).withNano(0);
                    stmt.setTimestamp(1, Timestamp.valueOf(forcedTimeIn));
                } else {
                    stmt.setNull(1, java.sql.Types.TIMESTAMP);
                }
            } else {
                stmt.setTimestamp(1, Timestamp.valueOf(timeIn));
            }

            stmt.setString(2, totalDuration);
            stmt.setString(3, finalStatus);
            stmt.setString(4, slipId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row updated for pass slip [%s], but got %d.",
                        slipId, rowsAffected
                ));
            }
        }
    }

    private String fetchEmployeeIdFromSlip(Connection connection, String slipId)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_EMPLOYEE_ID_FROM_SLIP)) {
            stmt.setString(1, slipId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                            "Cannot resolve employee ID: pass slip [" + slipId + "] not found."
                    );
                }
                return rs.getString("employee_id");
            }
        }
    }

    private void updateEmployeeStatusToReturned(Connection connection, String employeeId, String finalStatus)
            throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_EMPLOYEE_STATUS_RETURNED)) {
            stmt.setString(1, finalStatus); // 🟢 Dynamic status applied here
            stmt.setString(2, employeeId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException(String.format(
                        "Expected 1 row updated for employee [%s], but got %d.",
                        employeeId, rowsAffected
                ));
            }
        }
    }

    private void rollbackSilently(Connection connection, String slipId) {
        if (connection == null) return;
        try {
            connection.rollback();
            LOGGER.warning(String.format("Transaction rolled back for slip [%s].", slipId));
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Critical: rollback failed for Time-In on slip [" + slipId + "].", e);
        }
    }

    public static class ReturnStatusUpdaterException extends Exception {
        public ReturnStatusUpdaterException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}