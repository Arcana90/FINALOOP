package backend.passslip;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ReportsJdbcRepository {

    public ReportsStats getStats() {
        Connection connection = null;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            int totalSlips = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    """);

            int currentlyOut = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE status = 'Out'
                    """);

            int official = count(connection, """
                    SELECT COUNT(*)
                    FROM pass_slips
                    WHERE reason_for_leaving ILIKE 'Type: Official Business%'
                    """);

            int avgMinutes = averageDurationMinutes(connection);

            return new ReportsStats(
                    totalSlips,
                    currentlyOut,
                    official,
                    formatDuration(avgMinutes)
            );

        } catch (Exception e) {
            e.printStackTrace();
            return new ReportsStats(0, 0, 0, "0m");
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    public List<ReportDepartmentSummary> findDepartmentSummaries() {
        List<ReportDepartmentSummary> summaries = new ArrayList<>();
        Connection connection = null;

        String sql = """
                SELECT
                    e.department,
                    COUNT(*) AS total_slips,
                    COUNT(*) FILTER (
                        WHERE ps.reason_for_leaving ILIKE 'Type: Official Business%'
                    ) AS official_count,
                    COUNT(*) FILTER (
                        WHERE ps.reason_for_leaving ILIKE 'Type: Personal%'
                    ) AS personal_count,
                    COALESCE(ROUND(AVG(ps.duration_minutes)), 0) AS avg_duration_minutes
                FROM pass_slips ps
                JOIN employees e ON ps.employee_id = e.employee_id
                GROUP BY e.department
                ORDER BY total_slips DESC, e.department
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    summaries.add(new ReportDepartmentSummary(
                            resultSet.getString("department"),
                            resultSet.getInt("total_slips"),
                            resultSet.getInt("official_count"),
                            resultSet.getInt("personal_count"),
                            formatDuration(resultSet.getInt("avg_duration_minutes"))
                    ));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return summaries;
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
                    COUNT(*) FILTER (
                        WHERE reason_for_leaving ILIKE 'Type: Official Business%'
                    ) AS official_count,
                    COUNT(*) FILTER (
                        WHERE reason_for_leaving ILIKE 'Type: Personal%'
                    ) AS personal_count
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
            ConnectionPoolManager.getInstance().release(connection);
        }

        return dailySummaries;
    }

    public List<MonthlyActivitySummary> findMonthlyActivity() {
        List<MonthlyActivitySummary> monthlyData = new ArrayList<>();
        Connection connection = null;

        String sql = """
                SELECT
                    TRIM(TO_CHAR(date_issued, 'Mon')) AS month_name,
                    COUNT(*) AS total_slips
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
                            resultSet.getInt("total_slips")
                    ));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
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
        String sql = """
                SELECT COALESCE(ROUND(AVG(duration_minutes)), 0) AS avg_minutes
                FROM pass_slips
                WHERE duration_minutes IS NOT NULL
                """;

        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            return resultSet.next()
                    ? resultSet.getInt("avg_minutes")
                    : 0;
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