package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.DashboardJdbcRepository;
import backend.passslip.DashboardSlipRecord;
import backend.passslip.DashboardSummary;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import backend.app.AppSettingsManager;import javafx.scene.input.MouseEvent;
public class DashboardController {

    @FXML private Label totalEmployeesLabel;
    @FXML private Label activePassSlipsLabel;
    @FXML private Label todaysSlipsLabel;
    @FXML private Label totalRecordsLabel;
    @FXML private Label dashboardDateLabel; // <-- Add it right here!

    @FXML private Label currentlyOutBadgeLabel;
    @FXML private VBox currentlyOutList;
    @FXML private VBox recentActivityList;

    @FXML private Label officialBusinessLabel;
    @FXML private Label personalLabel;
    @FXML private Label returnedTodayLabel;
    @FXML private Label stillOutLabel;

    private final DashboardJdbcRepository dashboardRepository = new DashboardJdbcRepository();

    @FXML
    private void initialize() {
        loadDashboard();
    }

    private void loadDashboard() {
        DashboardSummary summary = dashboardRepository.getSummary();

        totalEmployeesLabel.setText(String.valueOf(summary.getTotalEmployees()));
        activePassSlipsLabel.setText(String.valueOf(summary.getActivePassSlips()));
        todaysSlipsLabel.setText(String.valueOf(summary.getTodaysSlips()));
        totalRecordsLabel.setText(String.valueOf(summary.getTotalRecords()));

        currentlyOutBadgeLabel.setText(String.valueOf(summary.getActivePassSlips()));

        officialBusinessLabel.setText(String.valueOf(summary.getOfficialBusinessToday()));
        personalLabel.setText(String.valueOf(summary.getPersonalToday()));
        returnedTodayLabel.setText(String.valueOf(summary.getReturnedToday()));
        stillOutLabel.setText(String.valueOf(summary.getStillOutToday()));

        populateCurrentlyOut(summary);
        populateRecentActivity(summary);
    }

    private void populateCurrentlyOut(DashboardSummary summary) {
        currentlyOutList.getChildren().clear();

        if (summary.getCurrentlyOut().isEmpty()) {
            Label emptyLabel = new Label("No employees currently out.");
            emptyLabel.getStyleClass().add("muted-text");
            currentlyOutList.getChildren().add(emptyLabel);
            return;
        }

        for (DashboardSlipRecord record : summary.getCurrentlyOut()) {
            // FORMAT THE TIME OUT
            String formattedTimeOut = AppSettingsManager.getInstance().formatTimeString(record.getTimeOut());

            currentlyOutList.getChildren().add(createNameLabel(record.getEmployeeName()));
            currentlyOutList.getChildren().add(createDetailLabel(
                    record.getDepartment() + " - Out " + formattedTimeOut + " - " + record.getDuration()
            ));
            currentlyOutList.getChildren().add(new Separator());
        }
    }

    private void populateRecentActivity(DashboardSummary summary) {
        recentActivityList.getChildren().clear();

        if (summary.getRecentActivity().isEmpty()) {
            Label emptyLabel = new Label("No recent activity yet.");
            emptyLabel.getStyleClass().add("muted-text");
            recentActivityList.getChildren().add(emptyLabel);
            return;
        }

        for (DashboardSlipRecord record : summary.getRecentActivity()) {
            // FORMAT BOTH TIMES
            String formattedTimeOut = AppSettingsManager.getInstance().formatTimeString(record.getTimeOut());
            String formattedTimeIn = AppSettingsManager.getInstance().formatTimeString(record.getTimeIn());

            recentActivityList.getChildren().add(createNameLabel(record.getEmployeeName()));
            recentActivityList.getChildren().add(createDetailLabel(
                    formattedTimeOut + " - " + formattedTimeIn + " - " + record.getDuration() + " - " + record.getStatus()
            ));
            recentActivityList.getChildren().add(new Separator());
        }
    }

    private Label createNameLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private Label createDetailLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-text");
        return label;
    }
    @FXML
    private void goToPassSlip(MouseEvent event) {
        try {
            javafx.scene.Scene scene = ((javafx.scene.Node) event.getSource()).getScene();
            MainLayoutController mainController = (MainLayoutController) scene.getRoot().getUserData();

            if (mainController != null) {
                mainController.loadPassSlip(); // Calls your existing method to update the page title too!
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goToEmployeeManagement(MouseEvent event) {
        try {
            javafx.scene.Scene scene = ((javafx.scene.Node) event.getSource()).getScene();
            MainLayoutController mainController = (MainLayoutController) scene.getRoot().getUserData();

            if (mainController != null) {
                mainController.loadEmployeeMgmt();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goToReports(MouseEvent event) {
        try {
            javafx.scene.Scene scene = ((javafx.scene.Node) event.getSource()).getScene();
            MainLayoutController mainController = (MainLayoutController) scene.getRoot().getUserData();

            if (mainController != null) {
                mainController.loadReports();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void goToMonitoring(MouseEvent event) {
        try {
            javafx.scene.Scene scene = ((javafx.scene.Node) event.getSource()).getScene();
            MainLayoutController mainController = (MainLayoutController) scene.getRoot().getUserData();

            if (mainController != null) {
                mainController.loadMonitoring();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}