package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.MonitoringJdbcRepository;
import backend.passslip.PassSlipMonitoringRecord;
import backend.passslip.PassSlipJdbcRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class AdminPassSlipDetailModalController {

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

    private PassSlipMonitoringRecord record;
    private final MonitoringJdbcRepository repository = new MonitoringJdbcRepository();
    private final PassSlipJdbcRepository passSlipRepository = new PassSlipJdbcRepository();

    private boolean isEmergencyPass = false;
    private String passType = "Standard";

    public void setRecord(PassSlipMonitoringRecord record) {
        this.record = record;
        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());

        String formattedRequested = AppSettingsManager.getInstance().formatTimeString(record.getTimeRequested());
        modalTimeRequestedLabel.setText("Requested at: " + formattedRequested);

        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getName());
        modalDeptLabel.setText(record.getDepartment());

        String formattedOut = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeOut());
        String formattedIn = AppSettingsManager.getInstance().formatTimeString(record.getExpectedTimeIn());

        modalEstOutLabel.setText("Est. Out: " + formattedOut);
        modalEstInLabel.setText("Est. In: " + formattedIn);

        String rawReason = record.getReasonForLeaving();
        if (rawReason == null || rawReason.isBlank()) {
            rawReason = record.getReason();
        }

        String destination = "N/A";
        String nature = "N/A";
        passType = "Standard";
        isEmergencyPass = false;

        if (rawReason != null && rawReason.contains("|")) {
            String[] parts = rawReason.split("\\|");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("Destination:")) {
                    destination = part.replace("Destination:", "").trim();
                } else if (part.startsWith("Reason:")) {
                    nature = part.replace("Reason:", "").trim();
                } else if (part.startsWith("Type:")) {
                    passType = part.replace("Type:", "").trim();
                    isEmergencyPass = passType.equalsIgnoreCase("Emergency");
                }
            }
        } else {
            nature = rawReason != null ? rawReason : "N/A";
        }

        modalTypeLabel.setText(passType);
        modalDestinationLabel.setText(destination);
        modalReasonLabel.setText(nature);

        setupStatusBadge(record.getStatus());
    }

    private void setupStatusBadge(String status) {
        if (status == null) status = "UNKNOWN";
        boolean isPending = status.equalsIgnoreCase("For Approval");

        actionButtonContainer.setVisible(isPending);
        actionButtonContainer.setManaged(isPending);

        String safeStatus = status.toUpperCase();
        modalBadgeLabel.setText(isPending ? "PENDING REVIEW" : safeStatus);

        String baseStyle = "-fx-padding: 4 10; -fx-background-radius: 20; -fx-font-weight: bold; -fx-font-size: 11px; ";

        if (isPending) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;");
        } else if (safeStatus.equals("APPROVED") || safeStatus.equals("RETURNED") || safeStatus.equals("EXCUSED")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;");
        } else if (safeStatus.equals("REJECTED") || safeStatus.equals("CANCELLED") || safeStatus.equals("AWOL")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626;");
        } else if (safeStatus.equals("OUT")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7;");
        } else {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563;");
        }
    }

    @FXML
    private void handleApprove() {
        // Direct execution - no popup
        boolean success = passSlipRepository.approvePassSlip(record.getPassSlipId(), isEmergencyPass);
        if (success) {
            ((Stage) modalBadgeLabel.getScene().getWindow()).close();
        } else {
            new Alert(Alert.AlertType.ERROR, "Failed to approve pass slip.").show();
        }
    }

    @FXML
    private void handleReject() {
        processAction("Rejected");
    }

    @FXML
    private void handleCancel() {
        processAction("Cancelled");
    }

    private void processAction(String status) {
        // Direct execution - no popup
        boolean success = repository.updateSlipStatus(record.getPassSlipId(), status);
        if (success) {
            ((Stage) modalBadgeLabel.getScene().getWindow()).close();
        } else {
            new Alert(Alert.AlertType.ERROR, "Failed to update status.").show();
        }
    }
}