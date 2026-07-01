package com.example.frontend_emp_pass_slip.controller;

import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;
import backend.app.AppSettingsManager;
import backend.auth.AuthSessionManager; // 🌟 Updated Import!
import backend.auth.PasswordHasher;
import backend.db.ConnectionPoolManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Button;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;
    @FXML private ToggleGroup roleToggleGroup;
    @FXML private Label roleSubtitleLabel;
    @FXML private Button loginButton;
    @FXML private VBox loginCard;

    // Text Labels injected to apply full readability overrides
    @FXML private Label welcomePortalLabel;
    @FXML private Label selectPortalLabel;
    @FXML private Label usernameLabel;
    @FXML private Label passwordLabel;

    @FXML
    private void handleRoleChange() {
        ToggleButton selectedRole = (ToggleButton) roleToggleGroup.getSelectedToggle();

        if (selectedRole != null) {
            String role = selectedRole.getText();

            // Unified clean base label style rule for maximum contrast
            usernameField.clear();
            passwordField.clear();
            statusLabel.setText("   ");
            String baseLabelStyle = "-fx-text-fill: #ffffff;";
            welcomePortalLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
            selectPortalLabel.setStyle(baseLabelStyle);
            usernameLabel.setStyle(baseLabelStyle);
            passwordLabel.setStyle(baseLabelStyle);

            switch (role) {
                case "Administrators":
                    roleSubtitleLabel.setText("Sign in to your system administrator account");
                    usernameField.setPromptText("Enter admin username");

                    // Crimson Maroon Theme
                    loginCard.setStyle("-fx-background-color: #2b1111; -fx-border-color: #800000; -fx-border-radius: 12; -fx-background-radius: 12;");
                    loginButton.setStyle("-fx-background-color: #800000; -fx-text-fill: #ffffff; -fx-background-radius: 4;");
                    roleSubtitleLabel.setStyle("-fx-text-fill: #ff9999;");
                    break;

                case "Guards":
                    roleSubtitleLabel.setText("Sign in to your gate checkpoint station");
                    usernameField.setPromptText("Enter badge number or guard ID");

                    // Security Navy Blue Theme
                    loginCard.setStyle("-fx-background-color: #0f172a; -fx-border-color: #1a365d; -fx-border-radius: 12; -fx-background-radius: 12;");
                    loginButton.setStyle("-fx-background-color: #1a365d; -fx-text-fill: #ffffff; -fx-background-radius: 4;");
                    roleSubtitleLabel.setStyle("-fx-text-fill: #90cdf4;");
                    break;

                case "Directors":
                    roleSubtitleLabel.setText("Sign in to your executive oversight panel");
                    usernameField.setPromptText("Enter director email or username");

                    // Executive Emerald Theme
                    loginCard.setStyle("-fx-background-color: #062719; -fx-border-color: #064e3b; -fx-border-radius: 12; -fx-background-radius: 12;");
                    loginButton.setStyle("-fx-background-color: #064e3b; -fx-text-fill: #ffffff; -fx-background-radius: 4;");
                    roleSubtitleLabel.setStyle("-fx-text-fill: #6ee7b7;");
                    break;
            }
        }
    }

    @FXML
    private void initialize() {
        // Run standard configurations to ensure text turns white immediately on setup
        handleRoleChange();
    }

    @FXML
    private void login() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        // 🌟 1. UI UX: Disable button and show loading text so the user knows it's working
        loginButton.setDisable(true);
        String originalButtonText = loginButton.getText();
        loginButton.setText("Authenticating...");
        statusLabel.setText("");

        // 🌟 2. BACKGROUND THREAD: Do the heavy database and password hashing here!
        CompletableFuture.supplyAsync(() -> {
            // This runs in the background and will not freeze the UI
            return validateAndEstablishSession(username, password);
        }).thenAccept(isValid -> {
            // 🌟 3. BACK TO UI THREAD: Once the background task finishes, update the screen
            Platform.runLater(() -> {
                if (isValid) {
                    try {
                        loadMainDashboard();
                    } catch (IOException e) {
                        e.printStackTrace();
                        statusLabel.setText("System error: Could not load dashboard.");
                        resetLoginButton(originalButtonText);
                    }
                } else {
                    statusLabel.setText("Invalid username or password.");
                    resetLoginButton(originalButtonText);
                }
            });
        });
    }

    // --- Helper Methods to keep your code clean ---

    private void resetLoginButton(String originalText) {
        loginButton.setDisable(false);
        loginButton.setText(originalText);
    }

    private void loadMainDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/frontend_emp_pass_slip/view/MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 768);
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(scene);
        stage.centerOnScreen();

        int savedTimeout = AppSettingsManager.getInstance().getAutoLogoutTimer();
        backend.app.SessionManager.getInstance().updateTimeout(savedTimeout);
    }

    private boolean validateAndEstablishSession(String username, String password) {
        Connection c = null;
        String sql = "SELECT password_hash, role FROM users WHERE username = ?";

        try {
            c = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        String role = rs.getString("role");

                        boolean isValid = false;
                        if (password.equals(storedHash)) {
                            isValid = true;
                        } else {
                            char[] passwordChars = password.toCharArray();
                            isValid = PasswordHasher.getInstance().verify(passwordChars, storedHash);
                            Arrays.fill(passwordChars, '\0');
                        }

                        if (isValid) {
                            // 🌟 1. Tell the renamed Auth Manager who logged in
                            AuthSessionManager.getInstance().createSession(null, role, username);

                            // 🌟 2. FORCE the Settings Manager to load THIS specific user's settings!
                            AppSettingsManager.getInstance().refreshSettings();

                            // (Notice we removed the old setCurrentUser line here!)
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Database error during login validation.");
        } finally {
            if (c != null) {
                ConnectionPoolManager.getInstance().release(c);
            }
        }
        return false;
    }

    private void showTextDialog(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);

        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefWidth(500);
        textArea.setPrefHeight(400);

        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    @FXML private void handleTerms() {
        showTextDialog(                "Terms and Conditions",
                "Terms and Conditions — Pass Slip System",
                "1. ACCEPTANCE OF TERMS\n"
                        + "By using this system, you agree to be bound by these Terms and Conditions. "
                        + "Unauthorized access or use of this system is strictly prohibited.\n\n"
                        + "2. AUTHORIZED USE\n"
                        + "This system is intended solely for authorized personnel of the organization "
                        + "for the purpose of managing employee pass slips and monitoring employee "
                        + "time-in and time-out records.\n\n"
                        + "3. ACCURACY OF INFORMATION\n"
                        + "Users are responsible for the accuracy and completeness of all data entered "
                        + "into the system. Falsification of records may result in disciplinary action.\n\n"
                        + "4. ACCOUNT SECURITY\n"
                        + "Users must keep their login credentials confidential. You are responsible for "
                        + "all activities that occur under your account. Report any unauthorized access "
                        + "to the system administrator immediately.\n\n"
                        + "5. DATA OWNERSHIP\n"
                        + "All data entered into this system remains the property of the organization. "
                        + "Users must not export, copy, or distribute system data without prior written "
                        + "authorization from management.\n\n"
                        + "6. MODIFICATIONS\n"
                        + "The organization reserves the right to modify these Terms and Conditions at "
                        + "any time. Continued use of the system constitutes acceptance of any changes.\n\n"
                        + "7. TERMINATION\n"
                        + "Access to this system may be revoked at any time at the discretion of the "
                        + "system administrator or management, with or without notice.\n\n"
                        + "By logging in, you confirm that you have read, understood, and agree to "
                        + "these Terms and Conditions."
        );
    }

    @FXML private void handlePrivacy() {
        showTextDialog("Privacy Policy",
                "Privacy Policy — Pass Slip System",
                "EFFECTIVE DATE: January 1, 2025\n\n"
                        + "1. INFORMATION WE COLLECT\n"
                        + "The Pass Slip System collects the following personal information for "
                        + "operational purposes:\n"
                        + "  • Employee name, ID, department, and position\n"
                        + "  • Contact information (email, contact number)\n"
                        + "  • Time-in and time-out records\n"
                        + "  • Pass slip details including destination and reason for leaving\n\n"
                        + "2. HOW WE USE YOUR INFORMATION\n"
                        + "Collected data is used exclusively to:\n"
                        + "  • Track and manage employee movements within and outside the workplace\n"
                        + "  • Generate operational reports for management\n"
                        + "  • Ensure workplace security and compliance\n\n"
                        + "3. DATA RETENTION\n"
                        + "Employee records and pass slip data are retained for a period as defined "
                        + "by the organization's data governance policy. Records may be archived "
                        + "or deleted at the discretion of the system administrator.\n\n"
                        + "4. DATA SECURITY\n"
                        + "We implement reasonable technical and organizational measures to protect "
                        + "your personal information against unauthorized access, loss, or misuse. "
                        + "Access to the system is restricted to authorized personnel only.\n\n"
                        + "5. DATA SHARING\n"
                        + "Personal information collected through this system is not sold, rented, "
                        + "or shared with third parties outside the organization, except where "
                        + "required by law.\n\n"
                        + "6. YOUR RIGHTS\n"
                        + "Employees may request access to, correction of, or deletion of their "
                        + "personal data by contacting the HR department or system administrator.\n\n"
                        + "7. CONTACT\n"
                        + "For questions or concerns regarding this Privacy Policy, please contact "
                        + "your system administrator or HR representative.");
    }
}