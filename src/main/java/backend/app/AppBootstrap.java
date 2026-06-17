package backend.app;

import backend.db.ConnectionPoolManager;
import backend.events.EmployeeReturnedEvent;
import backend.events.EventPublisher;
import backend.events.PassSlipIssuedEvent;
import backend.passslip.PassSlipController;
import backend.passslip.PassSlipRepository;
import backend.passslip.PassSlipService;
import backend.passslip.PassSlipValidator;
import backend.passslip.PassSlipService.IssuanceResult;
import backend.timein.ReturnStatusUpdater;
import backend.timein.TimeInController;
import backend.timein.TimeInService;
import backend.timein.TimeInValidator;
import backend.timein.TimeInService.TimeInResult;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Application entry point for local development and VS Code F5 / Run.
 *
 * <p>This class:
 * <ol>
 *   <li>Configures Java logging to show INFO+ on the console.</li>
 *   <li>Boots an H2 in-memory database via {@link ConnectionPoolManager}.</li>
 *   <li>Creates the schema and seeds test employees via {@link DatabaseInitializer}.</li>
 *   <li>Wires all backend components together (manual DI — no framework needed).</li>
 *   <li>Registers demo event listeners on the {@link EventPublisher}.</li>
 *   <li>Runs a complete Issuance → Time-In demonstration workflow and prints results.</li>
 * </ol>
 * </p>
 *
 * <h2>Running</h2>
 * <ul>
 *   <li><b>VS Code:</b> Press {@code F5} (uses {@code .vscode/launch.json}).</li>
 *   <li><b>Terminal:</b> {@code mvn exec:java}</li>
 *   <li><b>Tests:</b> {@code mvn test}</li>
 * </ul>
 *
 * <h2>Switching to MySQL / PostgreSQL</h2>
 * Replace the {@code JDBC_URL}, {@code DB_USER}, and {@code DB_PASSWORD} constants
 * below with your production credentials, add the appropriate JDBC driver to {@code pom.xml},
 * and remove the {@link DatabaseInitializer#initialize(Connection)} call.
 */
public class AppBootstrap {

    // ── H2 in-memory database — change these three lines for production ──
    private static final String JDBC_URL  = "jdbc:h2:mem:passslipdb;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private static final String DB_USER   = "sa";
    private static final String DB_PASS   = "";
    private static final int    POOL_SIZE = 5;

    private static final Logger LOGGER = Logger.getLogger(AppBootstrap.class.getName());

    public static void main(String[] args) throws Exception {
        configureLogging();

        LOGGER.info("═══════════════════════════════════════════════════");
        LOGGER.info("  Pass Slip Backend — Local Development Bootstrap  ");
        LOGGER.info("═══════════════════════════════════════════════════");

        // ── 1. Boot connection pool ───────────────────────────────────────
        ConnectionPoolManager.initialize(JDBC_URL, DB_USER, DB_PASS, POOL_SIZE);
        ConnectionPoolManager pool = ConnectionPoolManager.getInstance();
        LOGGER.info("Connection pool ready.");

        // ── 2. Create schema + seed data ──────────────────────────────────
        Connection setupConn = pool.acquire();
        try {
            DatabaseInitializer.initialize(setupConn);
        } finally {
            pool.release(setupConn);
        }

        // ── 3. Wire components (manual dependency injection) ──────────────
        EventPublisher eventPublisher = EventPublisher.getInstance();

        // Register demo listeners — in production these would be your
        // ActivityLogger, MetricsAggregator, NotificationService, etc.
        eventPublisher.register(PassSlipIssuedEvent.class, event ->
            LOGGER.info(String.format(
                "[EVENT] PassSlipIssued  → slip=%s  employee=%s  at=%s",
                event.slipId(), event.employeeId(), event.outboundTimestamp()
            ))
        );
        eventPublisher.register(EmployeeReturnedEvent.class, event ->
            LOGGER.info(String.format(
                "[EVENT] EmployeeReturned → slip=%s  employee=%s  duration=%s  at=%s",
                event.slipId(), event.employeeId(), event.totalDuration(), event.inboundTimestamp()
            ))
        );

        // Pass Slip Issuance pipeline
        PassSlipValidator    validator   = new PassSlipValidator();
        PassSlipRepository   psRepo      = new PassSlipRepository(pool);
        PassSlipService      psService   = new PassSlipService(validator, psRepo, eventPublisher);
        PassSlipController   psCtrl      = new PassSlipController(psService);

        // Time-In pipeline
        TimeInValidator      tiValidator = new TimeInValidator(pool);
        ReturnStatusUpdater  updater     = new ReturnStatusUpdater(pool);
        TimeInService        tiService   = new TimeInService(tiValidator, updater, eventPublisher, pool);
        TimeInController     tiCtrl      = new TimeInController(tiService);

        // ── 4. Demo: Issuance ─────────────────────────────────────────────
        LOGGER.info("");
        LOGGER.info("─── Demo: Issue pass slip for EMP-001 ───────────────");
        IssuanceResult issued = psCtrl.handleIssuePassSlip(
                "EMP-001",
                "City Hall - Business Permit Renewal",
                "Annual business permit renewal submission"
        );
        printIssuanceResult(issued);

        // ── 5. Demo: Duplicate issuance (must be BLOCKED) ─────────────────
        LOGGER.info("");
        LOGGER.info("─── Demo: Re-issue for same employee (must be blocked) ─");
        IssuanceResult blocked = psCtrl.handleIssuePassSlip(
                "EMP-001",
                "Another Location",
                "Should be blocked"
        );
        printIssuanceResult(blocked);

        // ── 6. Demo: Validation failure ───────────────────────────────────
        LOGGER.info("");
        LOGGER.info("─── Demo: Validation failure (empty destination) ────");
        IssuanceResult invalid = psCtrl.handleIssuePassSlip("EMP-002", "", "Some reason");
        printIssuanceResult(invalid);

        // ── 7. Demo: Time-In for the successfully issued slip ─────────────
        if (issued.isSuccess()) {
            String slipId = issued.getCreatedSlip().getSlipId();

            LOGGER.info("");
            LOGGER.info("─── Demo: Time-In for slip " + slipId.substring(0, 8) + "... ─────");

            // Small pause so duration is non-zero in the demo output
            Thread.sleep(1000);

            TimeInResult returned = tiCtrl.handleTimeIn(slipId);
            printTimeInResult(returned);
        }

        // ── 8. Demo: Time-In on already-returned slip (must fail) ─────────
        if (issued.isSuccess()) {
            String slipId = issued.getCreatedSlip().getSlipId();

            LOGGER.info("");
            LOGGER.info("─── Demo: Re-process Time-In on RETURNED slip (must fail) ─");
            TimeInResult duplicate = tiCtrl.handleTimeIn(slipId);
            printTimeInResult(duplicate);
        }

        LOGGER.info("");
        LOGGER.info("═══════════════════════════════════════════════════");
        LOGGER.info("  Demo complete. All lifecycle rules enforced.      ");
        LOGGER.info("═══════════════════════════════════════════════════");

        pool.shutdown();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void printIssuanceResult(IssuanceResult result) {
        switch (result.getOutcome()) {
            case SUCCESS -> LOGGER.info(String.format(
                "  ✓ SUCCESS  slip=%s  employee=%s  timeOut=%s",
                result.getCreatedSlip().getSlipId(),
                result.getCreatedSlip().getEmployeeId(),
                result.getCreatedSlip().getTimeOut()
            ));
            case VALIDATION_FAILURE -> LOGGER.warning(
                "  ✗ VALIDATION FAILURE: " + result.getViolations()
            );
            case BLOCKED -> LOGGER.warning(
                "  ✗ BLOCKED: " + result.getErrorMessage()
            );
            case SYSTEM_ERROR -> LOGGER.severe(
                "  ✗ SYSTEM ERROR: " + result.getErrorMessage()
            );
        }
    }

    private static void printTimeInResult(TimeInResult result) {
        switch (result.getOutcome()) {
            case SUCCESS -> LOGGER.info(String.format(
                "  ✓ SUCCESS  slip=%s  employee=%s  duration=%s  timeIn=%s",
                result.getSlipId(),
                result.getEmployeeId(),
                result.getTotalDuration(),
                result.getTimeIn()
            ));
            case VALIDATION_FAILURE -> LOGGER.warning(
                "  ✗ VALIDATION FAILURE: " + result.getErrorMessage()
            );
            case SYSTEM_ERROR -> LOGGER.severe(
                "  ✗ SYSTEM ERROR: " + result.getErrorMessage()
            );
        }
    }

    /**
     * Configures the root logger to emit readable single-line output to the console.
     * Overrides the default two-line JUL format.
     */
    private static void configureLogging() {
        System.setProperty(
            "java.util.logging.SimpleFormatter.format",
            "[%1$tH:%1$tM:%1$tS] [%4$-7s] %5$s%n"
        );
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (var h : root.getHandlers()) root.removeHandler(h);
        ConsoleHandler ch = new ConsoleHandler();
        ch.setLevel(Level.INFO);
        ch.setFormatter(new SimpleFormatter());
        root.addHandler(ch);
    }
}
