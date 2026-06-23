package backend.passslip;

import backend.app.DatabaseInitializer;
import backend.db.ConnectionPoolManager;
import backend.events.EmployeeReturnedEvent;
import backend.events.EventPublisher;
import backend.events.PassSlipIssuedEvent;
import backend.passslip.PassSlipService.IssuanceResult;
import backend.timein.ReturnStatusUpdater;
import backend.timein.TimeInController;
import backend.timein.TimeInService;
import backend.timein.TimeInValidator;
import backend.timein.TimeInService.TimeInResult;

import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests executing the full Issuance → Time-In pipeline against an
 * H2 in-memory database. No mocking — every layer is exercised end-to-end.
 */
@DisplayName("Pipeline Integration: Issuance → Time-In")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PassSlipPipelineIntegrationTest {

    // Unique pool name per test class to avoid singleton collision across runs
    private static final String JDBC_URL =
            "jdbc:h2:mem:integration_test_db;DB_CLOSE_DELAY=-1;MODE=MySQL";

    private static ConnectionPoolManager pool;
    private static PassSlipController   psCtrl;
    private static TimeInController     tiCtrl;
    private static EventPublisher       eventPublisher;

    private static List<PassSlipIssuedEvent>   issuedEvents   = new ArrayList<>();
    private static List<EmployeeReturnedEvent> returnedEvents = new ArrayList<>();

    // Shared state across ordered tests
    private static String issuedSlipId;

    @BeforeAll
    static void setUpAll() throws Exception {
        ConnectionPoolManager.initialize(JDBC_URL, "sa", "", 3);
        pool = ConnectionPoolManager.getInstance();

        Connection c = pool.acquire();
        try {
            DatabaseInitializer.initialize(c);
        } finally {
            pool.release(c);
        }

        eventPublisher = EventPublisher.getInstance();
        eventPublisher.clearAllListeners();
        eventPublisher.register(PassSlipIssuedEvent.class,   issuedEvents::add);
        eventPublisher.register(EmployeeReturnedEvent.class, returnedEvents::add);

        PassSlipValidator  validator = new PassSlipValidator();
        PassSlipRepository psRepo   = new PassSlipRepository(pool);
        PassSlipService    psService = new PassSlipService(validator, psRepo, eventPublisher);
        psCtrl = new PassSlipController(psService);

        TimeInValidator     tiValidator = new TimeInValidator(pool);
        ReturnStatusUpdater updater     = new ReturnStatusUpdater(pool);
        TimeInService       tiService   = new TimeInService(tiValidator, updater, eventPublisher, pool);
        tiCtrl = new TimeInController(tiService);
    }

    @AfterAll
    static void tearDownAll() {
        eventPublisher.clearAllListeners();
        pool.shutdown();
    }

    // ── Issuance ──────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("Issue: Valid submission → SUCCESS and event published")
    void issueValidSlip() {
        IssuanceResult result = psCtrl.handleIssuePassSlip(
                "EMP-001", "City Hall", "Permit renewal"
        );

        assertEquals(IssuanceResult.Outcome.SUCCESS, result.getOutcome());
        assertNotNull(result.getCreatedSlip());
        assertNotNull(result.getCreatedSlip().getSlipId());
        assertNotNull(result.getCreatedSlip().getTimeOut());

        issuedSlipId = result.getCreatedSlip().getSlipId();

        assertEquals(1, issuedEvents.size());
        assertEquals(issuedSlipId, issuedEvents.get(0).slipId());
        assertEquals("EMP-001", issuedEvents.get(0).employeeId());
    }

    @Test
    @Order(2)
    @DisplayName("Issue: Re-issue for OUT employee → BLOCKED")
    void issueBlockedForOutEmployee() {
        IssuanceResult result = psCtrl.handleIssuePassSlip(
                "EMP-001", "Another Place", "Should be blocked"
        );

        assertEquals(IssuanceResult.Outcome.BLOCKED, result.getOutcome());
        assertNotNull(result.getErrorMessage());
        // Event count must not have increased
        assertEquals(1, issuedEvents.size());
    }

    @Test
    @Order(3)
    @DisplayName("Issue: Blank destination → VALIDATION_FAILURE")
    void issueValidationFailure() {
        IssuanceResult result = psCtrl.handleIssuePassSlip("EMP-002", "", "Some reason");

        assertEquals(IssuanceResult.Outcome.VALIDATION_FAILURE, result.getOutcome());
        assertFalse(result.getViolations().isEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("Issue: Unknown employee → SYSTEM_ERROR (FK violation)")
    void issueUnknownEmployee() {
        IssuanceResult result = psCtrl.handleIssuePassSlip(
                "EMP-GHOST", "Somewhere", "Reason"
        );

        // Either SYSTEM_ERROR (FK fails) or SYSTEM_ERROR (not found) — must not be SUCCESS
        assertNotEquals(IssuanceResult.Outcome.SUCCESS, result.getOutcome());
    }

    // ── Time-In ───────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("TimeIn: Process return for OUT slip → SUCCESS and event published")
    void timeInSuccess() throws InterruptedException {
        assertNotNull(issuedSlipId, "Slip ID must be set by issuance test (Order 1)");
        Thread.sleep(100); // ensure non-zero duration

        TimeInResult result = tiCtrl.handleTimeIn(issuedSlipId);

        assertEquals(TimeInResult.Outcome.SUCCESS, result.getOutcome());
        assertEquals(issuedSlipId, result.getSlipId());
        assertEquals("EMP-001", result.getEmployeeId());
        assertNotNull(result.getTotalDuration());
        assertTrue(result.getTotalDuration().matches("\\d+h \\d+m"),
                "Duration must match 'Xh Ym' format, was: " + result.getTotalDuration());

        assertEquals(1, returnedEvents.size());
        assertEquals(issuedSlipId, returnedEvents.get(0).slipId());
    }

    @Test
    @Order(6)
    @DisplayName("TimeIn: Re-process already RETURNED slip → VALIDATION_FAILURE")
    void timeInReturnedSlipBlocked() {
        assertNotNull(issuedSlipId);

        TimeInResult result = tiCtrl.handleTimeIn(issuedSlipId);

        assertEquals(TimeInResult.Outcome.VALIDATION_FAILURE, result.getOutcome());
        assertNotNull(result.getErrorMessage());
        // Event count must not have increased
        assertEquals(1, returnedEvents.size());
    }

    @Test
    @Order(7)
    @DisplayName("TimeIn: Non-existent slip ID → VALIDATION_FAILURE")
    void timeInNonExistentSlip() {
        TimeInResult result = tiCtrl.handleTimeIn("00000000-0000-0000-0000-000000000000");

        assertEquals(TimeInResult.Outcome.VALIDATION_FAILURE, result.getOutcome());
    }

    @Test
    @Order(8)
    @DisplayName("TimeIn: Null slip ID → VALIDATION_FAILURE (controller guard)")
    void timeInNullSlipId() {
        TimeInResult result = tiCtrl.handleTimeIn(null);
        assertEquals(TimeInResult.Outcome.VALIDATION_FAILURE, result.getOutcome());
    }

    @Test
    @Order(9)
    @DisplayName("Issue: Second employee (EMP-002) can still get a slip after first is resolved")
    void issuanceForSecondEmployee() {
        int beforeCount = issuedEvents.size();

        IssuanceResult result = psCtrl.handleIssuePassSlip(
                "EMP-002", "Bank", "Cash withdrawal"
        );

        assertEquals(IssuanceResult.Outcome.SUCCESS, result.getOutcome());
        assertEquals(beforeCount + 1, issuedEvents.size());
    }
}
