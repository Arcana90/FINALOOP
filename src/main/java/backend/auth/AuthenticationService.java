package backend.auth;

import backend.auth.AuthSessionManager; // 🌟 CORRECT IMPORT
import backend.db.ConnectionPoolManager;
import backend.logging.ActivityLogger;
import backend.shared.ApplicationConstants;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.logging.Logger;

public final class AuthenticationService {
    private static final Logger LOG = Logger.getLogger(AuthenticationService.class.getName());
    private static volatile AuthenticationService instance;
    private final AdminAuthValidator validator = AdminAuthValidator.getInstance();
    private final PasswordHasher hasher = PasswordHasher.getInstance();
    // 🌟 Use AuthSessionManager here
    private final AuthSessionManager sessions = AuthSessionManager.getInstance();
    private final ActivityLogger logger = ActivityLogger.getInstance();

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

        // 🌟 Use the session manager
        sessions.createSession(onAutoLock, creds.role, creds.username);

        // ❌ REMOVED: .setCurrentUser(creds.username) -> Not needed, AuthSessionManager is the source of truth

        logger.log(ApplicationConstants.LOG_EVENT_LOGIN, "Successful login for: " + username);
    }

    public void seedAccounts() {
        String checkSql = "SELECT count(*) FROM users";
        String insertSql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";

        try (Connection c = ConnectionPoolManager.getInstance().acquire();
             PreparedStatement checkStmt = c.prepareStatement(checkSql);
             ResultSet rs = checkStmt.executeQuery()) {

            // If the users table is completely empty, create a default admin
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement insertStmt = c.prepareStatement(insertSql)) {
                    insertStmt.setString(1, "admin");

                    char[] defaultPassword = "password123".toCharArray();
                    insertStmt.setString(2, hasher.hash(defaultPassword));
                    insertStmt.setString(3, "Admin");

                    insertStmt.executeUpdate();
                    Arrays.fill(defaultPassword, '\0');
                    LOG.info("Database was empty. Seeded default admin account (admin / password123).");
                }
            }
        } catch (Exception e) {
            LOG.warning("Skipped account seeding (accounts may already exist): " + e.getMessage());
        }
    }
    public void unlock(char[] password) {
        validator.validateUnlockPayload(password);
        String currentUsername = sessions.getCurrentUsername();
        if (currentUsername == null) {
            throw new AuthenticationException("Session expired. Please log in again.");
        }

        UserCredentials creds = loadCredentials(currentUsername);
        if (creds == null || !hasher.verify(password, creds.hash)) {
            logger.log(ApplicationConstants.LOG_EVENT_SESSION_LOCK, "Failed unlock attempt.");
            throw new AuthenticationException("Incorrect password.");
        }

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