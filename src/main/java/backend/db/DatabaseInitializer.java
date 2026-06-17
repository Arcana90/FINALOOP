package backend.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Runs DDL statements to bootstrap the SQLite schema on first launch.
 * Idempotent — uses IF NOT EXISTS on every table.
 */
public final class DatabaseInitializer {

    private static final Logger LOG = Logger.getLogger(DatabaseInitializer.class.getName());

    private DatabaseInitializer() {}

    public static void initialize() {
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        Connection c = null;
        try {
            c = pool.acquire();
            try (Statement st = c.createStatement()) {

                // ── Admin credentials (single row) ───────────────────────────
                st.execute("""
                    CREATE TABLE IF NOT EXISTS admin_credentials (
                        id            INTEGER PRIMARY KEY CHECK (id = 1),
                        username      TEXT    NOT NULL,
                        password_hash TEXT    NOT NULL,
                        created_at    TEXT    NOT NULL DEFAULT (datetime('now'))
                    )
                """);

                // ── Employees ────────────────────────────────────────────────
                st.execute("""
                    CREATE TABLE IF NOT EXISTS employees (
                        employee_id   TEXT    PRIMARY KEY,
                        first_name    TEXT    NOT NULL,
                        last_name     TEXT    NOT NULL,
                        department    TEXT    NOT NULL,
                        status        TEXT    NOT NULL DEFAULT 'AVAILABLE'
                                      CHECK (status IN ('AVAILABLE','OUT','RETURNED')),
                        archived      INTEGER NOT NULL DEFAULT 0,
                        created_at    TEXT    NOT NULL DEFAULT (datetime('now')),
                        updated_at    TEXT    NOT NULL DEFAULT (datetime('now'))
                    )
                """);

                // ── Activity log (append-only) ───────────────────────────────
                st.execute("""
                    CREATE TABLE IF NOT EXISTS activity_log (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_type  TEXT    NOT NULL,
                        description TEXT    NOT NULL,
                        actor       TEXT    NOT NULL DEFAULT 'ADMIN',
                        occurred_at TEXT    NOT NULL DEFAULT (datetime('now'))
                    )
                """);
            }
            LOG.info("Database schema verified / initialised.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Database initialisation was interrupted.", e);
        } catch (SQLException e) {
            throw new RuntimeException("Database initialisation failed: " + e.getMessage(), e);
        } finally {
            pool.release(c);
        }
    }
}
