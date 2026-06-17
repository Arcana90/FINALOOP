package com.example.frontend_emp_pass_slip.controller;

import backend.employee.Employee;
import backend.employee.EmployeeRepository;
import backend.passslip.PassSlipJdbcRepository;
import backend.passslip.PassSlipJdbcRepository.IssuePassSlipResult;
import backend.app.AppSettingsManager; // Ensures global formatting is applied
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.List;

public class PassSlipIssuanceController {

    @FXML private ComboBox<Employee> employeeComboBox;
    @FXML private ComboBox<String> passTypeComboBox;
    @FXML private TextField departmentField;
    @FXML private TextField positionField;
    @FXML private TextField supervisorField;
    @FXML private TextField destinationField;
    @FXML private TextArea reasonTextArea;
    @FXML private Label timeOutLabel; // Correctly mapped to Label
    @FXML private Label statusLabel;

    private final EmployeeRepository employeeRepository = new EmployeeRepository();
    private final PassSlipJdbcRepository passSlipRepository = new PassSlipJdbcRepository();

    @FXML
    private void initialize() {
        setupEmployeeComboBox();
        setupPassTypeComboBox();

        employeeComboBox.valueProperty().addListener((observable, oldValue, newValue) ->
                fillEmployee(newValue)
        );

        fillEmployee(employeeComboBox.getValue());

        updateTimeOutLabel(); // Automatically updates when screen loads
    }

    private void setupEmployeeComboBox() {
        // We now call the new method that checks both tables!
        List<Employee> activeEmployees = employeeRepository.findAvailableForIssuance()
                .stream()
                .filter(employee -> "Active".equalsIgnoreCase(employee.getStatus()))
                .toList();

        employeeComboBox.setItems(FXCollections.observableArrayList(activeEmployees));

        employeeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Employee employee) {
                if (employee == null) {
                    return "";
                }
                return employee.getFullName() + " (" + employee.getEmployeeId() + ")";
            }

            @Override
            public Employee fromString(String string) {
                return null;
            }
        });

        if (!activeEmployees.isEmpty()) {
            employeeComboBox.getSelectionModel().selectFirst();
        } else {
            employeeComboBox.getItems().clear(); // Ensure dropdown is empty
            showStatus("No active employees available for pass slip issuance.", true);
        }
    }

    private void setupPassTypeComboBox() {
        passTypeComboBox.setItems(FXCollections.observableArrayList(
                "Official Business",
                "Personal"
        ));

        passTypeComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void issuePassSlip() {
        Employee selectedEmployee = employeeComboBox.getValue();

        if (selectedEmployee == null) {
            showStatus("Please select an employee.", true);
            return;
        }

        String passType = passTypeComboBox.getValue();
        String destination = destinationField.getText() == null ? "" : destinationField.getText().trim();
        String reason = reasonTextArea.getText() == null ? "" : reasonTextArea.getText().trim();

        if (destination.isBlank()) {
            showStatus("Destination is required.", true);
            return;
        }

        if (reason.isBlank()) {
            showStatus("Reason / nature of pass is required.", true);
            return;
        }

        String finalReason = buildReason(passType, destination, reason);

        // Temporary until login user ID is connected.
        int issuedByUserId = 1;

        IssuePassSlipResult result = passSlipRepository.issuePassSlip(
                selectedEmployee.getEmployeeId(),
                finalReason,
                issuedByUserId
        );

        if (result.isSuccess()) {
            // Apply global 12h/24h setting to the success message
            String formattedTimeOut = AppSettingsManager.getInstance().formatTime(LocalTime.now());

            showStatus(
                    "Pass slip issued for " + selectedEmployee.getFullName()
                            + ". Slip ID: " + result.getPassSlipId()
                            + ". Time-Out: " + formattedTimeOut,
                    false
            );

            destinationField.clear();
            reasonTextArea.clear();
            updateTimeOutLabel();

            // 👇 THIS IS THE MISSING LINE 👇
            // It forces the dropdown to instantly reload and hide the employee who just left
            setupEmployeeComboBox();

        } else {
            showStatus(result.getErrorMessage(), true);
        }
    }
    @FXML
    private void clearForm() {
        employeeComboBox.getSelectionModel().clearSelection();
        departmentField.clear();
        positionField.clear();
        supervisorField.clear();

        if (!passTypeComboBox.getItems().isEmpty()) {
            passTypeComboBox.getSelectionModel().selectFirst();
        }

        destinationField.clear();
        reasonTextArea.clear();
        statusLabel.setText("");

    }


    private String buildReason(String passType, String destination, String reason) {
        return "Type: " + passType
                + " | Destination: " + destination
                + " | Reason: " + reason;
    }

    private void fillEmployee(Employee employee) {
        if (employee == null) {
            departmentField.clear();
            positionField.clear();
            supervisorField.clear();
            return;
        }

        departmentField.setText(employee.getDepartment());
        positionField.setText(employee.getPosition());
        supervisorField.setText(employee.getSupervisorName());
    }

    private void showStatus(String message, boolean error) {
        statusLabel.setText(message == null ? "" : message);
        statusLabel.setStyle(error ? "-fx-text-fill: #b00020;" : "-fx-text-fill: #0b6b2b;");
    }

    // Fixed dynamic time display to rely on the centralized Manager
    private void updateTimeOutLabel() {
        LocalTime currentTime = LocalTime.now();
        String formattedTime = AppSettingsManager.getInstance().formatTime(currentTime);
        timeOutLabel.setText("Time-Out will be automatically recorded as: " + formattedTime);
    }
}