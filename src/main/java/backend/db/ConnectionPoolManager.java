package backend.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight, thread-safe JDBC connection pool manager.
 *
 * <p>Maintains a fixed-size pool of pre-initialized {@link Connection} instances.
 * Callers acquire a connection via {@link #acquire()} and must always return it
 * via {@link #release(Connection)} inside a {@code finally} block to prevent pool
 * starvation. This class is a singleton and is initialized once at application startup
 * via {@link #initialize(String, String, String, int)}.</p>
 *
 * <p>Connections returned to the pool are validated before re-queuing; invalid
 * connections are silently replaced with a fresh one.</p>
 */
public final class ConnectionPoolManager {

    private static final Logger LOGGER = Logger.getLogger(ConnectionPoolManager.class.getName());

    private static volatile ConnectionPoolManager instance;

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final int poolSize;

    private final Deque<Connection> pool = new ArrayDeque<>();
    private final Object lock = new Object();

    private ConnectionPoolManager(String jdbcUrl, String username, String password, int poolSize) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.poolSize = poolSize;
        initializePool();
    }

    /**
     * Initializes the singleton pool. Must be called once before any calls to
     * {@link #getInstance()}. Subsequent calls after initialization are ignored.
     *
     * @param jdbcUrl  JDBC connection URL (e.g., "jdbc:mysql://localhost:3306/passslip_db").
     * @param username Database username.
     * @param password Database password.
     * @param poolSize Total number of pooled connections to maintain.
     */
    public static void initialize(String jdbcUrl, String username, String password, int poolSize) {
        if (instance == null) {
            synchronized (ConnectionPoolManager.class) {
                if (instance == null) {
                    instance = new ConnectionPoolManager(jdbcUrl, username, password, poolSize);
                    LOGGER.info(String.format(
                            "ConnectionPoolManager initialized with pool size [%d] for [%s].",
                            poolSize, jdbcUrl
                    ));
                }
            }
        }
    }

    /**
     * Returns the singleton instance. {@link #initialize(String, String, String, int)}
     * must be called before this method.
     *
     * @return The global ConnectionPoolManager instance.
     * @throws IllegalStateException if the pool has not been initialized.
     */
    public static ConnectionPoolManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ConnectionPoolManager has not been initialized. " +
                    "Call initialize() at application startup before acquiring connections."
            );
        }
        return instance;
    }

    /**
     * Pre-fills the pool with {@code poolSize} open connections.
     */
    private void initializePool() {
        synchronized (lock) {
            for (int i = 0; i < poolSize; i++) {
                try {
                    pool.push(createConnection());
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE,
                            "Failed to create initial pool connection at slot " + i, e);
                }
            }
        }
    }

    /**
     * Opens and returns a fresh JDBC connection using the configured credentials.
     *
     * @return A new, open {@link Connection}.
     * @throws SQLException If the driver fails to open the connection.
     */
    private Connection createConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    /**
     * Acquires a connection from the pool. Blocks until one is available.
     *
     * <p>The acquired connection will have {@code autoCommit} set to {@code true}
     * by default. Repository classes managing explicit transactions must set
     * {@code autoCommit(false)} themselves and restore it before releasing.</p>
     *
     * @return A valid, open {@link Connection}.
     * @throws SQLException         If a replacement connection cannot be created.
     * @throws InterruptedException If the waiting thread is interrupted.
     */
    public Connection acquire() throws SQLException, InterruptedException {
        synchronized (lock) {
            while (pool.isEmpty()) {
                LOGGER.fine("Pool exhausted. Waiting for a released connection...");
                lock.wait();
            }

            Connection connection = pool.pop();

            if (!isValid(connection)) {
                LOGGER.warning("Stale connection detected in pool. Replacing with a fresh connection.");
                closeSilently(connection);
                connection = createConnection();
            }

            return connection;
        }
    }

    /**
     * Returns a connection to the pool. The connection's {@code autoCommit} state is
     * reset to {@code true} before re-queuing to ensure a clean state for the next acquirer.
     *
     * @param connection The connection to release. Null values are silently ignored.
     */
    public void release(Connection connection) {
        if (connection == null) {
            return;
        }

        synchronized (lock) {
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                    pool.push(connection);
                    lock.notifyAll();
                } else {
                    LOGGER.warning("Attempted to release a closed connection. " +
                                   "Replacing with fresh connection to maintain pool size.");
                    try {
                        pool.push(createConnection());
                        lock.notifyAll();
                    } catch (SQLException e) {
                        LOGGER.log(Level.SEVERE, "Failed to replace closed connection in pool.", e);
                    }
                }
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING,
                        "Error inspecting connection state during release. Discarding connection.", e);
                try {
                    pool.push(createConnection());
                    lock.notifyAll();
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to replenish pool after faulty release.", ex);
                }
            }
        }
    }

    /**
     * Validates a connection by checking if it is open and responsive.
     *
     * @param connection The connection to validate.
     * @return {@code true} if the connection is valid and open; {@code false} otherwise.
     */
    private boolean isValid(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Closes a connection silently, suppressing any SQLException.
     *
     * @param connection The connection to close.
     */
    private void closeSilently(Connection connection) {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to silently close a stale connection.", e);
        }
    }

    /**
     * Shuts down the pool by closing all pooled connections. Intended for use
     * during application teardown ({@code Application.stop()}).
     */
    public void shutdown() {
        synchronized (lock) {
            LOGGER.info(String.format(
                    "Shutting down ConnectionPoolManager. Closing [%d] pooled connection(s).",
                    pool.size()
            ));
            for (Connection connection : pool) {
                closeSilently(connection);
            }
            pool.clear();
        }
    }
    private boolean updatePasswordInDatabase(String newPassword) {
        java.sql.Connection connection = null;

        // ADJUST THIS SQL statement to match your database schema.
        // Example A (If you have a users/accounts table):
        String sql = "UPDATE users SET password = ? WHERE username = 'admin'";

        // Example B (If you store credentials inside your settings key-value table):
        // String sql = "UPDATE settings SET setting_value = ? WHERE setting_key = 'admin_password'";

        try {
            connection = backend.db.ConnectionPoolManager.getInstance().acquire();
            try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {

                // 💡 TIP: If your login system expects hashed passwords, wrap it using your project's PasswordHasher:
                // String hashedPassword = backend.app.auth.PasswordHasher.hash(newPassword);
                // statement.setString(1, hashedPassword);

                statement.setString(1, newPassword);

                int rowsUpdated = statement.executeUpdate();
                return rowsUpdated > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                backend.db.ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }
}
