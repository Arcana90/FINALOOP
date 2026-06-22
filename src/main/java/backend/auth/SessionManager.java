package backend.auth;

import backend.shared.ApplicationConstants;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    private static volatile SessionManager instance;

    // ── Session state (guarded by `this`) ───────────────────────────────────────
    private volatile String        sessionToken;
    private volatile LocalDateTime loginTime;
    private volatile LocalDateTime lastActivityTime;
    private volatile boolean       locked = true;

    // NEW: Store role and username for UI routing and unlocking
    private volatile String        currentUserRole;
    private volatile String        currentUsername;

    // ── Idle-lock timer ─────────────────────────────────────────────────────────
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-idle-watchdog");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?>  lockFuture;
    private Runnable            lockCallback;

    private final SecureRandom rng = new SecureRandom();

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) instance = new SessionManager();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    // UPDATED: Now accepts role and username
// Inside SessionManager.java
    public synchronized void createSession(Runnable onLock, String role, String username) {
        this.sessionToken     = generateToken();
        this.loginTime        = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.locked           = false;
        this.lockCallback     = onLock;
        this.currentUserRole  = role;
        this.currentUsername  = username; // Make sure this variable exists in your class
        scheduleIdleLock();
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public synchronized void invalidateSession() {
        locked          = true;
        sessionToken    = null;
        currentUserRole = null;
        currentUsername = null;
        cancelIdleLock();
        LOG.info("Session invalidated.");
    }

    public synchronized void lockSession() {
        locked = true;
        cancelIdleLock();
        LOG.info("Session locked.");
    }

    public synchronized void unlockSession() {
        locked            = false;
        lastActivityTime  = LocalDateTime.now();
        scheduleIdleLock();
        LOG.info("Session unlocked.");
    }

    public synchronized void recordActivity() {
        if (!locked) {
            lastActivityTime = LocalDateTime.now();
            rescheduleIdleLock();
        }
    }

    public boolean isSessionValid() {
        return !locked && sessionToken != null;
    }

    // GETTERS
    public String getSessionToken()        { return sessionToken; }
    public LocalDateTime getLoginTime()    { return loginTime; }
    public LocalDateTime getLastActivity() { return lastActivityTime; }
    public String getCurrentUserRole()     { return currentUserRole; }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void scheduleIdleLock() {
        long delayMinutes = ApplicationConstants.SESSION_IDLE_TIMEOUT_MIN;
        lockFuture = scheduler.schedule(this::triggerIdleLock, delayMinutes, TimeUnit.MINUTES);
    }

    private void cancelIdleLock() {
        if (lockFuture != null && !lockFuture.isDone()) {
            lockFuture.cancel(false);
        }
    }

    private void rescheduleIdleLock() {
        cancelIdleLock();
        scheduleIdleLock();
    }

    private void triggerIdleLock() {
        synchronized (SessionManager.this) {
            if (!locked) {
                locked = true;
                LOG.warning("Session auto-locked due to inactivity.");
                if (lockCallback != null) {
                    lockCallback.run();
                }
            }
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[ApplicationConstants.SESSION_TOKEN_LENGTH / 2];
        rng.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}