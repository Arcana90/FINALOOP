package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class    PassSlipJdbcRepository {

    public IssuePassSlipResult issuePassSlip(String employeeId, String reason, int issuedByUserId, String expectedTimeOut, String expectedTimeIn) {
        Connection connection = null;

        String cancelOldSlipSql = """
        UPDATE pass_slips 
        SET status = 'Cancelled'::slip_status 
        WHERE employee_id = ? 
          AND date_issued = CURRENT_DATE 
          AND status = 'For Approval'::slip_status
        """;

        String insertNewSlipSql = """
        INSERT INTO pass_slips (
            employee_id,
            reason_for_leaving,
            date_issued,
            time_requested,
            expected_time_out, 
            expected_time_in,  
            status,
            issued_by_user_id
        )
        VALUES (
            ?, ?, CURRENT_DATE, (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time, ?::time, ?::time, 'For Approval'::slip_status, ?
        )
        RETURNING pass_slip_id
        """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            connection.setAutoCommit(false);

            try (PreparedStatement cancelStmt = connection.prepareStatement(cancelOldSlipSql)) {
                cancelStmt.setString(1, employeeId);
                cancelStmt.executeUpdate();
            }

            try (PreparedStatement insertStmt = connection.prepareStatement(insertNewSlipSql)) {
                insertStmt.setString(1, employeeId);
                insertStmt.setString(2, reason);

                // SAFEGUARD: Prevent PSQLException when expected time is null (Emergency Passes)
                if (expectedTimeOut == null || expectedTimeOut.isBlank()) {
                    insertStmt.setNull(3, java.sql.Types.VARCHAR);
                } else {
                    insertStmt.setString(3, expectedTimeOut);
                }

                if (expectedTimeIn == null || expectedTimeIn.isBlank()) {
                    insertStmt.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    insertStmt.setString(4, expectedTimeIn);
                }

                insertStmt.setInt(5, issuedByUserId);

                try (ResultSet resultSet = insertStmt.executeQuery()) {
                    if (resultSet.next()) {
                        connection.commit();
                        return IssuePassSlipResult.success(resultSet.getInt("pass_slip_id"));
                    }
                }
            }

            connection.rollback();
            return IssuePassSlipResult.failed("Pass slip was not created.");

        } catch (Exception e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return IssuePassSlipResult.failed("Database error: " + e.getMessage());
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    /**
     * PROCESSES SLIP APPROVAL:
     * - Emergency: Automatically stamps the `time_out` and sets status to 'Out'.
     * - Normal: ONLY sets status to 'Approved' (Guard handles the time out).
     */
    /**
     * PROCESSES SLIP APPROVAL:
     * - Emergency: Automatically stamps the `time_out` and sets status to 'Excused'.
     * - Normal: ONLY sets status to 'Approved' (Guard handles the time out).
     */
    public boolean approvePassSlip(int passSlipId, boolean isEmergency) {
        Connection connection = null;
        String sql;

        if (isEmergency) {
            // 🚨 UPDATED: Automatically log the actual time out and jump straight to 'Excused' status
            sql = "UPDATE pass_slips SET status = 'Excused'::slip_status, time_out = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time WHERE pass_slip_id = ?";
        } else {
            // 🛂 NORMAL APPROVAL: Only approve it. Wait for the guard to click "Log Time Out".
            sql = "UPDATE pass_slips SET status = 'Approved'::slip_status WHERE pass_slip_id = ?";
        }

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, passSlipId);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }

    public static class IssuePassSlipResult {
        private final boolean success;
        private final int passSlipId;
        private final String errorMessage;

        private IssuePassSlipResult(boolean success, int passSlipId, String errorMessage) {
            this.success = success;
            this.passSlipId = passSlipId;
            this.errorMessage = errorMessage;
        }

        public static IssuePassSlipResult success(int passSlipId) {
            return new IssuePassSlipResult(true, passSlipId, null);
        }

        public static IssuePassSlipResult failed(String errorMessage) {
            return new IssuePassSlipResult(false, -1, errorMessage);
        }

        public boolean isSuccess() { return success; }
        public int getPassSlipId() { return passSlipId; }
        public String getErrorMessage() { return errorMessage; }
    }
}