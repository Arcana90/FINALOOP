package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class HeaderStatsRepository {

    public int countEmployeesOut() {
        Connection connection = null;

        // 🟢 Added 'date = ?' to ensure we only count people out TODAY
// 🟢 Replace 'created_at' with your actual database column name if it differs!
        String sql = """
                SELECT COUNT(*)
                FROM pass_slips
                WHERE status IN ('Out', 'Excused') 
                AND DATE(created_at) = ?
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // Bind today's date to the query
                statement.setDate(1, java.sql.Date.valueOf(LocalDate.now()));

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return 0;
    }
}