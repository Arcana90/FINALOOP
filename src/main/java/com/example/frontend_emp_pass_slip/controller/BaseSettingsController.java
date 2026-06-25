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
    
    public abstract class BaseSettingsController {
    
        private static final Logger LOG = Logger.getLogger(BaseSettingsController.class.getName());
    
        @FXML protected Label accountHeaderLabel;
        @FXML protected Label nameLabel;
        @FXML protected Label roleLabel;
        @FXML protected Label departmentLabel;
        @FXML protected Label accessLevelLabel;
        @FXML protected Label usernameLabel;
    
        @FXML protected Label lastLoginLabel;
        @FXML protected ComboBox<String> timeFormatComboBox;
        @FXML protected ComboBox<String> dateFormatComboBox;
        @FXML protected TextField autoLogoutField;
        @FXML protected Label statusLabel;
    
        @FXML protected Button activityLogBtn;
        @FXML protected Button backupDbBtn;
    
        private final SettingsRepository settingsRepository = new SettingsRepository();
        private final ActivityLogRepository activityLogRepository = new ActivityLogRepository();
    
        protected abstract String getCurrentUsername();
        protected abstract void setupProfileUI();
        protected abstract void applySecurityRestrictions();
    
        @FXML
        public void initialize() {
            timeFormatComboBox.getItems().addAll("12h", "24h");
            dateFormatComboBox.getItems().addAll("YYYY-MM-DD", "DD/MM/YYYY", "MM/DD/YYYY");
    
            lastLoginLabel.setText(AppSettingsManager.getInstance().formatDateTime(LocalDateTime.now()));
    
            setupProfileUI();
            applySecurityRestrictions();
            loadSettings();
        }
    
        private void loadSettings() {
            Map<String, String> settings = settingsRepository.loadSettings(getCurrentUsername());
            timeFormatComboBox.setValue(settings.getOrDefault("time_format", "24h"));
            dateFormatComboBox.setValue(settings.getOrDefault("date_format", "YYYY-MM-DD"));
            autoLogoutField.setText(settings.getOrDefault("auto_logout_minutes", "30"));
        }

            @FXML
            protected void saveSettings() {
                String timeFormat  = timeFormatComboBox.getValue();
                String dateFormat  = dateFormatComboBox.getValue();
                String autoLogout  = autoLogoutField.getText().trim();

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

                boolean saved = settingsRepository.saveSettings(getCurrentUsername(), timeFormat, dateFormat, autoLogout);

                if (saved) {
                    AppSettingsManager.getInstance().refreshSettings();
                    int newTimerValue = AppSettingsManager.getInstance().getAutoLogoutTimer();
                    SessionManager.getInstance().updateTimeout(newTimerValue);
                    lastLoginLabel.setText(AppSettingsManager.getInstance().formatDateTime(LocalDateTime.now()));
                    showStatus("✓ System settings saved successfully.", "#0b6b2b");
                } else {
                    showStatus("⚠ Failed to save settings. Please try again.", "#b00020");
                }
            }
    
        @FXML
        protected void changePassword() {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Change Password");
            dialog.setHeaderText("Update account password for: " + getCurrentUsername());
    
            PasswordField currentField = new PasswordField();
            currentField.setPromptText("Current password");
            PasswordField newField = new PasswordField();
            newField.setPromptText("New password (min 6 characters)");
            PasswordField confirmField = new PasswordField();
            confirmField.setPromptText("Confirm new password");
    
            GridPane grid = new GridPane();
            grid.setHgap(10); grid.setVgap(10);
            grid.setPadding(new Insets(20, 100, 10, 10));
            grid.add(new Label("Current Password:"), 0, 0); grid.add(currentField, 1, 0);
            grid.add(new Label("New Password:"), 0, 1); grid.add(newField, 1, 1);
            grid.add(new Label("Confirm Password:"), 0, 2); grid.add(confirmField, 1, 2);
    
            dialog.getDialogPane().setContent(grid);
            ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
    
            javafx.scene.Node saveButtonNode = dialog.getDialogPane().lookupButton(saveBtn);
            saveButtonNode.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String current = currentField.getText();
                String newPass  = newField.getText();
                String confirm  = confirmField.getText();
    
                char[] currentChars = current.toCharArray();
                String storedHash = loadStoredHash();
                boolean isValid = false;
    
                // 🔍 DEBUG — remove once issue is resolved
                System.out.println("DEBUG username = [" + getCurrentUsername() + "]");
                System.out.println("DEBUG storedHash = [" + storedHash + "]");
    
                if (storedHash != null) {
                    if (current.equals(storedHash)) {
                        isValid = true;
                    } else {
                        try {
                            isValid = PasswordHasher.getInstance().verify(currentChars, storedHash);
                        } catch (Exception e) { isValid = false; }
                    }
                }
    
                if (!isValid) {
                    showStatus("Change password failed: current password incorrect.", "#b00020");
                    showAlert(Alert.AlertType.ERROR, "Error", "Current password is incorrect.");
                    java.util.Arrays.fill(currentChars, '\0');
                    event.consume();
                    return;
                }
                java.util.Arrays.fill(currentChars, '\0');
    
                if (newPass == null || newPass.length() < 6) {
                    showStatus("Change password failed: min 6 characters.", "#b00020");
                    showAlert(Alert.AlertType.ERROR, "Error", "New password must be at least 6 characters.");
                    event.consume();
                    return;
                }
    
                if (!newPass.equals(confirm)) {
                    showStatus("Change password failed: passwords do not match.", "#b00020");
                    showAlert(Alert.AlertType.ERROR, "Error", "Passwords do not match.");
                    event.consume();
                    return;
                }
    
                if (updatePasswordInDatabase(newPass)) {
                    backend.logging.ActivityLogger.getInstance().log("PASSWORD_CHANGE", getCurrentUsername() + " changed password.");
                } else {
                    showStatus("Database error: Failed to update.", "#b00020");
                    showAlert(Alert.AlertType.ERROR, "Error", "Failed to update database.");
                    event.consume();
                }
            });
    
            dialog.showAndWait().ifPresent(response -> {
                if (response == saveBtn) {
                    showStatus("Password changed successfully.", "#0b6b2b");
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Password updated.");
                }
            });
        }
    
        protected String loadStoredHash() {
            java.sql.Connection c = null;
            String sql = "SELECT password_hash FROM users WHERE username = ?";
            try {
                c = backend.db.ConnectionPoolManager.getInstance().acquire();
                try (java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, getCurrentUsername());
                    try (java.sql.ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getString("password_hash");
                    }
                }
            } catch (Exception e) { LOG.log(Level.SEVERE, "Failed to load hash.", e); }
            finally { if (c != null) backend.db.ConnectionPoolManager.getInstance().release(c); }
            return null;
        }
    
        protected boolean updatePasswordInDatabase(String newPassword) {
            java.sql.Connection connection = null;
            String sql = "UPDATE users SET password_hash = ? WHERE username = ?";
            try {
                char[] passwordChars = newPassword.toCharArray();
                String hashedPassword = PasswordHasher.getInstance().hash(passwordChars);
                java.util.Arrays.fill(passwordChars, '\0');
    
                connection = backend.db.ConnectionPoolManager.getInstance().acquire();
                try (java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, hashedPassword);
                    statement.setString(2, getCurrentUsername());
                    return statement.executeUpdate() > 0;
                }
            } catch (Exception e) { LOG.log(Level.SEVERE, "Failed to update password.", e); return false; }
            finally { if (connection != null) backend.db.ConnectionPoolManager.getInstance().release(connection); }
        }
    
        @SuppressWarnings("deprecation")
        @FXML
        protected void viewActivityLog() {
            List<ActivityEntry> entries = activityLogRepository.findRecentActivity(100);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Activity Log");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setPrefSize(780, 480);
    
            TableView<ActivityEntry> table = new TableView<>();
            TableColumn<ActivityEntry, String> colSlip = new TableColumn<>("Slip ID");
            colSlip.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().slipId()));
            TableColumn<ActivityEntry, String> colName = new TableColumn<>("Employee Name");
            colName.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().employeeName()));
            TableColumn<ActivityEntry, String> colAction = new TableColumn<>("Action");
            colAction.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().action()));
            TableColumn<ActivityEntry, String> colTime = new TableColumn<>("Timestamp");
            colTime.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().timestamp()));
    
            table.getColumns().addAll(List.of(colSlip, colName, colAction, colTime));
            table.setItems(FXCollections.observableArrayList(entries));
    
            VBox content = new VBox(10, table);
            content.setPadding(new Insets(10));
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        }
    
        @FXML
        protected void backupDatabase() {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Database Backup (CSV)");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            fileChooser.setInitialFileName("PassSlipBackup_" + timestamp + ".csv");
    
            File file = fileChooser.showSaveDialog(statusLabel.getScene().getWindow());
            if (file == null) return;
    
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("PASS SLIP SYSTEM — DATABASE BACKUP");
                List<ActivityEntry> entries = activityLogRepository.findRecentActivity(Integer.MAX_VALUE);
                for (ActivityEntry entry : entries) {
                    writer.println("\"" + entry.slipId() + "\",\"" + entry.action() + "\",\"" + entry.timestamp() + "\"");
                }
                showStatus("Backup saved.", "#0b6b2b");
            } catch (Exception e) { showStatus("Backup failed.", "#b00020"); }
        }
    
        protected void showStatus(String message, String hexColor) {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + hexColor + ";");
        }
    
        protected void showAlert(Alert.AlertType type, String title, String message) {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        }
    }