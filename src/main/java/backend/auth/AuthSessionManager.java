package backend.auth; // 🌟 FIXED: Changed to auth package

import backend.shared.ApplicationConstants;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class AuthSessionManager {

    private static final Logger LOG = Logger.getLogger(AuthSessionManager.class.getName());
    private static volatile AuthSessionManager instance;

    private volatile String sessionToken;
    private volatile LocalDateTime loginTime;
    private volatile LocalDateTime lastActivityTime;
    private volatile boolean locked = true;

    private volatile String currentUserRole;
    private volatile String currentUsername;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "session-idle-watchdog");
                t.setDaemon(true);
                return t;
            });

    private ScheduledFuture<?> lockFuture;
    private Runnable lockCallback;
    private final SecureRandom rng = new SecureRandom();

    private AuthSessionManager() {}

    public static AuthSessionManager getInstance() {
        if (instance == null) {
            synchronized (AuthSessionManager.class) {
                if (instance == null) instance = new AuthSessionManager();
            }
        }
        return instance;
    }

    public synchronized void createSession(Runnable onLock, String role, String username) {
        this.sessionToken = generateToken();
        this.loginTime = LocalDateTime.now();
        this.lastActivityTime = LocalDateTime.now();
        this.locked = false;
        this.lockCallback = onLock;
        this.currentUserRole = role;
        this.currentUsername = username;
        scheduleIdleLock();
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    // 🌟 ADDED: Return user role for MainLayoutController
    public String getCurrentUserRole() {
        return currentUserRole;
    }

    // 🌟 ADDED: Unlock method for AuthenticationService
    public synchronized void unlockSession() {
        this.locked = false;
        this.lastActivityTime = LocalDateTime.now();
        rescheduleIdleLock();
        LOG.info("Session unlocked.");
    }

    // 🌟 ADDED: Session validation check for SessionActiveGuard
    public synchronized boolean isSessionValid() {
        return !locked && sessionToken != null;
    }

    // 🌟 ADDED: Activity recorder for SessionActiveGuard
    public synchronized void recordActivity() {
        if (!locked) {
            this.lastActivityTime = LocalDateTime.now();
            rescheduleIdleLock();
        }
    }

    public synchronized void updateTimeout(int minutes) {
        rescheduleIdleLock();
        LOG.info("Session timeout updated to " + minutes + " minutes.");
    }

    public synchronized void invalidateSession() {
        locked = true;
        sessionToken = null;
        currentUserRole = null;
        currentUsername = null;
        cancelIdleLock();
        LOG.info("Session invalidated.");
    }

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
        synchronized (AuthSessionManager.this) {
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