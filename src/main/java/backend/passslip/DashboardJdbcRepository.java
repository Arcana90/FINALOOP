package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DashboardJdbcRepository {

    public DashboardSummary getSummary() {
        Connection connection = null;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            int totalEmployees = count(connection, """
                    SELECT COUNT(*)
                    FROM employees
                    WHERE status = 'Active'
                    """);

            // 🟢 CORRECTED: Strictly look for active 'Out' status
            int activePassSlips = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE status = 'Out'
                    """);

            int todaysSlips = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE date_issued = CURRENT_DATE
                    """);

            int totalRecords = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    """);

            int officialBusinessToday = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE date_issued = CURRENT_DATE
                      AND reason_for_leaving ILIKE 'Type: Official Business%'
                    """);

            int personalToday = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE date_issued = CURRENT_DATE
                      AND reason_for_leaving ILIKE 'Type: Personal%'
                    """);

            int returnedToday = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE date_issued = CURRENT_DATE
                      AND status = 'Returned'
                    """);

            // 🟢 CORRECTED: Strictly look for active 'Out' status for today's snapshot
            int stillOutToday = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE date_issued = CURRENT_DATE
                      AND status = 'Out'
                    """);

            List<DashboardSlipRecord> currentlyOut = findCurrentlyOut(connection);
            List<DashboardSlipRecord> recentActivity = findRecentActivity(connection);

            return new DashboardSummary(
                    totalEmployees,
                    activePassSlips,
                    todaysSlips,
                    totalRecords,
                    officialBusinessToday,
                    personalToday,
                    returnedToday,
                    stillOutToday,
                    currentlyOut,
                    recentActivity
            );

        } catch (Exception e) {
            e.printStackTrace();
            return new DashboardSummary(
                    0, 0, 0, 0,
                    0, 0, 0, 0,
                    List.of(),
                    List.of()
            );

        } finally {
            if (connection != null) {
                ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }

    private int count(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }

            return 0;
        }
    }

    private List<DashboardSlipRecord> findCurrentlyOut(Connection connection) throws Exception {
        List<DashboardSlipRecord> records = new ArrayList<>();

        // 🟢 CORRECTED: Only pull employees who are actively 'Out'
        String sql = """
                SELECT
                    ps.pass_slip_id,
                    e.first_name || ' ' || e.last_name AS employee_name,
                    e.department,
                    ps.time_out,
                    ps.time_in,
                    ps.duration_minutes,
                    ps.status::text AS status
                FROM pass_slips ps
                JOIN employees e ON ps.employee_id = e.employee_id
                WHERE ps.status = 'Out'
                ORDER BY ps.pass_slip_id DESC
                LIMIT 5
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                records.add(toDashboardSlipRecord(resultSet));
            }
        }

        return records;
    }

    private List<DashboardSlipRecord> findRecentActivity(Connection connection) throws Exception {
        List<DashboardSlipRecord> records = new ArrayList<>();

        String sql = """
                SELECT
                    ps.pass_slip_id,
                    e.first_name || ' ' || e.last_name AS employee_name,
                    e.department,
                    ps.time_out,
                    ps.time_in,
                    ps.duration_minutes,
                    ps.status::text AS status
                FROM pass_slips ps
                JOIN employees e ON ps.employee_id = e.employee_id
                ORDER BY ps.pass_slip_id DESC
                LIMIT 5
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                records.add(toDashboardSlipRecord(resultSet));
            }
        }

        return records;
    }

    private DashboardSlipRecord toDashboardSlipRecord(ResultSet resultSet) throws Exception {
        Integer durationMinutes = (Integer) resultSet.getObject("duration_minutes");

        return new DashboardSlipRecord(
                resultSet.getInt("pass_slip_id"),
                resultSet.getString("employee_name"),
                resultSet.getString("department"),
                formatTime(resultSet.getString("time_out")),
                formatTime(resultSet.getString("time_in")),
                formatDuration(durationMinutes),
                resultSet.getString("status")
        );
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
}