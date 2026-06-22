package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class PassSlipJdbcRepository {

    public IssuePassSlipResult issuePassSlip(String employeeId, String reason, int issuedByUserId, String timeRequested) {
        Connection connection = null;

        // FIXED: Added time_requested to the columns and values
        String sql = """
            INSERT INTO pass_slips (
                employee_id,
                reason_for_leaving,
                date_issued,
                time_requested,
                status,
                issued_by_user_id
            )
            VALUES (
                ?, ?, CURRENT_DATE, ?, 'For Approval'::slip_status, ?
            )
            RETURNING pass_slip_id
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            // FIXED: Using 'stmt' consistently
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, employeeId);
                stmt.setString(2, reason);
                stmt.setString(3, timeRequested); // time_requested column
                stmt.setInt(4, issuedByUserId);   // issued_by_user_id column

                // Use executeQuery() because we have a 'RETURNING' clause in the SQL
                try (ResultSet resultSet = stmt.executeQuery()) {
                    if (resultSet.next()) {
                        return IssuePassSlipResult.success(resultSet.getInt("pass_slip_id"));
                    }
                }
                return IssuePassSlipResult.failed("Pass slip was not created.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            return IssuePassSlipResult.failed("Database error while issuing pass slip.");
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