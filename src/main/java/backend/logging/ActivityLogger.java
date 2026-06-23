package backend.logging;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes immutable audit entries to the {@code activity_log} table.
 *
 * <p>Log rows are never updated or deleted — this class provides INSERT only.
 * All writes are synchronised to prevent interleaved entries under concurrent access.
 */
public final class ActivityLogger {

    private static final Logger LOG = Logger.getLogger(ActivityLogger.class.getName());

    private static final String INSERT_SQL =
            "INSERT INTO activity_log (event_type, description, actor) VALUES (?, ?, ?)";

    private static volatile ActivityLogger instance;

    private ActivityLogger() {}

    public static ActivityLogger getInstance() {
        if (instance == null) {
            synchronized (ActivityLogger.class) {
                if (instance == null) instance = new ActivityLogger();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Logs an event attributed to the administrator.
     *
     * @param eventType  one of the {@code LOG_EVENT_*} constants in ApplicationConstants
     * @param description human-readable detail about the event
     */
    public synchronized void log(String eventType, String description) {
        log(eventType, description, "ADMIN");
    }

    /**
     * Logs an event with a custom actor label (e.g. a system process name).
     */
    public synchronized void log(String eventType, String description, String actor) {
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection c = null;
        try {
            c = pool.acquire();
            try (PreparedStatement ps = c.prepareStatement(INSERT_SQL)) {
                ps.setString(1, eventType);
                ps.setString(2, description);
                ps.setString(3, actor);
                ps.executeUpdate();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.SEVERE, "ActivityLogger was interrupted while persisting event ["
                    + eventType + "].", e);
        } catch (SQLException e) {
            // Logging must not crash the application — record to JUL instead.
            LOG.log(Level.SEVERE, "ActivityLogger failed to persist event [" + eventType + "]: "
                    + e.getMessage(), e);
        } finally {
            pool.release(c);
        }
    }
}
