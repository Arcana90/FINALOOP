package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.PassSlipJdbcRepository;
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

        // Populate base
        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());
        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getFullName());
        modalDeptLabel.setText(record.getDepartment());

        // 🟢 FIXED: Apply dynamic time formatting to the requested time
        String formattedRequestedTime = AppSettingsManager.getInstance().formatTimeString(record.getTimeRequested());
        modalTimeRequestedLabel.setText("Requested at: " + formattedRequestedTime);

        // Call getReasonForLeaving() to get the FULL string with all the pipes
        String rawData = record.getReasonForLeaving();

        // Set defaults
        String dest = "N/A", reas = "N/A", estOut = "N/A", estIn = "N/A";

        if (rawData != null && rawData.contains("|")) {
            String[] parts = rawData.split("\\|");
            for (String part : parts) {
                String p = part.trim();
                // We use replaceFirst here just in case the user typed a colon in their reason
                if (p.startsWith("Destination:")) {
                    dest = p.replaceFirst("Destination:", "").trim();
                }
                if (p.startsWith("Reason:")) {
                    reas = p.replaceFirst("Reason:", "").trim();
                }
                if (p.startsWith("Est. Out:")) {
                    String rawOut = p.replaceFirst("Est. Out:", "").trim();
                    // 🟢 FIXED: Apply dynamic time formatting to Est. Out
                    estOut = AppSettingsManager.getInstance().formatTimeString(rawOut);
                }
                if (p.startsWith("Est. In:")) {
                    String rawIn = p.replaceFirst("Est. In:", "").trim();
                    // 🟢 FIXED: Apply dynamic time formatting to Est. In
                    estIn = AppSettingsManager.getInstance().formatTimeString(rawIn);
                }
            }
        }

        modalDestinationLabel.setText(dest);
        modalReasonLabel.setText(reas);
        modalEstOutLabel.setText("Est. Out: " + estOut);
        modalEstInLabel.setText("Est. In: " + estIn);

        // BUTTON VISIBILITY LOGIC
        // Ensure we handle "For Approval" exactly as it appears in your DB
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
        // Strict Title Case to match Supabase enum
        boolean processingSuccess = dbService.updateSlipStatus(referenceRecord.getPassSlipId(), "Approved");
        finalizeModalTransaction(processingSuccess);
    }

    @FXML
    private void handleReject() {
        // Matches your database enum exactly
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