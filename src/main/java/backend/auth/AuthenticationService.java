package backend.auth;

import backend.db.ConnectionPoolManager;
import backend.logging.ActivityLogger;
import backend.shared.ApplicationConstants;
import backend.validation.ValidationException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Coordinates the full authentication and unlock sequences.
 *
 * <p>Flow (login):
 * <ol>
 *   <li>{@link AdminAuthValidator} – validates field constraints.</li>
 *   <li>Load stored hash from {@code admin_credentials}.</li>
 *   <li>{@link PasswordHasher#verify} – constant-time comparison.</li>
 *   <li>{@link SessionManager#createSession} – mint token, start idle timer.</li>
 *   <li>{@link ActivityLogger#log} – write immutable audit entry.</li>
 * </ol>
 *
 * <p>Throws {@link AuthenticationException} for credential mismatches or DB errors
 * so the controller can display an appropriate message without exposing internals.
 */
public final class AuthenticationService {

    private static final Logger LOG = Logger.getLogger(AuthenticationService.class.getName());

    //  YOUR REAL SUPABASE SCHEMA
    private static final String FIND_CREDENTIALS_SQL =
            "SELECT password_hash FROM users WHERE username = 'admin'";

    private static volatile AuthenticationService instance;

    private final AdminAuthValidator validator   = AdminAuthValidator.getInstance();
    private final PasswordHasher     hasher      = PasswordHasher.getInstance();
    private final SessionManager     sessions    = SessionManager.getInstance();
    private final ActivityLogger     logger      = ActivityLogger.getInstance();

    private AuthenticationService() {}

    public static AuthenticationService getInstance() {
        if (instance == null) {
            synchronized (AuthenticationService.class) {
                if (instance == null) instance = new AuthenticationService();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Attempts to authenticate the administrator and initialise a new session.
     *
     * @param username raw username input
     * @param password raw password characters (caller should zero the array afterwards)
     * @param onAutoLock callback forwarded to {@link SessionManager} for idle-lock
     * @throws ValidationException    if field-level constraints are violated
     * @throws AuthenticationException if credentials are wrong or no admin is seeded
     */
    public void login(String username, char[] password, Runnable onAutoLock) {
        // Step 1 — validate fields
        validator.validateLoginPayload(username, password);

        // Step 2 — load stored hash
        String storedHash = loadStoredHash();
        if (storedHash == null) {
            throw new AuthenticationException(
                    "No administrator account found. Please run the setup utility first.");
        }

        // Step 3 — verify password
        if (!hasher.verify(password, storedHash)) {
            logger.log(ApplicationConstants.LOG_EVENT_LOGIN,
                    "Failed login attempt for username: " + username);
            throw new AuthenticationException("Invalid username or password.");
        }

        // Step 4 — create session
        sessions.createSession(onAutoLock);

        // Step 5 — immutable audit log
        logger.log(ApplicationConstants.LOG_EVENT_LOGIN,
                "Successful login for username: " + username
                        + " | token: " + sessions.getSessionToken().substring(0, 8) + "…");

        LOG.info("Administrator authenticated successfully.");
    }

    /**
     * Validates the unlock password and resumes the existing session.
     *
     * @param password raw password characters
     * @throws ValidationException    if field-level constraints are violated
     * @throws AuthenticationException if the password is incorrect
     */
    public void unlock(char[] password) {
        // Step 1 — field validation (unlock has no username field)
        validator.validateUnlockPayload(password);

        // Step 2 — load and verify
        String storedHash = loadStoredHash();
        if (storedHash == null || !hasher.verify(password, storedHash)) {
            logger.log(ApplicationConstants.LOG_EVENT_SESSION_LOCK,
                    "Failed unlock attempt — incorrect password.");
            throw new AuthenticationException("Incorrect password. Session remains locked.");
        }

        // Step 3 — resume session
        sessions.unlockSession();

        logger.log(ApplicationConstants.LOG_EVENT_SESSION_LOCK,
                "Session unlocked successfully.");
    }

    /**
     * Logs out and destroys the current session.
     */
    public void logout() {
        String token = sessions.getSessionToken();
        sessions.invalidateSession();
        logger.log(ApplicationConstants.LOG_EVENT_LOGOUT,
                "Administrator logged out | token: "
                        + (token != null ? token.substring(0, 8) + "…" : "N/A"));
    }

    /**
     * Seeds the administrator account if the {@code admin_credentials} table is empty.
     * Must be called once during first-run setup.
     *
     * @param username desired administrator username
     * @param password desired administrator password (caller zeroes array afterwards)
     */
    public void seedAdministrator(String username, char[] password) {
        String hash = hasher.hash(password);
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection c = null;
        try {
            c = pool.acquire();
            String sql = "INSERT OR IGNORE INTO admin_credentials (id, username, password_hash) "
                       + "VALUES (1, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, hash);
                ps.executeUpdate();
            }
            logger.log("ADMIN_SEED", "Administrator account seeded for username: " + username);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while seeding administrator account.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to seed administrator account.", e);
        } finally {
            pool.release(c);
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private String loadStoredHash() {
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection c = null;
        try {
            c = pool.acquire();
            try (PreparedStatement ps = c.prepareStatement(FIND_CREDENTIALS_SQL);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationException("Authentication was interrupted.", e);
        } catch (SQLException e) {
            throw new AuthenticationException(
                    "Database error during authentication: " + e.getMessage());
        } finally {
            pool.release(c);
        }
    }
}
