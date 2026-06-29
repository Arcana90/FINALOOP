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
                // 🟢 TEMPORARY HACK: Commented out to stop the system from
                // auto-cancelling slips during your late-night testing.
                runShiftValidation();

                List<PassSlipMonitoringRecord> records = new ArrayList<>();
                // ... rest of your code
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
                if (connection != null) {
                    ConnectionPoolManager.getInstance().release(connection);
                }
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
        public void runShiftValidation() {
            Connection c = null;

            // The Smart Time Rule:
            // Triggers IF the slip is from yesterday/older OR IF it's today but past 9:00 PM
            String timeCondition = """
            (date_issued < CURRENT_DATE OR 
            (date_issued = CURRENT_DATE AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time >= '21:00:00'))
            """;

            // Rule 1: "For Approval" should be cancelled after working hours
            String cancelPending = "UPDATE pass_slips SET status = 'Cancelled'::slip_status WHERE status = 'For Approval' AND " + timeCondition;

            // Rule 2: "Approved" (but they never actually left/no time_out) should be cancelled
            String cancelUnused = "UPDATE pass_slips SET status = 'Cancelled'::slip_status WHERE status = 'Approved' AND time_out IS NULL AND " + timeCondition;

            // 🟢 Rule 3: Regular passes left as 'Out' become AWOL after working hours
            String markAwol = """
            UPDATE pass_slips 
            SET status = 'AWOL'::slip_status 
            WHERE status = 'Out' 
              AND time_in IS NULL 
              AND reason_for_leaving NOT LIKE 'Type: Emergency%' 
              AND 
            """ + timeCondition;

            // 🟢 Rule 4: Emergency passes left as 'Out' remain/become 'Excused' after working hours
            String markExcused = """
            UPDATE pass_slips 
            SET status = 'Excused'::slip_status 
            WHERE status = 'Out' 
              AND time_in IS NULL 
              AND reason_for_leaving LIKE 'Type: Emergency%' 
              AND 
            """ + timeCondition;

            try {
                c = ConnectionPoolManager.getInstance().acquire();
                c.setAutoCommit(false);

                try (PreparedStatement ps1 = c.prepareStatement(cancelPending);
                     PreparedStatement ps2 = c.prepareStatement(cancelUnused);
                     PreparedStatement ps3 = c.prepareStatement(markAwol);
                     PreparedStatement ps4 = c.prepareStatement(markExcused)) { // Added ps4

                    ps1.executeUpdate();
                    ps2.executeUpdate();
                    ps3.executeUpdate();
                    ps4.executeUpdate(); // Executes the automated closing rule for Emergency passes

                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (c != null) {
                    try { c.setAutoCommit(true); } catch (SQLException ignored) {}
                    ConnectionPoolManager.getInstance().release(c);
                }
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

            // 🌟 FIX: Removed the "Type" strings from the IN clause.
            // We now look for 'Excused' which is the actual database status
            // assigned to emergency slips when they leave.
            String sql = """
        UPDATE pass_slips
        SET 
            time_in = (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time,
            duration_minutes = GREATEST(
                0,
                FLOOR(EXTRACT(EPOCH FROM (
                    (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time - COALESCE(time_out, time_requested)
                )) / 60)::INT
            ),
            status = 'Returned'::slip_status
        WHERE pass_slip_id = ?
          AND status IN ('Out', 'Approved', 'Excused')
        """;

            try {
                connection = backend.db.ConnectionPoolManager.getInstance().acquire();

                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, passSlipId);
                    return statement.executeUpdate() > 0;
                }

            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                if (connection != null) {
                    backend.db.ConnectionPoolManager.getInstance().release(connection);
                }
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