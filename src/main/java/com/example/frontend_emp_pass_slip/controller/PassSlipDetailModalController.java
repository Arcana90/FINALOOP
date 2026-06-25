package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.PassSlipMonitoringRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import backend.passslip.MonitoringJdbcRepository;

public class PassSlipDetailModalController {

    @FXML private Label modalSlipNoLabel;
    @FXML private Label modalTimeRequestedLabel;
    @FXML private Label modalBadgeLabel;
    @FXML private Label modalEmpIdLabel;
    @FXML private Label modalNameLabel;
    @FXML private Label modalDeptLabel;
    @FXML private Label modalDestinationLabel;
    @FXML private Label modalReasonLabel;
    @FXML private Label modalEstOutLabel;
    @FXML private Label modalEstInLabel;
    @FXML private HBox actionButtonContainer;

    private PassSlipMonitoringRecord referenceRecord;
    private MonitoringDirectorController dashboardController;
    private Stage activeStage;
    private final MonitoringJdbcRepository dbService = new MonitoringJdbcRepository();

    public void setPassSlipData(PassSlipMonitoringRecord record, MonitoringDirectorController controller, Stage stage) {
        this.referenceRecord = record;
        this.dashboardController = controller;
        this.activeStage = stage;

        // Base Data Mapping
        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());
        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getFullName());
        modalDeptLabel.setText(record.getDepartment());

        // 🌟 Apply dynamic time formatting safely
        String formattedRequestedTime = AppSettingsManager.getInstance().formatTimeString(record.getTimeRequested());
        modalTimeRequestedLabel.setText("Requested at: " + formattedRequestedTime);

        // 🌟 FIXED: Extract clean fields directly from the modern record object
        modalDestinationLabel.setText(record.getDestination());
        modalReasonLabel.setText(record.getReason());

        // 🌟 FIXED: Format output using the updated bulletproof string manager
        String estOut = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeOut());
        String estIn = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeIn());

        modalEstOutLabel.setText("Est. Out: " + estOut);
        modalEstInLabel.setText("Est. In: " + estIn);

        // Action Options Rendering Check
        String status = record.getStatus();
        if (status != null && status.equalsIgnoreCase("For Approval")) {
            actionButtonContainer.setVisible(true);
            actionButtonContainer.setManaged(true);
            modalBadgeLabel.setText("PENDING REVIEW");
            modalBadgeLabel.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #d97706;");
        } else {
            actionButtonContainer.setVisible(false);
            actionButtonContainer.setManaged(false);
            modalBadgeLabel.setText(status != null ? status.toUpperCase() : "UNKNOWN");
            modalBadgeLabel.setStyle("-fx-background-color: #e5e7eb; -fx-text-fill: #374151;");
        }
    }

    @FXML
    private void handleApprove() {
        boolean processingSuccess = dbService.updateSlipStatus(referenceRecord.getPassSlipId(), "Approved");
        finalizeModalTransaction(processingSuccess);
    }

    @FXML
    private void handleReject() {
        boolean processingSuccess = dbService.updateSlipStatus(referenceRecord.getPassSlipId(), "Rejected");
        finalizeModalTransaction(processingSuccess);
    }

    private void finalizeModalTransaction(boolean successfulUpdate) {
        if (successfulUpdate) {
            dashboardController.refreshDashboardData();
            activeStage.close();
        } else {
            System.err.println("Database sync transaction failed updating pass slip status.");
        }
    }
}