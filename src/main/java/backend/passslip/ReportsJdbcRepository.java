package backend.passslip;

import backend.db.ConnectionPoolManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportsJdbcRepository {

    public List<DailyActivitySummary> findWeeklyDailyActivity() {
        List<DailyActivitySummary> list = new ArrayList<>();
        String sql = """
            SELECT TRIM(TO_CHAR(date_issued, 'Dy')) AS day_name,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count
            FROM pass_slips
            WHERE date_issued >= DATE_TRUNC('week', CURRENT_DATE)
            GROUP BY TO_CHAR(date_issued, 'Dy'), EXTRACT(DOW FROM date_issued)
            ORDER BY EXTRACT(DOW FROM date_issued);
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new DailyActivitySummary(
                            rs.getString("day_name"),
                            rs.getInt("official_count"),
                            rs.getInt("personal_count")
                    ));
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }

    public List<MonthlyActivitySummary> findMonthlyActivity() {
        List<MonthlyActivitySummary> list = new ArrayList<>();
        String sql = """
            SELECT TRIM(TO_CHAR(date_issued, 'Mon')) AS month_name,
                   COUNT(*) AS total_slips,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count
            FROM pass_slips
            WHERE EXTRACT(YEAR FROM date_issued) = EXTRACT(YEAR FROM CURRENT_DATE)
            GROUP BY TO_CHAR(date_issued, 'Mon'), EXTRACT(MONTH FROM date_issued)
            ORDER BY EXTRACT(MONTH FROM date_issued);
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new MonthlyActivitySummary(
                            rs.getString("month_name"),
                            rs.getInt("total_slips"),
                            rs.getInt("official_count"),
                            rs.getInt("personal_count")
                    ));
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }

    public List<ReportEmployeeSummary> getEmployeeSummaries() {
        List<ReportEmployeeSummary> list = new ArrayList<>();
        String sql = """
            SELECT e.employee_id, 
                   CONCAT(e.first_name, ' ', e.last_name) AS name,
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count,
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Approved' THEN 1 END) AS approved_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Cancel%' THEN 1 END) AS canceled_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Rejected' THEN 1 END) AS rejected_count,
                   COUNT(CASE WHEN ps.time_in IS NULL AND ps.status::text ILIKE 'Approved' 
                               AND (ps.date_issued < CURRENT_DATE OR (ps.date_issued = CURRENT_DATE AND LOCALTIME > ps.expected_time_in)) THEN 1 END) AS awol_count
            FROM employees e
            LEFT JOIN pass_slips ps ON e.employee_id = ps.employee_id
            GROUP BY e.employee_id, e.first_name, e.last_name
            ORDER BY e.employee_id;
            """;

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
                            rs.getInt("official_count"),
                            rs.getInt("approved_count"),
                            rs.getInt("canceled_count"),
                            rs.getInt("rejected_count"),
                            rs.getInt("awol_count")
                    ));
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }

    public List<EmployeePassSlipDetail> getEmployeePassSlipDetails(String employeeId) {
        List<EmployeePassSlipDetail> list = new ArrayList<>();

        // Fixed: Cast timestamps to text so AppSettingsManager can format 12h/24h dynamically
        String sql = """
            SELECT ps.date_issued::text AS date_issued,
                   e.employee_id, 
                   CONCAT(e.first_name, ' ', e.last_name) AS name,
                   ps.reason_for_leaving,
                   ps.time_out::text AS time_out,
                   ps.time_in::text AS time_in,
                   ps.status
            FROM employees e
            JOIN pass_slips ps ON e.employee_id = ps.employee_id
            WHERE e.employee_id = ?
            ORDER BY ps.date_issued DESC
            """;

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

                        // Intelligent Prefix Stripping Engine
                        if (rawReason != null) {
                            if (rawReason.contains("|")) {
                                String[] parts = rawReason.split("\\|");
                                for (String part : parts) {
                                    String item = part.trim();
                                    if (item.toLowerCase().startsWith("type:")) {
                                        slipType = item.substring(5).trim();
                                    } else if (item.toLowerCase().startsWith("destination:")) {
                                        destination = item.substring(12).trim();
                                    } else if (item.toLowerCase().startsWith("reason:")) {
                                        actualReason = item.substring(7).trim();
                                    }
                                }
                            } else {
                                if (rawReason.toLowerCase().contains("official")) {
                                    slipType = "Official Business";
                                }
                                actualReason = rawReason;
                            }
                        }

                        list.add(new EmployeePassSlipDetail(
                                rs.getString("date_issued"),
                                rs.getString("employee_id"),
                                rs.getString("name"),
                                slipType, destination, actualReason,
                                rs.getString("time_out"),
                                rs.getString("time_in"),
                                rs.getString("status")
                        ));
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) pool.release(conn);
        }
        return list;
    }
}