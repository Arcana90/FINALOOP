package backend.passslip;

import backend.db.ConnectionPoolManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.sql.SQLException;

public class MonitoringJdbcRepository {

    public List<PassSlipMonitoringRecord> findAll() {
        List<PassSlipMonitoringRecord> records = new ArrayList<>();
        Connection connection = null;

        String sql = """
        SELECT 
            ps.pass_slip_id,
            ps.employee_id,
            e.first_name || ' ' || e.last_name AS employee_name,
            e.department,
            ps.reason_for_leaving,
            ps.date_issued,
            ps.time_requested,  
            ps.expected_time_out,
            ps.expected_time_in,
            ps.time_out,        
            ps.time_in,
            ps.duration_minutes,
            ps.status::text AS status
        FROM pass_slips ps
        JOIN employees e ON ps.employee_id = e.employee_id
        ORDER BY ps.pass_slip_id DESC
        """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                // 🟢 FIXED: Removed the double while loop that was skipping records
                while (resultSet.next()) {
                    int passSlipId = resultSet.getInt("pass_slip_id");
                    String reasonForLeaving = resultSet.getString("reason_for_leaving");
                    Integer durationMinutes = (Integer) resultSet.getObject("duration_minutes");

                    records.add(new PassSlipMonitoringRecord(
                            passSlipId,
                            "PS-" + passSlipId,
                            resultSet.getString("employee_id"),
                            resultSet.getString("employee_name"),
                            resultSet.getString("department"),
                            String.valueOf(resultSet.getDate("date_issued")),
                            formatTime(resultSet.getString("time_requested")),
                            formatTime(resultSet.getString("expected_time_out")),
                            formatTime(resultSet.getString("expected_time_in")),
                            formatTime(resultSet.getString("time_out")),
                            formatTime(resultSet.getString("time_in")),
                            formatDuration(durationMinutes),
                            reasonForLeaving,
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return records;
    }

    // 1. Add this method to handle Approving or Rejecting a slip
    public boolean updateSlipStatus(int passSlipId, String newStatus) {
        // Ensure the string perfectly matches your DB ENUM
        String sql = "UPDATE pass_slips SET status = ?::slip_status WHERE pass_slip_id = ?";

        try (java.sql.Connection conn = backend.db.ConnectionPoolManager.getInstance().acquire();
             java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus);
            stmt.setInt(2, passSlipId);

            return stmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 2. Add this if your controller is specifically looking for "getAllMonitoringRecords"
    public java.util.List<PassSlipMonitoringRecord> getAllMonitoringRecords() {
        // Change this from getAllRecords() to findAll()
        return findAll();
    }

    public boolean approvePassSlip(int passSlipId, String employeeId) {
        Connection c = null;

        // FIXED: Only change the status to 'Approved'.
        // We DO NOT set time_out here. The Guard handles that later at the gate.
        String updateSlip = "UPDATE pass_slips SET status = 'Approved'::slip_status WHERE pass_slip_id = ?";

        try {
            c = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement ps1 = c.prepareStatement(updateSlip)) {
                ps1.setInt(1, passSlipId);
                return ps1.executeUpdate() > 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (c != null) {
                ConnectionPoolManager.getInstance().release(c);
            }
        }
    }

    public boolean markAsReturned(int passSlipId) {
        Connection connection = null;

        String sql = """
            UPDATE pass_slips
            SET 
                time_in = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time,
                duration_minutes = GREATEST(
                    0,
                    FLOOR(EXTRACT(EPOCH FROM (
                        (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time - time_out
                    )) / 60)::INT
                ),
                status = 'Returned'::slip_status
            WHERE pass_slip_id = ?
              AND status = 'Out'
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, passSlipId);
                return statement.executeUpdate() > 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    // FIXED: Bulletproof time parsing
    private String formatTime(String rawTime) {
        if (rawTime == null || rawTime.equals("null") || rawTime.isBlank()) {
            return "-";
        }

        // If the database returns a full timestamp (e.g., "2026-06-22 14:30:00")
        // We split it at the space and take the time portion
        if (rawTime.contains(" ")) {
            rawTime = rawTime.split(" ")[1];
        }

        // Now rawTime is safely formatted as "14:30:00" or "14:30"
        if (rawTime.length() >= 5) {
            return rawTime.substring(0, 5); // Returns standard "HH:mm"
        }

        return rawTime;
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null) {
            return "-";
        }

        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;

        if (hours == 0) {
            return remainingMinutes + "m";
        }

        if (remainingMinutes == 0) {
            return hours + "h";
        }

        return hours + "h " + remainingMinutes + "m";
    }

    private String extractType(String reason) {
        if (reason == null || reason.isBlank()) {
            return "-";
        }

        if (reason.startsWith("Type: ")) {
            int endIndex = reason.indexOf(" |");
            if (endIndex > 6) {
                return reason.substring(6, endIndex);
            }
        }

        return "-";
    }
    public boolean markAsOut(int passSlipId) {
        Connection connection = null;

        // Updates the time_out to the exact current time and shifts status to 'Out'
        String sql = """
            UPDATE pass_slips
            SET 
                time_out = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time,
                status = 'Out'::slip_status
            WHERE pass_slip_id = ?
              AND status = 'Approved'
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, passSlipId);
                return statement.executeUpdate() > 0;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    public boolean cancelPassSlip(int passSlipId) {
        String sql = "UPDATE pass_slips SET status = 'Cancelled'::slip_status WHERE pass_slip_id = ?";
        Connection connection = null;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, passSlipId);

                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }
}