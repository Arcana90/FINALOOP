package backend.app;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class SettingsRepository {

    public Map<String, String> loadSettings() {
        Map<String, String> settings = new HashMap<>();
        Connection connection = null;

        String sql = """
                SELECT setting_key, setting_value
                FROM app_settings
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    settings.put(
                            resultSet.getString("setting_key"),
                            resultSet.getString("setting_value")
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();

        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return settings;
    }

    public boolean saveSettings(String timeFormat, String dateFormat, String autoLogoutMinutes) {
        Connection connection = null;

        String sql = """
                INSERT INTO app_settings (setting_key, setting_value, updated_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key)
                DO UPDATE SET 
                    setting_value = EXCLUDED.setting_value,
                    updated_at = CURRENT_TIMESTAMP
                """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                saveSingleSetting(statement, "time_format", timeFormat);
                saveSingleSetting(statement, "date_format", dateFormat);
                saveSingleSetting(statement, "auto_logout_minutes", autoLogoutMinutes);
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;

        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    private void saveSingleSetting(PreparedStatement statement, String key, String value) throws Exception {
        statement.setString(1, key);
        statement.setString(2, value);
        statement.executeUpdate();
    }
}