package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException; // 1. ADDED THIS IMPORT

public class PassSlipJdbcRepository {

    // Method accepts the expected time parameters from your Controller
    public IssuePassSlipResult issuePassSlip(String employeeId, String reason, int issuedByUserId, String expectedTimeOut, String expectedTimeIn) {
        Connection connection = null;

        // STEP 1: SQL to automatically cancel any active 'For Approval' slips for this employee today
        String cancelOldSlipSql = """
        UPDATE pass_slips 
        SET status = 'Cancelled'::slip_status 
        WHERE employee_id = ? 
          AND date_issued = CURRENT_DATE 
          AND status = 'For Approval'::slip_status
        """;

        // STEP 2: SQL to insert the fresh pass slip
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
            connection.setAutoCommit(false); // Start transaction to ensure both operations succeed together

            // Execute Auto-Cancel
            try (PreparedStatement cancelStmt = connection.prepareStatement(cancelOldSlipSql)) {
                cancelStmt.setString(1, employeeId);
                cancelStmt.executeUpdate();
            }

            // Execute New Issuance
            try (PreparedStatement insertStmt = connection.prepareStatement(insertNewSlipSql)) {
                insertStmt.setString(1, employeeId);
                insertStmt.setString(2, reason);
                insertStmt.setString(3, expectedTimeOut);
                insertStmt.setString(4, expectedTimeIn);
                insertStmt.setInt(5, issuedByUserId);

                try (ResultSet resultSet = insertStmt.executeQuery()) {
                    if (resultSet.next()) {
                        connection.commit(); // Commit transaction safely
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
                } catch (SQLException ex) { // 2. FIXED TYPO HERE (Changed longSQLException to SQLException)
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return IssuePassSlipResult.failed("Database error: " + e.getMessage());
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
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

        public boolean isSuccess() {
            return success;
        }

        public int getPassSlipId() {
            return passSlipId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}