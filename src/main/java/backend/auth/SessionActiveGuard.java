package backend.auth;

import backend.shared.ApplicationConstants;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Global access-control interceptor that must be called at the entry point of
 * every secured controller action.
 *
 * <p>Usage:
 * <pre>
 *   SessionActiveGuard.getInstance().assertSessionActive(primaryStage);
 *   // — proceed with secured logic if no exception is thrown —
 * </pre>
 *
 * <p>If the session is not valid this class:
 * <ol>
 *   <li>Throws {@link SecurityException} to immediately halt the calling code path.</li>
 *   <li>Schedules a {@code Platform.runLater} redirect to the Lock Screen FXML.</li>
 * </ol>
 */
public final class SessionActiveGuard {

    private static final Logger LOG = Logger.getLogger(SessionActiveGuard.class.getName());

    private static volatile SessionActiveGuard instance;

    private final SessionManager sessions = SessionManager.getInstance();

    private SessionActiveGuard() {}

    public static SessionActiveGuard getInstance() {
        if (instance == null) {
            synchronized (SessionActiveGuard.class) {
                if (instance == null) instance = new SessionActiveGuard();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Verifies the session is currently valid.
     *
     * <p>On success: returns normally; the caller may proceed.
     * On failure: schedules a JavaFX redirect to the lock screen and throws
     * {@link SecurityException} to unwind the calling stack.
     *
     * @param ownerWindow the {@link Window} from which to derive the current {@link Stage}
     * @throws SecurityException if the session is absent or locked
     */
    public void assertSessionActive(Window ownerWindow) {
        if (!sessions.isSessionValid()) {
            LOG.warning("SessionActiveGuard: session invalid — redirecting to lock screen.");
            Platform.runLater(() -> redirectToLockScreen(ownerWindow));
            throw new SecurityException(
                    "Access denied: the administrator session is not active. "
                    + "Please authenticate to continue.");
        }
        // Session valid — record the interaction to reset the idle timer.
        sessions.recordActivity();
    }

    /**
     * Non-throwing variant: returns {@code true} if the session is active,
     * {@code false} otherwise. Does NOT redirect.
     * Use this for conditional UI rendering where no exception should propagate.
     */
    public boolean isSessionActive() {
        return sessions.isSessionValid();
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void redirectToLockScreen(Window ownerWindow) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(ApplicationConstants.FXML_LOCK));
            Parent lockRoot = loader.load();

            Stage stage;
            if (ownerWindow instanceof Stage s) {
                stage = s;
            } else {
                // Fallback: use the first available stage
                stage = Stage.getWindows().stream()
                             .filter(Window::isShowing)
                             .filter(w -> w instanceof Stage)
                             .map(w -> (Stage) w)
                             .findFirst()
                             .orElseThrow(() -> new IllegalStateException("No active Stage found."));
            }

            stage.setScene(new Scene(lockRoot));
            stage.setTitle("Session Locked");
        } catch (IOException e) {
            LOG.severe("Failed to load lock screen FXML: " + e.getMessage());
        }
    }
}
