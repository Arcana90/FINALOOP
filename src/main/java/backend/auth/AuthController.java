package backend.auth;

import backend.shared.ApplicationConstants;
import backend.validation.ValidationException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * JavaFX controller for {@code /fxml/login.fxml} and {@code /fxml/lock.fxml}.
 *
 * <p>This single controller handles both the initial login sequence and the
 * unlock-in-place sequence. The FXML {@code fx:id="usernameField"} is
 * {@code setVisible(false)} on the lock screen, making the username row invisible.
 *
 * <p>Flow:
 * <pre>
 *   [Button click] -> onLoginButtonPressed()
 *       -> AdminAuthValidator   (field constraints)
 *       -> AuthenticationService.login() | .unlock()
 *       -> SessionManager (token created)
 *       -> ActivityLogger (immutable log)
 *       -> navigateToDashboard()
 * </pre>
 */
public final class AuthController implements Initializable {

    private static final Logger LOG = Logger.getLogger(AuthController.class.getName());

    // ── FXML bindings ────────────────────────────────────────────────────────────
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;
    @FXML private Label         usernameErrorLabel;
    @FXML private Label         passwordErrorLabel;

    /** Set to {@code true} by the FXML controller factory when loaded as the lock screen. */
    private boolean unlockMode = false;

    // ── Dependencies ─────────────────────────────────────────────────────────────
    private final AuthenticationService authService = AuthenticationService.getInstance();

    // ── Initializable ────────────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        clearErrors();
        if (unlockMode) {
            usernameField.setVisible(false);
            usernameField.setManaged(false);
            loginButton.setText("Unlock");
        }
        // Allow Enter key on the password field to trigger login
        passwordField.setOnAction(event -> onLoginButtonPressed());
    }

    /** Called by the controller factory to switch this instance into unlock mode. */
    public void setUnlockMode(boolean unlockMode) {
        this.unlockMode = unlockMode;
    }

    // ── FXML event handlers ──────────────────────────────────────────────────────

    @FXML
    private void onLoginButtonPressed() {
        clearErrors();
        loginButton.setDisable(true);

        String username  = usernameField.getText().strip();
        char[] password  = passwordField.getText().toCharArray();

        try {
            if (unlockMode) {
                authService.unlock(password);
            } else {
                authService.login(username, password, this::onSessionAutoLocked);
            }
            navigateToDashboard();

        } catch (ValidationException e) {
            displayFieldErrors(e.getFieldErrors());
        } catch (AuthenticationException e) {
            showGlobalError(e.getMessage());
        } catch (Exception e) {
            LOG.severe("Unexpected error during authentication: " + e.getMessage());
            showGlobalError("An unexpected error occurred. Please contact support.");
        } finally {
            // Zero out password array immediately after use
            java.util.Arrays.fill(password, '\0');
            passwordField.clear();
            loginButton.setDisable(false);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Callback executed (on the watchdog thread) when the session idle-timer fires.
     * Dispatches the lock-screen navigation to the JavaFX thread.
     */
    private void onSessionAutoLocked() {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        getClass().getResource(ApplicationConstants.FXML_LOCK));
                AuthController lockController = new AuthController();
                lockController.setUnlockMode(true);
                loader.setController(lockController);
                Parent lockRoot = loader.load();

                Stage stage = (Stage) loginButton.getScene().getWindow();
                stage.setScene(new Scene(lockRoot));
                stage.setTitle("Session Locked");
            } catch (IOException e) {
                LOG.severe("Failed to navigate to lock screen: " + e.getMessage());
            }
        });
    }

    private void navigateToDashboard() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(ApplicationConstants.FXML_DASHBOARD));
            Parent dashRoot = loader.load();
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(dashRoot));
            stage.setTitle("Pass Slip Management — Dashboard");
        } catch (IOException e) {
            LOG.severe("Failed to navigate to dashboard: " + e.getMessage());
            showGlobalError("Navigation error. Please restart the application.");
        }
    }


    private void displayFieldErrors(Map<String, String> errors) {
        errors.forEach((field, msg) -> {
            switch (field) {
                case AdminAuthValidator.FIELD_USERNAME -> {
                    usernameErrorLabel.setText(msg);
                    usernameErrorLabel.setVisible(true);
                    usernameField.getStyleClass().add("field-error");
                }
                case AdminAuthValidator.FIELD_PASSWORD -> {
                    passwordErrorLabel.setText(msg);
                    passwordErrorLabel.setVisible(true);
                    passwordField.getStyleClass().add("field-error");
                }
                default -> showGlobalError(msg);
            }
        });
    }

    private void showGlobalError(String message) {
        errorLabel.setText(message);
        errorLabel.setTextFill(Color.RED);
        errorLabel.setVisible(true);
    }

    private void clearErrors() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        usernameErrorLabel.setText("");
        usernameErrorLabel.setVisible(false);
        passwordErrorLabel.setText("");
        passwordErrorLabel.setVisible(false);
        usernameField.getStyleClass().remove("field-error");
        passwordField.getStyleClass().remove("field-error");
    }
}
