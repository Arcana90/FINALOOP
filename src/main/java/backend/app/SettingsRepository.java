package backend.app;

import backend.db.ConnectionPoolManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class SettingsRepository {

    public Map<String, String> loadSettings(String username) {
        Map<String, String> settings = new HashMap<>();
        Connection connection = null;

        String sql = """
                SELECT time_format, date_format, auto_logout_minutes
                FROM settings
                WHERE username = ?
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        settings.put("time_format", resultSet.getString("time_format"));
                        settings.put("date_format", resultSet.getString("date_format"));
                        settings.put("auto_logout_minutes", String.valueOf(resultSet.getInt("auto_logout_minutes")));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return settings;
    }

    public boolean saveSettings(String username, String timeFormat, String dateFormat, String autoLogoutMinutes) {
        Connection connection = null;

        String sql = """
                INSERT INTO settings (username, time_format, date_format, auto_logout_minutes)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (username)
                DO UPDATE SET
                    time_format = EXCLUDED.time_format,
                    date_format = EXCLUDED.date_format,
                    auto_logout_minutes = EXCLUDED.auto_logout_minutes
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username);
                statement.setString(2, timeFormat);
                statement.setString(3, dateFormat);
                statement.setInt(4, Integer.parseInt(autoLogoutMinutes));
                statement.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }
}