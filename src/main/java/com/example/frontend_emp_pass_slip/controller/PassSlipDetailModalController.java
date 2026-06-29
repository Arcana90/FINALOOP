package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.PassSlipMonitoringRecord;
import backend.passslip.PassSlipJdbcRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
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
    @FXML private Label modalTypeLabel;
    @FXML private Label modalDestinationLabel;
    @FXML private Label modalReasonLabel;
    @FXML private Label modalEstOutLabel;
    @FXML private Label modalEstInLabel;
    @FXML private HBox actionButtonContainer;

    private PassSlipMonitoringRecord referenceRecord;
    private MonitoringDirectorController dashboardController;
    private Stage activeStage;

    private final MonitoringJdbcRepository dbService = new MonitoringJdbcRepository();
    private final PassSlipJdbcRepository passSlipRepository = new PassSlipJdbcRepository();
    private boolean isEmergencyPass = false;

    public void setPassSlipData(PassSlipMonitoringRecord record, MonitoringDirectorController controller, Stage stage) {
        this.referenceRecord = record;
        this.dashboardController = controller;
        this.activeStage = stage;

        String rawReason = record.getReasonForLeaving();
        String passType = "Standard";
        String extractedDestination = "N/A"; // Variable to hold parsed destination
        String extractedReason = "N/A";      // Variable to hold parsed reason
        this.isEmergencyPass = false;

        if (rawReason != null && rawReason.contains("|")) {
            String[] parts = rawReason.split("\\|");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("Type:")) {
                    passType = part.substring("Type:".length()).trim();
                    this.isEmergencyPass = passType.equalsIgnoreCase("Emergency");
                }
                // 🟢 ADDED: Parse Destination from the rawReason string
                else if (part.startsWith("Destination:")) {
                    extractedDestination = part.substring("Destination:".length()).trim();
                }
                // 🟢 ADDED: Parse Reason from the rawReason string
                else if (part.startsWith("Reason:")) {
                    extractedReason = part.substring("Reason:".length()).trim();
                }
            }
        }

        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());
        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getFullName());
        modalDeptLabel.setText(record.getDepartment());
        modalTypeLabel.setText(passType);

        String formattedRequestedTime = AppSettingsManager.getInstance().formatTimeString(record.getTimeRequested());
        modalTimeRequestedLabel.setText("Requested at: " + formattedRequestedTime);

        // 🟢 FIXED: Use the extracted values instead of record.getDestination()/getReason()
        modalDestinationLabel.setText(extractedDestination);
        modalReasonLabel.setText(extractedReason);

        String estOut = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeOut());
        String estIn = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeIn());

        modalEstOutLabel.setText("Est. Out: " + estOut);
        modalEstInLabel.setText("Est. In: " + estIn);

        // ... (rest of the method remains the same)


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
        // Direct execution - no confirmation popup
        boolean success = passSlipRepository.approvePassSlip(referenceRecord.getPassSlipId(), this.isEmergencyPass);
        if (success) {
            dashboardController.refreshDashboardData();
            activeStage.close();
        } else {
            new Alert(Alert.AlertType.ERROR, "Failed to approve pass slip.").show();
        }
    }

    @FXML
    private void handleReject() {
        // Direct execution
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