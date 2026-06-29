package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.PassSlipMonitoringRecord;
import backend.passslip.PassSlipJdbcRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
    @FXML private Label modalTypeLabel; // 🟢 ADDED
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
        String passType = "Standard"; // Default
        this.isEmergencyPass = false;

        if (rawReason != null && rawReason.contains("|")) {
            String[] parts = rawReason.split("\\|");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("Type:")) {
                    passType = part.replace("Type:", "").trim();
                    this.isEmergencyPass = passType.equalsIgnoreCase("Emergency");
                }
            }
        }

        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());
        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getFullName());
        modalDeptLabel.setText(record.getDepartment());
        modalTypeLabel.setText(passType); // 🟢 Updated UI

        String formattedRequestedTime = AppSettingsManager.getInstance().formatTimeString(record.getTimeRequested());
        modalTimeRequestedLabel.setText("Requested at: " + formattedRequestedTime);

        modalDestinationLabel.setText(record.getDestination());
        modalReasonLabel.setText(record.getReason());

        String estOut = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeOut());
        String estIn = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeIn());

        modalEstOutLabel.setText("Est. Out: " + estOut);
        modalEstInLabel.setText("Est. In: " + estIn);

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
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm APPROVE");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to APPROVE this pass slip?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            boolean success = passSlipRepository.approvePassSlip(referenceRecord.getPassSlipId(), this.isEmergencyPass);
            if (success) {
                dashboardController.refreshDashboardData();
                activeStage.close();
            } else {
                new Alert(Alert.AlertType.ERROR, "Failed to approve pass slip.").show();
            }
        }
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