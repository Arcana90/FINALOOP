package backend.passslip;

import backend.db.ConnectionPoolManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportsJdbcRepository {

    // ==========================================
    // ORIGINAL METHODS (For UI Charts & Monthly/Quarterly Reports)
    // ==========================================

    public List<DailyActivitySummary> findWeeklyDailyActivity() {
        List<DailyActivitySummary> list = new ArrayList<>();
        String sql = """
            SELECT TRIM(TO_CHAR(date_issued, 'Dy')) AS day_name,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Emergency%' THEN 1 END) AS emergency_count
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
                            rs.getInt("personal_count"),
                            rs.getInt("emergency_count")
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
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Emergency%' THEN 1 END) AS emergency_count
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
                            rs.getInt("personal_count"),
                            rs.getInt("emergency_count")
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
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Emergency%' THEN 1 END) AS emergency_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Approved' OR ps.time_out IS NOT NULL THEN 1 END) AS approved_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Cancel%' THEN 1 END) AS canceled_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Rejected' THEN 1 END) AS rejected_count,
                   COUNT(CASE WHEN ps.reason_for_leaving NOT ILIKE '%Emergency%'
                               AND ps.time_in IS NULL 
                               AND (ps.status::text ILIKE 'Approved' OR ps.time_out IS NOT NULL)
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
                            rs.getInt("emergency_count"),
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
                                } else if (rawReason.toLowerCase().contains("emergency")) {
                                    slipType = "Emergency";
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

    // ==========================================
    // FILTERED METHODS (For Accurate Weekly PDF Exports)
    // ==========================================

    public List<DailyActivitySummary> findWeeklyDailyActivity(LocalDate startDate, LocalDate endDate) {
        List<DailyActivitySummary> list = new ArrayList<>();
        String sql = """
            SELECT TRIM(TO_CHAR(date_issued, 'Dy')) AS day_name,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count,
                   COUNT(CASE WHEN reason_for_leaving ILIKE '%Emergency%' THEN 1 END) AS emergency_count
            FROM pass_slips
            WHERE date_issued >= ? AND date_issued <= ?
            GROUP BY TO_CHAR(date_issued, 'Dy'), EXTRACT(DOW FROM date_issued)
            ORDER BY EXTRACT(DOW FROM date_issued);
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(endDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new DailyActivitySummary(
                                rs.getString("day_name"),
                                rs.getInt("official_count"),
                                rs.getInt("personal_count"),
                                rs.getInt("emergency_count")
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

    public List<ReportEmployeeSummary> getEmployeeSummariesForWeek(LocalDate startDate, LocalDate endDate) {
        List<ReportEmployeeSummary> list = new ArrayList<>();
        String sql = """
            SELECT e.employee_id, 
                   CONCAT(e.first_name, ' ', e.last_name) AS name,
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Personal%' THEN 1 END) AS personal_count,
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Official%' THEN 1 END) AS official_count,
                   COUNT(CASE WHEN ps.reason_for_leaving ILIKE '%Emergency%' THEN 1 END) AS emergency_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Approved' OR ps.time_out IS NOT NULL THEN 1 END) AS approved_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Cancel%' THEN 1 END) AS canceled_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'Rejected' THEN 1 END) AS rejected_count,
                   COUNT(CASE WHEN ps.status::text ILIKE 'AWOL' THEN 1 END) AS awol_count
            FROM employees e
            INNER JOIN pass_slips ps ON e.employee_id = ps.employee_id
            WHERE ps.date_issued >= ? AND ps.date_issued <= ?
            GROUP BY e.employee_id, e.first_name, e.last_name
            ORDER BY e.employee_id;
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(endDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ReportEmployeeSummary(
                                rs.getString("employee_id"),
                                rs.getString("name"),
                                rs.getInt("personal_count"),
                                rs.getInt("official_count"),
                                rs.getInt("emergency_count"),
                                rs.getInt("approved_count"),
                                rs.getInt("canceled_count"),
                                rs.getInt("rejected_count"),
                                rs.getInt("awol_count")
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

    public List<WeeklyAwolRecord> getWeeklyAwolRecords(LocalDate startDate, LocalDate endDate) {
        List<WeeklyAwolRecord> list = new ArrayList<>();
        String sql = """
            SELECT e.employee_id, 
                   CONCAT(e.first_name, ' ', e.last_name) AS name, 
                   e.department, 
                   ps.date_issued::text AS date_issued
            FROM pass_slips ps
            JOIN employees e ON ps.employee_id = e.employee_id
            WHERE ps.status::text ILIKE 'AWOL' AND ps.date_issued >= ? AND ps.date_issued <= ?
            ORDER BY ps.date_issued DESC;
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(endDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new WeeklyAwolRecord(
                                rs.getString("employee_id"),
                                rs.getString("name"),
                                rs.getMetaData().getColumnCount() >= 3 ? rs.getString("department") : "Operations",
                                rs.getString("date_issued")
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

    public List<WeeklySlipDetailRecord> getWeeklySlipDetails(LocalDate startDate, LocalDate endDate) {
        List<WeeklySlipDetailRecord> list = new ArrayList<>();
        // 🟢 FIX: Used ps.date_issued::text AS slip_id to prevent "column ps.slip_id does not exist" error
        String sql = """
            SELECT ps.date_issued::text AS slip_id, 
                   CONCAT(e.first_name, ' ', e.last_name) AS name, 
                   ps.reason_for_leaving, 
                   ps.status, 
                   ps.date_issued::text AS date_issued
            FROM pass_slips ps
            JOIN employees e ON ps.employee_id = e.employee_id
            WHERE ps.date_issued >= ? AND ps.date_issued <= ?
            ORDER BY ps.date_issued DESC;
            """;

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection conn = null;
        try {
            conn = pool.acquire();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, java.sql.Date.valueOf(startDate));
                ps.setDate(2, java.sql.Date.valueOf(endDate));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rawReason = rs.getString("reason_for_leaving");
                        String leaveType = "Personal";
                        if (rawReason != null && rawReason.toLowerCase().contains("official")) leaveType = "Official";
                        if (rawReason != null && rawReason.toLowerCase().contains("emergency")) leaveType = "Emergency";

                        list.add(new WeeklySlipDetailRecord(
                                rs.getString("slip_id"),
                                rs.getString("name"),
                                leaveType,
                                rs.getString("status"),
                                rs.getString("date_issued")
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