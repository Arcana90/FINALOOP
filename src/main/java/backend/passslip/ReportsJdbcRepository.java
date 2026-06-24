package backend.passslip;

import backend.db.ConnectionPoolManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportsJdbcRepository {

    public ReportsStats getStats() {
        Connection connection = null;
        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            int totalSlips = count(connection, "SELECT COUNT(*) FROM pass_slips");
            int currentlyOut = count(connection, "SELECT COUNT(*) FROM pass_slips WHERE status = 'Out'");
            int official = count(connection, "SELECT COUNT(*) FROM pass_slips WHERE reason_for_leaving ILIKE 'Type: Official Business%'");
            int avgMinutes = averageDurationMinutes(connection);

            return new ReportsStats(totalSlips, currentlyOut, official, formatDuration(avgMinutes));
        } catch (Exception e) {
            e.printStackTrace();
            return new ReportsStats(0, 0, 0, "0m");
        } finally {
            if (connection != null) {
                ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }

    public List<DailyActivitySummary> findWeeklyDailyActivity() {
        List<DailyActivitySummary> dailySummaries = new ArrayList<>();
        Connection connection = null;

        String sql = """
                SELECT
                    CASE EXTRACT(ISODOW FROM date_issued)
                        WHEN 1 THEN 'Mon'
                        WHEN 2 THEN 'Tue'
                        WHEN 3 THEN 'Wed'
                        WHEN 4 THEN 'Thu'
                        WHEN 5 THEN 'Fri'
                        WHEN 6 THEN 'Sat'
                        WHEN 7 THEN 'Sun'
                    END AS day_of_week,
                    COUNT(*) FILTER (WHERE reason_for_leaving ILIKE 'Type: Official Business%') AS official_count,
                    COUNT(*) FILTER (WHERE reason_for_leaving ILIKE 'Type: Personal%') AS personal_count
                FROM pass_slips
                WHERE date_issued >= DATE_TRUNC('week', CURRENT_DATE)
                  AND EXTRACT(ISODOW FROM date_issued) BETWEEN 1 AND 7
                GROUP BY EXTRACT(ISODOW FROM date_issued)
                ORDER BY EXTRACT(ISODOW FROM date_issued)
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    dailySummaries.add(new DailyActivitySummary(
                            resultSet.getString("day_of_week"),
                            resultSet.getInt("official_count"),
                            resultSet.getInt("personal_count")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                ConnectionPoolManager.getInstance().release(connection);
            }
        }
        return dailySummaries;
    }

    public List<ReportEmployeeSummary> getEmployeeSummaries() {
        List<ReportEmployeeSummary> list = new ArrayList<>();
        String sql = "SELECT e.employee_id, CONCAT(e.first_name, ' ', e.last_name) AS name, " +
                "COUNT(CASE WHEN ps.reason_for_leaving ILIKE 'Type: Personal%' THEN 1 END) AS personal_count, " +
                "COUNT(CASE WHEN ps.reason_for_leaving ILIKE 'Type: Official Business%' THEN 1 END) AS official_count " +
                "FROM employees e " +
                "LEFT JOIN pass_slips ps ON e.employee_id = ps.employee_id " +
                "GROUP BY e.employee_id, e.first_name, e.last_name";

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;

        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ReportEmployeeSummary(
                            rs.getString("employee_id"),
                            rs.getString("name"),
                            rs.getInt("personal_count"),
                            rs.getInt("official_count")
                    ));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }

    public List<EmployeePassSlipDetail> getEmployeePassSlipDetails(String employeeId) {
        List<EmployeePassSlipDetail> list = new ArrayList<>();

        // FIXED: Added TO_CHAR formatting for time_out and time_in columns to show clean AM/PM times
        String sql = "SELECT e.employee_id, CONCAT(e.first_name, ' ', e.last_name) AS name, " +
                "ps.reason_for_leaving, " +
                "TO_CHAR(ps.time_out, 'HH12:MI AM') AS time_out, " +
                "TO_CHAR(ps.time_in, 'HH12:MI AM') AS time_in, " +
                "TO_CHAR(ps.date_issued, 'YYYY-MM-DD') AS expected_time, ps.status " +
                "FROM employees e " +
                "JOIN pass_slips ps ON e.employee_id = ps.employee_id " +
                "WHERE e.employee_id = ?";

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;

        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, employeeId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rawReason = rs.getString("reason_for_leaving");
                        String slipType = "Personal";
                        String destination = "N/A";
                        String actualReason = "N/A";

                        if (rawReason != null && rawReason.contains("|")) {
                            String[] parts = rawReason.split("\\|");
                            for (String part : parts) {
                                part = part.trim();
                                if (part.toLowerCase().startsWith("type:")) {
                                    slipType = part.substring(5).trim().replace(" Business", "");
                                } else if (part.toLowerCase().startsWith("destination:")) {
                                    destination = part.substring(12).trim();
                                } else if (part.toLowerCase().startsWith("reason:")) {
                                    actualReason = part.substring(7).trim();
                                }
                            }
                        } else if (rawReason != null) {
                            actualReason = rawReason;
                            if (rawReason.toLowerCase().contains("official")) slipType = "Official";
                        }

                        list.add(new EmployeePassSlipDetail(
                                rs.getString("employee_id"),
                                rs.getString("name"),
                                slipType,
                                destination,
                                actualReason,
                                rs.getString("time_out"), // Now formatted automatically as AM/PM
                                rs.getString("time_in"),  // Now formatted automatically as AM/PM
                                rs.getString("expected_time"),
                                rs.getString("status")
                        ));
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }

    public List<MonthlyActivitySummary> findMonthlyActivity() {
        List<MonthlyActivitySummary> monthlyData = new ArrayList<>();
        Connection connection = null;

        String sql = """
                SELECT
                    TRIM(TO_CHAR(date_issued, 'Mon')) AS month_name,
                    COUNT(*) AS total_slips,
                    COUNT(*) FILTER (WHERE reason_for_leaving ILIKE 'Type: Official Business%') AS official_count,
                    COUNT(*) FILTER (WHERE reason_for_leaving ILIKE 'Type: Personal%') AS personal_count
                FROM pass_slips
                WHERE date_issued >= CURRENT_DATE - INTERVAL '1 year'
                GROUP BY
                    EXTRACT(YEAR FROM date_issued),
                    EXTRACT(MONTH FROM date_issued),
                    TRIM(TO_CHAR(date_issued, 'Mon'))
                ORDER BY
                    EXTRACT(YEAR FROM date_issued),
                    EXTRACT(MONTH FROM date_issued)
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    monthlyData.add(new MonthlyActivitySummary(
                            resultSet.getString("month_name"),
                            resultSet.getInt("total_slips"),
                            resultSet.getInt("official_count"),
                            resultSet.getInt("personal_count")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                ConnectionPoolManager.getInstance().release(connection);
            }
        }
        return monthlyData;
    }

    private int count(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private int averageDurationMinutes(Connection connection) throws Exception {
        String sql = "SELECT COALESCE(ROUND(AVG(duration_minutes)), 0) AS avg_minutes FROM pass_slips WHERE duration_minutes IS NOT NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt("avg_minutes") : 0;
        }
    }

    private String formatDuration(int minutes) {
        if (minutes <= 0) return "0m";
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (hours == 0) return remainingMinutes + "m";
        if (remainingMinutes == 0) return hours + "h";
        return hours + "h " + remainingMinutes + "m";
    }
}