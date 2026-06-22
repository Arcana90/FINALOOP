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

        // ADDED: ps.time_requested to the SELECT query
        String sql = """
                SELECT 
                    ps.pass_slip_id,
                    ps.employee_id,
                    e.first_name || ' ' || e.last_name AS employee_name,
                    e.department,
                    ps.reason_for_leaving,
                    ps.date_issued,
                    ps.time_requested,
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

                while (resultSet.next()) {
                    int passSlipId = resultSet.getInt("pass_slip_id");
                    String reason = resultSet.getString("reason_for_leaving");
                    Integer durationMinutes = (Integer) resultSet.getObject("duration_minutes");

                    // FETCH the new field from the database
                    String timeRequested = resultSet.getString("time_requested");

                    // ADD timeRequested into the constructor
                    // Inside while(resultSet.next())
                    records.add(new PassSlipMonitoringRecord(
                            passSlipId,
                            "PS-" + passSlipId,
                            resultSet.getString("employee_id"),
                            resultSet.getString("employee_name"),
                            resultSet.getString("department"),
                            String.valueOf(resultSet.getDate("date_issued")),
                            resultSet.getString("time_requested"), // <--- THIS MUST BE HERE
                            formatTime(resultSet.getString("time_out")),
                            formatTime(resultSet.getString("time_in")),
                            formatDuration(durationMinutes),
                            extractType(reason),
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

    public boolean approvePassSlip(int passSlipId, String employeeId) {
        Connection c = null;
        // FIXED: Changed 'WHERE id =' to 'WHERE pass_slip_id ='
        String updateSlip = "UPDATE pass_slips SET time_out = CURRENT_TIMESTAMP, status = 'Out' WHERE pass_slip_id = ?";
        String updateEmp = "UPDATE employees SET status = 'Out' WHERE employee_id = ?";

        try {
            c = ConnectionPoolManager.getInstance().acquire();
            c.setAutoCommit(false); // Start transaction

            // Update Pass Slip
            try (PreparedStatement ps1 = c.prepareStatement(updateSlip)) {
                ps1.setInt(1, passSlipId);
                ps1.executeUpdate();
            }

            // Update Employee Status
            try (PreparedStatement ps2 = c.prepareStatement(updateEmp)) {
                ps2.setString(1, employeeId);
                ps2.executeUpdate();
            }

            c.commit(); // Commit if both succeed
            return true;

        } catch (Exception e) {
            if (c != null) {
                try { c.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
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

    private String formatTime(String rawTime) {
        if (rawTime == null || rawTime.equals("null")) {
            return "-";
        }

        if (rawTime.length() >= 5) {
            return rawTime.substring(0, 5);
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
}