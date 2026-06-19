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
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        // Ensure tracking is completely killed right when the login view displays
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

        if (!validateLogin(username, password)) {
            statusLabel.setText("Invalid username or password.");
            return;
        }

        // 1. Load the Dashboard Scene Context
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/frontend_emp_pass_slip/view/MainLayout.fxml"));
        Scene scene = new Scene(loader.load(), 1280, 768);
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.setScene(scene);
        stage.centerOnScreen();

        // 2. Fetch runtime database settings limits and wake up tracking controls
        int savedTimeout = AppSettingsManager.getInstance().getAutoLogoutTimer();
        SessionManager.getInstance().updateTimeout(savedTimeout);
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

    @FXML
    private void showTermsAndConditions() {
        showTextDialog(
                "Terms and Conditions",
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

    @FXML
    private void showPrivacyPolicy() {
        showTextDialog(
                "Privacy Policy",
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
                        + "your system administrator or HR representative."
        );
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
    @FXML
    private void handleTerms() {
        System.out.println("Terms and Conditions clicked");
        showTextDialog(
                "Terms and Conditions",
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

    @FXML
    private void handlePrivacy() {
        System.out.println("Privacy and Policy clicked");
        showTextDialog(
                "Privacy Policy",
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
                        + "your system administrator or HR representative."
        );

    }
}