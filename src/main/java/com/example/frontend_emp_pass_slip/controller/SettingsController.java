package com.example.frontend_emp_pass_slip.controller;

import backend.auth.PasswordHasher;
import backend.app.ActivityLogRepository;
import backend.app.ActivityLogRepository.ActivityEntry;
import backend.app.AppSettingsManager;
import backend.app.SessionManager;
import backend.app.SettingsRepository;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SettingsController {

    private static final Logger LOG = Logger.getLogger(SettingsController.class.getName());

    @FXML private Label lastLoginLabel;
    @FXML private ComboBox<String> timeFormatComboBox;
    @FXML private ComboBox<String> dateFormatComboBox;
    @FXML private TextField autoLogoutField;
    @FXML private Label statusLabel;

    private final SettingsRepository settingsRepository = new SettingsRepository();
    private final ActivityLogRepository activityLogRepository = new ActivityLogRepository();

    @FXML
    private void initialize() {
        timeFormatComboBox.getItems().addAll("12h", "24h");
        dateFormatComboBox.getItems().addAll("YYYY-MM-DD", "DD/MM/YYYY", "MM/DD/YYYY");

        lastLoginLabel.setText(
                AppSettingsManager.getInstance().formatDateTime(LocalDateTime.now())
        );

        loadSettings();
    }

    private void loadSettings() {
        Map<String, String> settings = settingsRepository.loadSettings();
        timeFormatComboBox.setValue(settings.getOrDefault("time_format", "24h"));
        dateFormatComboBox.setValue(settings.getOrDefault("date_format", "YYYY-MM-DD"));
        autoLogoutField.setText(settings.getOrDefault("auto_logout_minutes", "30"));
    }

    @FXML
    private void saveSettings() {
        String timeFormat  = timeFormatComboBox.getValue();
        String dateFormat  = dateFormatComboBox.getValue();
        String autoLogout  = autoLogoutField.getText().trim();

        // 1. Validation
        if (timeFormat == null || dateFormat == null || autoLogout.isBlank()) {
            showStatus("Please complete all settings fields.", "#b00020");
            return;
        }

        try {
            int minutes = Integer.parseInt(autoLogout);
            if (minutes <= 0) {
                showStatus("Auto-logout timer must be greater than 0.", "#b00020");
                return;
            }
        } catch (NumberFormatException e) {
            showStatus("Auto-logout timer must be a number.", "#b00020");
            return;
        }

        // 2. Execution
        boolean saved = settingsRepository.saveSettings(timeFormat, dateFormat, autoLogout);

        if (saved) {
            AppSettingsManager.getInstance().refreshSettings();
            int newTimerValue = AppSettingsManager.getInstance().getAutoLogoutTimer();
            SessionManager.getInstance().updateTimeout(newTimerValue);
            lastLoginLabel.setText(AppSettingsManager.getInstance().formatDateTime(LocalDateTime.now()));

            // Success text feedback
            showStatus("✓ System settings saved successfully.", "#0b6b2b");
        } else {
            showStatus("⚠ Failed to save settings. Please try again.", "#b00020");
        }
    }

    // ── Change Password ───────────────────────────────────────────────────────

    @FXML
    private void changePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Update your account password");

        PasswordField currentField = new PasswordField();
        currentField.setPromptText("Current password");

        PasswordField newField = new PasswordField();
        newField.setPromptText("New password (min 6 characters)");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm new password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 100, 10, 10));

        grid.add(new Label("Current Password:"), 0, 0);
        grid.add(currentField, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        javafx.scene.Node saveButtonNode = dialog.getDialogPane().lookupButton(saveBtn);

        saveButtonNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String current = currentField.getText();
            String newPass  = newField.getText();
            String confirm  = confirmField.getText();

            // 1. Verify Current Password (using hasher to match DB)
            char[] currentChars = current.toCharArray();
            String storedHash = loadStoredHash();

            // 🌟 ADDED PLAIN-TEXT FALLBACK FOR INITIAL SETUP 🌟
            boolean isValid = false;
            if (storedHash != null) {
                if (current.equals(storedHash)) {
                    isValid = true; // Matches literal 'admin123' text currently in database
                } else {
                    try {
                        isValid = PasswordHasher.getInstance().verify(currentChars, storedHash);
                    } catch (Exception e) {
                        isValid = false; // Fails safely if data isn't a valid hash structure yet
                    }
                }
            }

            // Check against our updated validity result
            if (!isValid) {
                showStatus("Change password failed: current password is incorrect.", "#b00020");
                showAlert(Alert.AlertType.ERROR, "Incorrect Password", "The current password you entered is incorrect.");
                java.util.Arrays.fill(currentChars, '\0');
                event.consume();
                return;
            }
            java.util.Arrays.fill(currentChars, '\0');

            // 2. Validate New Password
            if (newPass == null || newPass.length() < 6) {
                showStatus("Change password failed: new password must be at least 6 characters.", "#b00020");
                showAlert(Alert.AlertType.ERROR, "Weak Password", "New password must be at least 6 characters long.");
                event.consume();
                return;
            }

            // 3. Confirm Passwords Match
            if (!newPass.equals(confirm)) {
                showStatus("Change password failed: new passwords do not match.", "#b00020");
                showAlert(Alert.AlertType.ERROR, "Passwords Do Not Match", "The new password and confirmation password do not match.");
                event.consume();
                return;
            }

            // 4. Save to Database
            boolean success = updatePasswordInDatabase(newPass);
            if (success) {
                backend.logging.ActivityLogger.getInstance().log("PASSWORD_CHANGE", "Administrator changed their password.");
            } else {
                showStatus("Database error: Failed to persist new password.", "#b00020");
                showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to update the password in the database.");
                event.consume();
            }
        });

        dialog.showAndWait().ifPresent(response -> {
            if (response == saveBtn) {
                showStatus("Password changed successfully.", "#0b6b2b");
                showAlert(Alert.AlertType.INFORMATION, "Password Changed", "Your password has been updated successfully.");
            }
        });
    }

    // ── Database Helpers for Password ─────────────────────────────────────────

    private String loadStoredHash() {
        java.sql.Connection c = null;
        // 🔥 UPDATED FOR YOUR REAL SUPABASE TABLE ('users')
        String sql = "SELECT password_hash FROM users WHERE username = 'admin'";

        try {
            c = backend.db.ConnectionPoolManager.getInstance().acquire();
            try (java.sql.PreparedStatement ps = c.prepareStatement(sql);
                 java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
                return null;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load stored hash from database.", e);
            return null;
        } finally {
            if (c != null) {
                backend.db.ConnectionPoolManager.getInstance().release(c);
            }
        }
    }

    private boolean updatePasswordInDatabase(String newPassword) {
        java.sql.Connection connection = null;
        // 🔥 UPDATED FOR YOUR REAL SUPABASE TABLE ('users')
        String sql = "UPDATE users SET password_hash = ? WHERE username = 'admin'";

        try {
            char[] passwordChars = newPassword.toCharArray();
            String hashedPassword = PasswordHasher.getInstance().hash(passwordChars);
            java.util.Arrays.fill(passwordChars, '\0');

            connection = backend.db.ConnectionPoolManager.getInstance().acquire();
            try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, hashedPassword);
                return statement.executeUpdate() > 0;
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to update password in database.", e);
            return false;
        } finally {
            if (connection != null) {
                backend.db.ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }
    // ── View Activity Log ─────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @FXML
    private void viewActivityLog() {
        List<ActivityEntry> entries = activityLogRepository.findRecentActivity(100);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Activity Log");
        dialog.setHeaderText("Recent System Activity (last 100 events)");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefSize(780, 480);

        TableView<ActivityEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ActivityEntry, String> colSlip = new TableColumn<>("Slip ID");
        colSlip.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().slipId()));
        colSlip.setPrefWidth(70);

        TableColumn<ActivityEntry, String> colId = new TableColumn<>("Employee ID");
        colId.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().employeeId()));
        colId.setPrefWidth(110);

        TableColumn<ActivityEntry, String> colName = new TableColumn<>("Employee Name");
        colName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().employeeName()));
        colName.setPrefWidth(180);

        TableColumn<ActivityEntry, String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().action()));
        colAction.setPrefWidth(200);

        TableColumn<ActivityEntry, String> colTime = new TableColumn<>("Timestamp");
        colTime.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().timestamp()));
        colTime.setPrefWidth(190);

        table.getColumns().addAll(List.of(colSlip, colId, colName, colAction, colTime));

        if (entries.isEmpty()) {
            table.setPlaceholder(new Label("No activity records found."));
        } else {
            table.setItems(FXCollections.observableArrayList(entries));
        }

        VBox content = new VBox(10, table);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
        showStatus("Activity log viewed — " + entries.size() + " records loaded.", "#0b6b2b");
    }

    // ── Backup Database ───────────────────────────────────────────────────────

    @FXML
    private void backupDatabase() {
        List<ActivityEntry> entries = activityLogRepository.findRecentActivity(Integer.MAX_VALUE);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Database Backup (CSV)");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        String timestamp = LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        fileChooser.setInitialFileName("PassSlipBackup_" + timestamp + ".csv");

        File file = fileChooser.showSaveDialog(statusLabel.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter writer = new PrintWriter(file)) {
            writer.println("PASS SLIP SYSTEM — DATABASE BACKUP");
            writer.println("Generated: " + LocalDateTime.now());
            writer.println();
            writer.println("Slip ID,Employee ID,Employee Name,Action,Timestamp");

            for (ActivityEntry entry : entries) {
                writer.println(
                        csvCell(entry.slipId()) + "," +
                                csvCell(entry.employeeId()) + "," +
                                csvCell(entry.employeeName()) + "," +
                                csvCell(entry.action()) + "," +
                                csvCell(entry.timestamp())
                );
            }

            showStatus("Database backup saved successfully to: " + file.getName(), "#0b6b2b");
            showAlert(Alert.AlertType.INFORMATION, "Backup Successful",
                    "Database backup saved to:\n" + file.getAbsolutePath());

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to write database backup to CSV.", e);
            showStatus("Backup failed. See console for details.", "#b00020");
            showAlert(Alert.AlertType.ERROR, "Backup Failed",
                    "Could not write the backup file. Please check that the location is accessible.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String csvCell(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void showStatus(String message, String hexColor) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: " + hexColor + ";");
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}