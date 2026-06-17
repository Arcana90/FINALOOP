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

/**
 * Manages the single global administrator session.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Creates and destroys session tokens.</li>
 *   <li>Tracks login timestamp and last-activity timestamp.</li>
 *   <li>Schedules and reschedules an idle-lock timer that fires after
 *       {@link ApplicationConstants#SESSION_IDLE_TIMEOUT_MIN} minutes of inactivity.</li>
 * </ul>
 *
 * <p>The lock callback supplied via {@link #createSession(Runnable)} is executed on the
 * idle-timeout daemon thread; callers are responsible for dispatching any JavaFX work
 * with {@code Platform.runLater()} inside that callback.
 */
public final class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());

    private static volatile SessionManager instance;

    // ── Session state (guarded by `this`) ───────────────────────────────────────
    private volatile String        sessionToken;
    private volatile LocalDateTime loginTime;
    private volatile LocalDateTime lastActivityTime;
    private volatile boolean       locked = true;

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

    /**
     * Creates a new session token and starts the idle-lock countdown.
     *
     * @param onLock callback invoked when the session auto-locks due to inactivity
     */
    public synchronized void createSession(Runnable onLock) {
        this.sessionToken     = generateToken();
        this.loginTime        = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.locked           = false;
        this.lockCallback     = onLock;
        scheduleIdleLock();
        LOG.info("Session created. Token prefix: " + sessionToken.substring(0, 8) + "…");
    }

    /**
     * Explicitly invalidates the session (logout or manual lock).
     */
    public synchronized void invalidateSession() {
        locked       = true;
        sessionToken = null;
        cancelIdleLock();
        LOG.info("Session invalidated.");
    }

    /**
     * Locks the session without clearing the token, allowing unlock-in-place (screensaver mode).
     */
    public synchronized void lockSession() {
        locked = true;
        cancelIdleLock();
        LOG.info("Session locked.");
    }

    /**
     * Unlocks a previously locked session and resets the idle timer.
     */
    public synchronized void unlockSession() {
        locked            = false;
        lastActivityTime  = LocalDateTime.now();
        scheduleIdleLock();
        LOG.info("Session unlocked.");
    }

    /** Resets the idle timer. Call this on every meaningful user interaction. */
    public synchronized void recordActivity() {
        if (!locked) {
            lastActivityTime = LocalDateTime.now();
            rescheduleIdleLock();
        }
    }

    /** @return {@code true} if the session exists and is not locked. */
    public boolean isSessionValid() {
        return !locked && sessionToken != null;
    }

    public String getSessionToken()        { return sessionToken; }
    public LocalDateTime getLoginTime()    { return loginTime; }
    public LocalDateTime getLastActivity() { return lastActivityTime; }

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
