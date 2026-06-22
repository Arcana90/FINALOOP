package backend.app;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class SessionManager {
    private static SessionManager instance;

    private Timer timer;
    private Stage stage;
    private Runnable logoutAction;
    private int timeoutMinutes;

    private Alert warningDialog;
    private boolean isWarningShowing = false;
    private boolean isTrackingActive = false; // CRITICAL: Safety guard flag

    private SessionManager() {}

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void initialize(Stage stage, int timeoutMinutes, Runnable logoutAction) {
        this.stage = stage;
        this.timeoutMinutes = timeoutMinutes;
        this.logoutAction = logoutAction;

        // Attach global filters to the Stage
        this.stage.addEventFilter(MouseEvent.ANY, e -> resetTimer());
        this.stage.addEventFilter(KeyEvent.ANY, e -> resetTimer());
    }

    public void startTimer() {
        stopTimer(); // Clear any existing routines first

        isTrackingActive = true; // Turn on tracking status
        timer = new Timer(true);

        long timeoutMillis = timeoutMinutes * 60 * 1000L;
        long warningMillis = timeoutMillis - 30000L;

        if (warningMillis <= 0) warningMillis = 15000L;

        // 1. Schedule Warning Dialog
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> showWarningDialog());
            }
        }, warningMillis);

        // 2. Schedule Forced Logout
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> executeLogout());
            }
        }, timeoutMillis);

    }

    public void updateTimeout(int newTimeoutMinutes) {
        this.timeoutMinutes = newTimeoutMinutes;
        startTimer();
    }

    public void stopTimer() {
        isTrackingActive = false; // CRITICAL: Stop listening to mouse/key movements
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
    }

    public void resetTimer() {
        // Only reset if tracking is explicitly active and warning isn't showing
        if (!isTrackingActive || isWarningShowing) return;

        startTimer();
    }

    private void showWarningDialog() {
        if (!isTrackingActive || isWarningShowing) return;
        isWarningShowing = true;

        warningDialog = new Alert(Alert.AlertType.WARNING);
        if (stage != null) {
            warningDialog.initOwner(stage);
        }

        warningDialog.setTitle("Session Inactive");
        warningDialog.setHeaderText("Are you still there?");
        warningDialog.setContentText("Your session has been inactive and will expire in 30 seconds.");

        ButtonType imStillHereBtn = new ButtonType("I'm Still Here");
        warningDialog.getButtonTypes().setAll(imStillHereBtn);

        Optional<ButtonType> result = warningDialog.showAndWait();

        if (result.isPresent() && result.get() == imStillHereBtn) {
            isWarningShowing = false;
            resetTimer();
        }
    }

    private void executeLogout() {
        if (warningDialog != null && warningDialog.isShowing()) {
            warningDialog.close();
        }

        isWarningShowing = false;
        stopTimer();

        if (logoutAction != null) {
            logoutAction.run();
        }
    }
}