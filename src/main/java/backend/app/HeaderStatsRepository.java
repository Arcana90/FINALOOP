package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class HeaderStatsRepository {

    public int countEmployeesOut() {
        Connection connection = null;

        // 🟢 FIXED: Actually added the '? 'placeholder to the SQL string!
        // Using DATE() ensures it matches exactly today, even if the column includes time.
        String sql = """
        SELECT COUNT(*) 
        FROM pass_slips 
        WHERE status = 'Out' 
        AND DATE(date_issued) = ?
        """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                // This now correctly binds to the '?' in the SQL above
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