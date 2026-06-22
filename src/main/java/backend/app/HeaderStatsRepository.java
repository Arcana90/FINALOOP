package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class HeaderStatsRepository {

    public int countEmployeesOut() {
        Connection connection = null;

        String sql = """
                SELECT COUNT(*)
                FROM pass_slips
                WHERE status = 'Out'
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getInt(1);
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