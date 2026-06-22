package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves the most recent pass-slip activity from the database for
 * display in the Activity Log viewer in the Settings screen.
 */
public class ActivityLogRepository {

    public record ActivityEntry(
            String slipId,
            String employeeId,
            String employeeName,
            String action,
            String timestamp
    ) {}

    public List<ActivityEntry> findRecentActivity(int limit) {
        List<ActivityEntry> entries = new ArrayList<>();
        Connection connection = null;

        String sql = """
            SELECT
                ps.pass_slip_id::text                       AS slip_id,
                ps.employee_id,
                e.first_name || ' ' || e.last_name          AS employee_name,
                CASE
                    WHEN ps.status = 'Out'      THEN 'Pass slip issued (Out)'
                    WHEN ps.status = 'Returned' THEN 'Employee returned'
                    ELSE ps.status::text
                END                                          AS action,
                CASE
                    WHEN ps.status = 'Returned' AND ps.time_in IS NOT NULL
                        THEN ps.date_issued::text || ' ' || ps.time_in::text
                    ELSE ps.date_issued::text || ' ' || ps.time_out::text
                END                                          AS event_timestamp
            FROM pass_slips ps
            JOIN employees e ON ps.employee_id = e.employee_id
            ORDER BY ps.pass_slip_id DESC
            LIMIT ?
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        entries.add(new ActivityEntry(
                                resultSet.getString("slip_id"),
                                resultSet.getString("employee_id"),
                                resultSet.getString("employee_name"),
                                resultSet.getString("action"),
                                resultSet.getString("event_timestamp")
                        ));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return entries;
    }
}
