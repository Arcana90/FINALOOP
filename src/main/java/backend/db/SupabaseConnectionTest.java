package backend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SupabaseConnectionTest {

    public static Connection getConnection() throws SQLException {
        String url = System.getenv("SUPABASE_DB_URL");
        String user = System.getenv("SUPABASE_DB_USER");
        String password = System.getenv("SUPABASE_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new IllegalStateException("Missing Supabase environment variables.");
        }

        return DriverManager.getConnection(url, user, password);
    }

    public static void testConnection() {
        try (Connection connection = getConnection()) {
            System.out.println("Database connected successfully!");
            System.out.println("Connected to: " + connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            System.err.println("Database connection failed.");
            e.printStackTrace();
        }
    }
}