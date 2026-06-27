package com.example.frontend_emp_pass_slip.controller;

import backend.auth.AuthSessionManager;
import backend.app.HeaderStatsRepository;
import backend.app.AppSettingsManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.LocalDate;
import javafx.scene.layout.BorderPane;


public class MainLayoutController {

    @FXML private StackPane contentArea;
    @FXML private BorderPane mainRoot;
    @FXML private Label employeesOutLabel;
    @FXML private Label currentDateLabel;
    @FXML private Label pageTitleLabel;

    @FXML private ToggleButton dashboardButton;
    @FXML private ToggleButton employeeButton;
    @FXML private ToggleButton passSlipButton;
    @FXML private ToggleButton monitoringButton;
    @FXML private ToggleButton reportsButton;
    @FXML private ToggleButton settingsButton;

    private final HeaderStatsRepository headerStatsRepository = new HeaderStatsRepository();

    // Tracks the current FXML so we can reload it instantly
    private String currentActiveFxml = "Dashboard.fxml";

    @FXML
    private void initialize() {
        updateActiveSidebarButton(dashboardButton);
        loadDashboard();
        applyRolePermissions();

        // Register the global settings listener
        AppSettingsManager.getInstance().addSettingsChangedListener(() -> {
            updateHeaderStats();
            loadView(currentActiveFxml);
        });
    }

    private void applyRolePermissions() {
        String role = backend.auth.AuthSessionManager.getInstance().getCurrentUserRole();

        if (role == null) return;

        switch (role) {
            case "Director":
            case "Directors":
                hideButton(dashboardButton);
                hideButton(employeeButton);
                hideButton(passSlipButton);

                updateActiveSidebarButton(monitoringButton);
                loadMonitoring();
                break;

            case "Guard":
            case "Guards":
                hideButton(dashboardButton);
                hideButton(employeeButton);
                hideButton(passSlipButton);
                hideButton(reportsButton);

                updateActiveSidebarButton(monitoringButton);
                loadMonitoring();
                break;

            case "Admin":
            case "Administrators":
            default:
                break;
        }
    }

    private void hideButton(ToggleButton button) {
        if (button != null) {
            button.setVisible(false);
            button.setManaged(false);
        }
    }

    public void loadDashboard() {
        setPageTitle("Dashboard");
        loadView("Dashboard.fxml");
        updateHeaderStats();
        mainRoot.setUserData(this);
        updateActiveSidebarButton(dashboardButton);
    }

    public void loadEmployeeMgmt() {
        setPageTitle("Employee Management");
        loadView("EmployeeManagement.fxml");
        updateActiveSidebarButton(employeeButton);
    }

    public void loadPassSlip() {
        setPageTitle("Pass Slip Issuance");
        loadView("PassSlipIssuance.fxml");
        updateHeaderStats();
        updateActiveSidebarButton(passSlipButton);
    }

    public void loadMonitoring() {
        setPageTitle("Monitoring");

        String role = backend.auth.AuthSessionManager.getInstance().getCurrentUserRole();
        if (role == null) role = "Admin";

        switch (role) {
            case "Director":
            case "Directors":
                loadView("MonitoringDirector.fxml");
                break;

            case "Guard":
            case "Guards":
                loadView("MonitoringGuard.fxml");
                break;

            case "Admin":
            case "Administrators":
            default:
                loadView("Monitoring.fxml");
                break;
        }

        updateHeaderStats();
        updateActiveSidebarButton(monitoringButton);
    }

    public void loadReports() {
        setPageTitle("Reports");
        loadView("Reports.fxml");
        updateActiveSidebarButton(reportsButton);
    }

    @FXML
    private void loadSettings() {
        setPageTitle("Settings");

        String role = backend.auth.AuthSessionManager.getInstance().getCurrentUserRole();
        if (role == null) role = "Admin";

        switch (role) {
            case "Director":
            case "Directors":
                loadView("DirectorSettings.fxml");
                break;

            case "Guard":
            case "Guards":
                loadView("GuardSettings.fxml");
                break;

            case "Admin":
            case "Administrators":
            default:
                loadView("Settings.fxml");
                break;
        }

        updateActiveSidebarButton(settingsButton);
    }

    private void updateActiveSidebarButton(ToggleButton activeButton) {
        if (dashboardButton != null) dashboardButton.setSelected(false);
        if (employeeButton != null) employeeButton.setSelected(false);
        if (passSlipButton != null) passSlipButton.setSelected(false);
        if (monitoringButton != null) monitoringButton.setSelected(false);
        if (reportsButton != null) reportsButton.setSelected(false);
        if (settingsButton != null) settingsButton.setSelected(false);

        if (activeButton != null) {
            activeButton.setSelected(true);
        }
    }

    private void setPageTitle(String title) {
        pageTitleLabel.setText(title);
    }

    private void updateHeaderStats() {
        int employeesOut = headerStatsRepository.countEmployeesOut();
        employeesOutLabel.setText(employeesOut + " out");

        // Dynamically format the header date
        String formattedDate = AppSettingsManager.getInstance().formatDate(LocalDate.now());
        currentDateLabel.setText(formattedDate);
    }

    private void loadView(String fxmlFileName) {
        this.currentActiveFxml = fxmlFileName; // Save current view state
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/frontend_emp_pass_slip/view/" + fxmlFileName)
            );

            Parent view = loader.load();
            contentArea.getChildren().setAll(view);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading: " + fxmlFileName);
        }
    }

    @FXML
    private void logout() throws IOException {
        backend.app.SessionManager.getInstance().stopTimer();
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/example/frontend_emp_pass_slip/view/Login.fxml")
        );

        Scene scene = new Scene(loader.load(), 1280, 768);
        Stage stage = (Stage) contentArea.getScene().getWindow();
        stage.setScene(scene);
        stage.centerOnScreen();
    }
}