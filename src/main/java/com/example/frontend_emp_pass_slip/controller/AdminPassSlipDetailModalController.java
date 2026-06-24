package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.MonitoringJdbcRepository;
import backend.passslip.PassSlipMonitoringRecord;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
    @FXML private Label modalDestinationLabel;
    @FXML private Label modalReasonLabel;
    @FXML private Label modalEstOutLabel;
    @FXML private Label modalEstInLabel;
    @FXML private HBox actionButtonContainer;

    private PassSlipMonitoringRecord record;
    private final MonitoringJdbcRepository repository = new MonitoringJdbcRepository();

    public void setRecord(PassSlipMonitoringRecord record) {
        this.record = record;
        modalSlipNoLabel.setText("Pass Slip No: " + record.getSlipNo());
        modalTimeRequestedLabel.setText("Requested at: " + record.getTimeRequested());
        modalEmpIdLabel.setText("ID: " + record.getEmployeeId());
        modalNameLabel.setText(record.getName());
        modalDeptLabel.setText(record.getDepartment());
        modalEstOutLabel.setText("Est. Out: " + record.getExpectedTimeOut());
        modalEstInLabel.setText("Est. In: " + record.getExpectedTimeIn());

        // Parse Reason and Destination safely
        String rawReason = record.getReasonForLeaving();

        if (rawReason == null || rawReason.isBlank()) {
            rawReason = record.getReason();
        }

        String destination = "N/A";
        String nature = "N/A";

        if (rawReason != null && rawReason.contains("|")) {
            String[] parts = rawReason.split("\\|");

            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("Destination:")) {
                    destination = part.replace("Destination:", "").trim();
                } else if (part.startsWith("Reason:")) {
                    nature = part.replace("Reason:", "").trim();
                }
            }
        } else {
            nature = rawReason != null ? rawReason : "N/A";
        }

        modalDestinationLabel.setText(destination);
        modalReasonLabel.setText(nature);

        // 🟢 CRITICAL FIX: You must call the badge setup here!
        setupStatusBadge(record.getStatus());
    }

    private void setupStatusBadge(String status) {
        if (status == null) status = "UNKNOWN";

        boolean isPending = status.equalsIgnoreCase("For Approval");

        // Toggle button visibility
        actionButtonContainer.setVisible(isPending);
        actionButtonContainer.setManaged(isPending);

        String safeStatus = status.toUpperCase();
        modalBadgeLabel.setText(isPending ? "PENDING REVIEW" : safeStatus);

        // Apply matching colors
        String baseStyle = "-fx-padding: 4 10; -fx-background-radius: 20; -fx-font-weight: bold; -fx-font-size: 11px; ";

        if (isPending) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #fef3c7; -fx-text-fill: #d97706;"); // Yellow
        } else if (safeStatus.equals("APPROVED") || safeStatus.equals("RETURNED")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #dcfce7; -fx-text-fill: #16a34a;"); // Green
        } else if (safeStatus.equals("REJECTED") || safeStatus.equals("CANCELLED") || safeStatus.equals("AWOL")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #fee2e2; -fx-text-fill: #dc2626;"); // Red
        } else if (safeStatus.equals("OUT")) {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7;"); // Blue
        } else {
            modalBadgeLabel.setStyle(baseStyle + "-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563;"); // Gray
        }
    }

    @FXML
    private void handleApprove() {
        processAction("Approved", "APPROVE");
    }

    @FXML
    private void handleReject() {
        processAction("Rejected", "REJECT");
    }

    @FXML
    private void handleCancel() {
        processAction("Cancelled", "CANCEL");
    }

    private void processAction(String status, String actionLabel) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm " + actionLabel);
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to " + actionLabel + " this pass slip?");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            boolean success = repository.updateSlipStatus(record.getPassSlipId(), status);
            if (success) {
                ((Stage) modalBadgeLabel.getScene().getWindow()).close();
            } else {
                new Alert(Alert.AlertType.ERROR, "Failed to update status.").show();
            }
        }
    }
}