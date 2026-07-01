package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class HeaderStatsRepository {
    public int countEmployeesOut() {
        Connection connection = null;

        String sql = """
        SELECT COUNT(*) 
        FROM pass_slips 
        WHERE (status = 'Out' OR status = 'Excused')
        AND DATE(date_issued) = ?
        AND (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Manila')::time < '21:00:00'
        """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
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