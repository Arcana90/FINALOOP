package backend.app;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Creates the required database schema and seeds test data when running with the
 * H2 in-memory database for local development.
 *
 * <p>In production (MySQL / PostgreSQL), run your migration scripts (Flyway / Liquibase)
 * instead and remove the call to this class from {@link AppBootstrap}.</p>
 */
public class DatabaseInitializer {

    private static final Logger LOGGER = Logger.getLogger(DatabaseInitializer.class.getName());

    private DatabaseInitializer() {}

    /**
     * Executes DDL and seed DML on the supplied connection.
     * The connection is NOT closed here — the caller manages its lifecycle.
     *
     * @param connection An open, auto-commit connection to the target database.
     * @throws SQLException if any DDL or DML statement fails.
     */
    public static void initialize(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // ── employees table ───────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS employees (
                    employee_id   VARCHAR(50)  NOT NULL PRIMARY KEY,
                    full_name     VARCHAR(255) NOT NULL,
                    department    VARCHAR(255),
                    status        VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
                    CONSTRAINT chk_employee_status
                        CHECK (status IN ('AVAILABLE', 'OUT', 'RETURNED'))
                )
                """);

            // ── pass_slips table ──────────────────────────────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS pass_slips (
                    slip_id        VARCHAR(36)  NOT NULL PRIMARY KEY,
                    employee_id    VARCHAR(50)  NOT NULL,
                    destination    VARCHAR(255) NOT NULL,
                    reason         VARCHAR(500) NOT NULL,
                    time_out       TIMESTAMP    NOT NULL,
                    time_in        TIMESTAMP,
                    total_duration VARCHAR(20),
                    status         VARCHAR(20)  NOT NULL DEFAULT 'OUT',
                    CONSTRAINT fk_slip_employee
                        FOREIGN KEY (employee_id) REFERENCES employees(employee_id),
                    CONSTRAINT chk_slip_status
                        CHECK (status IN ('OUT', 'RETURNED'))
                )
                """);

            // ── Seed: three employees in AVAILABLE state ──────────────────
            stmt.execute("""
                MERGE INTO employees (employee_id, full_name, department, status)
                KEY (employee_id)
                VALUES
                    ('EMP-001', 'Maria Santos',   'Administration', 'AVAILABLE'),
                    ('EMP-002', 'Juan dela Cruz', 'Engineering',    'AVAILABLE'),
                    ('EMP-003', 'Ana Reyes',      'Finance',        'AVAILABLE')
                """);
        }

        LOGGER.info("Database schema initialized and seed data loaded.");
    }
}
