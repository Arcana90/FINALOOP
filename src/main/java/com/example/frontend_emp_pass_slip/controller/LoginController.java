package com.example.frontend_emp_pass_slip.controller;

import backend.app.SessionManager;
import backend.app.AppSettingsManager;
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
    private void handleForgotPassword() {
        System.out.println("Forgot password link clicked!");
    }

    @FXML
    private void initialize() {
        // Run standard configurations to ensure text turns white immediately on setup
        handleRoleChange();
        SessionManager.getInstance().stopTimer();
    }

    @FXML
    private void login() throws IOException {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        // 👇 Pass username and password directly into the tracking validator
        if (!validateAndEstablishSession(username, password)) {
            statusLabel.setText("Invalid username or password.");
            return;
        }

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
        // 👇 UPDATED: Select both password_hash AND role from the DB
        String sql = "SELECT password_hash, role FROM users WHERE username = ?";

        try {
            c = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        String role = rs.getString("role"); // Fetch the user's role

                        boolean isValid = false;
                        if (password.equals(storedHash)) {
                            isValid = true;
                        } else {
                            char[] passwordChars = password.toCharArray();
                            isValid = backend.auth.PasswordHasher.getInstance().verify(passwordChars, storedHash);
                            Arrays.fill(passwordChars, '\0');
                        }

                        // 👇 If password is valid, initialize the active session context
                        if (isValid) {
                            backend.auth.SessionManager.getInstance().createSession(null, role, username);
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

    private boolean validateLogin(String username, String password) {
        Connection c = null;
        String sql = "SELECT password_hash FROM users WHERE username = ?";

        try {
            c = ConnectionPoolManager.getInstance().acquire();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");

                        if (password.equals(storedHash)) {
                            return true;
                        }

                        char[] passwordChars = password.toCharArray();
                        try {
                            boolean isMatch = PasswordHasher.getInstance().verify(passwordChars, storedHash);
                            Arrays.fill(passwordChars, '\0');
                            return isMatch;
                        } catch (Exception e) {
                            Arrays.fill(passwordChars, '\0');
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
        showTextDialog("Terms and Conditions", "Terms and Conditions — Pass Slip System", "Standard Terms apply...");
    }

    @FXML private void handlePrivacy() {
        showTextDialog("Privacy Policy", "Privacy Policy — Pass Slip System", "Standard Privacy Rules apply...");
    }
}