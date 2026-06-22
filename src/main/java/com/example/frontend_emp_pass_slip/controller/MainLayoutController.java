package com.example.frontend_emp_pass_slip.controller;

import backend.app.HeaderStatsRepository;
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
import java.time.format.DateTimeFormatter;
import javafx.scene.layout.BorderPane;

public class MainLayoutController {

    @FXML private StackPane contentArea;
    @FXML private BorderPane mainRoot;
    @FXML private Label employeesOutLabel;
    @FXML private Label currentDateLabel;
    @FXML private Label pageTitleLabel;

    // 👇 1. ADD ALL SIDEBAR TOGGLE BUTTONS HERE 👇
    // (Make sure these fx:id names match your FXML file exactly)
    @FXML private ToggleButton dashboardButton;
    @FXML private ToggleButton employeeButton;
    @FXML private ToggleButton passSlipButton;
    @FXML private ToggleButton monitoringButton;
    @FXML private ToggleButton reportsButton;
    @FXML private ToggleButton settingsButton;

    private final HeaderStatsRepository headerStatsRepository = new HeaderStatsRepository();

    @FXML
    private void initialize() {
        // Mark Dashboard active on initial startup
        updateActiveSidebarButton(dashboardButton);
        loadDashboard();

        // 👈 NEW: Apply Role-Based Access Control
        applyRolePermissions();
    }

    // 👇 NEW: Helper method to hide buttons based on role 👇
    private void applyRolePermissions() {
        String role = backend.auth.SessionManager.getInstance().getCurrentUserRole();

        if (role == null) return;

        switch (role) {
            case "Director":
            case "Directors": // 👈 Handles the plural DB string match
                hideButton(employeeButton);
                hideButton(passSlipButton);

                break;

            case "Guard":
            case "Guards": // 👈 Handles the plural DB string match
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

    // 👇 NEW: Helper method to cleanly remove a button from the UI 👇
    private void hideButton(ToggleButton button) {
        if (button != null) {
            button.setVisible(false);
            button.setManaged(false); // Removes the empty gap in the VBox
        }
    }

    public void loadDashboard() {
        setPageTitle("Dashboard");
        loadView("Dashboard.fxml");
        updateHeaderStats();
        mainRoot.setUserData(this);
        updateActiveSidebarButton(dashboardButton); // 👈 Update selection
    }

    public void loadEmployeeMgmt() {
        setPageTitle("Employee Management");
        loadView("EmployeeManagement.fxml");
        updateActiveSidebarButton(employeeButton); // 👈 Changed to employeeButton
    }

    public void loadPassSlip() {
        setPageTitle("Pass Slip Issuance");
        loadView("PassSlipIssuance.fxml");
        updateHeaderStats();
        updateActiveSidebarButton(passSlipButton); // 👈 Update selection (FIXES QUICK ACTION BUG!)
    }

    public void loadMonitoring() {
        setPageTitle("Monitoring");
        loadView("Monitoring.fxml");
        updateHeaderStats();
        updateActiveSidebarButton(monitoringButton); // 👈 Update selection
    }

    public void loadReports() {
        setPageTitle("Reports");
        loadView("Reports.fxml");
        updateActiveSidebarButton(reportsButton); // 👈 Update selection
    }

    @FXML
    private void loadSettings() {
        setPageTitle("Settings");
        loadView("Settings.fxml");
        updateActiveSidebarButton(settingsButton); // 👈 Update selection
    }

    // 👇 2. HELPER METHOD TO TOGGLE VISUAL STATES 👇
    private void updateActiveSidebarButton(ToggleButton activeButton) {
        // Deselect everything first to ensure UI consistency
        if (dashboardButton != null) dashboardButton.setSelected(false);
        if (employeeButton != null) employeeButton.setSelected(false); // 👈 Changed to employeeButton
        if (passSlipButton != null) passSlipButton.setSelected(false);
        if (monitoringButton != null) monitoringButton.setSelected(false);
        if (reportsButton != null) reportsButton.setSelected(false);
        if (settingsButton != null) settingsButton.setSelected(false);

        // Select the active screen's button
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

        currentDateLabel.setText(
                LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy"))
        );
    }

    private void loadView(String fxmlFileName) {
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