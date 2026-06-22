package backend.shared;

import backend.auth.AuthenticationService;
import backend.db.ConnectionPoolManager;
import backend.db.DatabaseInitializer;
import java.util.logging.Logger;

public final class ApplicationBootstrap {

    private static final Logger LOG = Logger.getLogger(ApplicationBootstrap.class.getName());
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final char[] DEFAULT_ADMIN_PASSWORD = "Admin@1234".toCharArray();

    private ApplicationBootstrap() {}

    // ADD THIS METHOD TO MAKE THE CODE RUNNABLE
    public static void main(String[] args) {
        try {
            System.out.println("=== Launching Standalone Backend Test ===");
            boot();
            System.out.println("=== Backend Running Successfully! ===");

            // Keeps the application open momentarily to verify connection
            Thread.sleep(2000);

        } catch (Exception e) {
            System.err.println("Bootstrap failed execution:");
            e.printStackTrace();
        } finally {
            shutdown();
            System.out.println("=== Backend Shutdown Cleanly ===");
        }
    }

    public static void boot() {
        LOG.info("Application bootstrap starting…");
        ConnectionPoolManager.getInstance();
        DatabaseInitializer.initialize();

        AuthenticationService authService = AuthenticationService.getInstance();

        // REMOVE OR COMMENT THESE OUT:
        // authService.seedAdministrator(DEFAULT_ADMIN_USERNAME, DEFAULT_ADMIN_PASSWORD);
        // java.util.Arrays.fill(DEFAULT_ADMIN_PASSWORD, '\0');

        // Keep this to seed the other two:
        authService.seedAccounts();

        LOG.info("Application bootstrap complete.");
    }

    public static void shutdown() {
        LOG.info("Application shutdown initiated.");
        ConnectionPoolManager.getInstance().shutdown();
    }
}