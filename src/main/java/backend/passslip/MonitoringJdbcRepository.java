package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

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

                    records.add(new PassSlipMonitoringRecord(
                            passSlipId,
                            "PS-" + passSlipId,
                            resultSet.getString("employee_id"),
                            resultSet.getString("employee_name"),
                            resultSet.getString("department"),
                            String.valueOf(resultSet.getDate("date_issued")),
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
        if (rawTime == null) {
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