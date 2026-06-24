package backend.auth;

import backend.db.ConnectionPoolManager;
import backend.logging.ActivityLogger;
import backend.shared.ApplicationConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class AuthenticationService {
    private static final Logger LOG = Logger.getLogger(AuthenticationService.class.getName());
    private static volatile AuthenticationService instance;
    private final AdminAuthValidator validator = AdminAuthValidator.getInstance();
    private final PasswordHasher hasher = PasswordHasher.getInstance();
    private final SessionManager sessions = SessionManager.getInstance();
    private final ActivityLogger logger = ActivityLogger.getInstance();

    // Using record for cleaner data holding
    private record UserCredentials(String hash, String role, String username) {}

    private AuthenticationService() {}

    public static AuthenticationService getInstance() {
        if (instance == null) {
            synchronized (AuthenticationService.class) {
                if (instance == null) instance = new AuthenticationService();
            }
        }
        return instance;
    }

    public void login(String username, char[] password, Runnable onAutoLock) {
        validator.validateLoginPayload(username, password);
        UserCredentials creds = loadCredentials(username);
        if (creds == null || !hasher.verify(password, creds.hash)) {
            throw new AuthenticationException("Invalid username or password.");
        }

        // This MUST match the signature in SessionManager
        sessions.createSession(onAutoLock, creds.role, creds.username);

        // 👇 ADD THIS — syncs username into backend.app.SessionManager,
        // which BaseSettingsController/AppSettingsManager rely on
        backend.app.SessionManager.getInstance().setCurrentUser(creds.username);

        logger.log(ApplicationConstants.LOG_EVENT_LOGIN, "Successful login for: " + username);
    }
    public void seedAccounts() {
        String testHash = hasher.hash("12345".toCharArray());

        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        try (Connection c = pool.acquire()) {

            String sql = "INSERT INTO users (user_id, username, password_hash, first_name, last_name, role, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?::user_role, ?) " +
                    "ON CONFLICT (user_id) DO NOTHING";

            // Seed Guard
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, 2);
                ps.setString(2, "guard");
                ps.setString(3, testHash);
                ps.setString(4, "Security");
                ps.setString(5, "Guard");

                // 👇 UPDATED TO PLURAL
                ps.setString(6, "Guards");

                ps.setBoolean(7, true);
                ps.executeUpdate();
            }

            // Seed Director
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, 3);
                ps.setString(2, "director");
                ps.setString(3, testHash);
                ps.setString(4, "Managing");
                ps.setString(5, "Director");

                // 👇 UPDATED TO PLURAL
                ps.setString(6, "Directors");

                ps.setBoolean(7, true);
                ps.executeUpdate();
            }
            System.out.println("=== Guard and Director accounts seeded successfully! ===");
        } catch (Exception e) {
            System.err.println("Database seeding failed because: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public void unlock(char[] password) {
        validator.validateUnlockPayload(password);

        // Retrieve the current user from the session
        String currentUsername = sessions.getCurrentUsername();
        if (currentUsername == null) {
            throw new AuthenticationException("Session expired. Please log in again.");
        }

        // Fetch credentials for the user who was locked
        UserCredentials creds = loadCredentials(currentUsername);

        // Verify their password against the stored hash
        if (creds == null || !hasher.verify(password, creds.hash)) {
            logger.log(ApplicationConstants.LOG_EVENT_SESSION_LOCK, "Failed unlock attempt.");
            throw new AuthenticationException("Incorrect password.");
        }

        // Resume session
        sessions.unlockSession();
        logger.log(ApplicationConstants.LOG_EVENT_SESSION_LOCK, "Session unlocked successfully.");
    }

    private UserCredentials loadCredentials(String username) {
        try (Connection c = ConnectionPoolManager.getInstance().acquire();
             PreparedStatement ps = c.prepareStatement("SELECT password_hash, role FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new UserCredentials(rs.getString("password_hash"), rs.getString("role"), username) : null;
            }
        } catch (Exception e) { return null; }
    }
}