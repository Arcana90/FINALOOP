package com.example.frontend_emp_pass_slip.controller;

import backend.employee.Employee;
import backend.employee.EmployeeRepository;
import backend.passslip.PassSlipJdbcRepository;
import backend.passslip.PassSlipJdbcRepository.IssuePassSlipResult;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.UnaryOperator;

public class PassSlipIssuanceController {

    @FXML private ComboBox<Employee> employeeComboBox;
    @FXML private ComboBox<String> passTypeComboBox;
    @FXML private TextField departmentField;
    @FXML private TextField positionField;
    @FXML private TextField supervisorField;
    @FXML private TextField destinationField;
    @FXML private TextArea reasonTextArea;

    // Split Time Fields
    @FXML private TextField outHourField;
    @FXML private TextField outMinuteField;
    @FXML private ComboBox<String> timeOutAmPmComboBox;

    @FXML private TextField inHourField;
    @FXML private TextField inMinuteField;
    @FXML private ComboBox<String> timeInAmPmComboBox;

    @FXML private Label statusLabel;

    private final EmployeeRepository employeeRepository = new EmployeeRepository();
    private final PassSlipJdbcRepository passSlipRepository = new PassSlipJdbcRepository();

    @FXML
    private void initialize() {
        setupEmployeeComboBox();
        setupPassTypeComboBox();
        setupTimeFields();

        employeeComboBox.valueProperty().addListener((observable, oldValue, newValue) ->
                fillEmployee(newValue)
        );

        fillEmployee(employeeComboBox.getValue());
    }


    private void setupEmployeeComboBox() {
        List<Employee> activeEmployees = employeeRepository.findAvailableForIssuance()
                .stream()
                .filter(employee -> "Active".equalsIgnoreCase(employee.getStatus()))
                .toList();

        employeeComboBox.setItems(FXCollections.observableArrayList(activeEmployees));

        employeeComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Employee employee) {
                if (employee == null) return "";
                return employee.getFullName() + " (" + employee.getEmployeeId() + ")";
            }
            @Override
            public Employee fromString(String string) { return null; }
        });

        if (activeEmployees.isEmpty()) {
            employeeComboBox.getItems().clear();
            showStatus("No active employees available for pass slip issuance.", true);
        }
    }

    private void setupPassTypeComboBox() {
        passTypeComboBox.setItems(FXCollections.observableArrayList("Official Business", "Personal"));
        passTypeComboBox.getSelectionModel().selectFirst();
    }

    private void setupTimeFields() {
        // Setup AM/PM Dropdowns
        timeOutAmPmComboBox.setItems(FXCollections.observableArrayList("AM", "PM"));
        timeOutAmPmComboBox.getSelectionModel().select("AM");

        timeInAmPmComboBox.setItems(FXCollections.observableArrayList("AM", "PM"));
        timeInAmPmComboBox.getSelectionModel().select("PM");

        // Formatter: Only allow exactly up to 2 numbers
        UnaryOperator<TextFormatter.Change> twoDigitFilter = change -> {
            String text = change.getControlNewText();
            if (text.matches("[0-9]{0,2}")) {
                return change;
            }
            return null;
        };

        outHourField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        outMinuteField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        inHourField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        inMinuteField.setTextFormatter(new TextFormatter<>(twoDigitFilter));

        // Auto-pad single digits with a zero when they click away (e.g., '9' becomes '09')
        addPaddingFocusListener(outHourField);
        addPaddingFocusListener(outMinuteField);
        addPaddingFocusListener(inHourField);
        addPaddingFocusListener(inMinuteField);

        // NEW: Auto-Tab logic. When a box hits 2 characters, jump to the next logical input.
        setupAutoTab(outHourField, outMinuteField);
        setupAutoTab(outMinuteField, timeOutAmPmComboBox);

        setupAutoTab(inHourField, inMinuteField);
        setupAutoTab(inMinuteField, timeInAmPmComboBox);
    }

    // NEW Helper: Automatically moves focus to the next control when 2 digits are typed
    private void setupAutoTab(TextField current, Control next) {
        current.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && newText.length() == 2 && (oldText == null || oldText.length() < 2)) {
                next.requestFocus();
            }
        });
    }

    // Helper to format "9" to "09" automatically
    private void addPaddingFocusListener(TextField field) {
        field.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !field.getText().isEmpty() && field.getText().length() == 1) {
                field.setText("0" + field.getText());
            }
        });
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

        String outHH = outHourField.getText() == null ? "" : outHourField.getText().trim();
        String outMM = outMinuteField.getText() == null ? "" : outMinuteField.getText().trim();
        String inHH = inHourField.getText() == null ? "" : inHourField.getText().trim();
        String inMM = inMinuteField.getText() == null ? "" : inMinuteField.getText().trim();

        // Validations
        if (destination.isBlank()) {
            showStatus("Destination is required.", true); return;
        }
        if (reason.isBlank()) {
            showStatus("Reason / nature of pass is required.", true); return;
        }
        if (outHH.isBlank() || outMM.isBlank() || inHH.isBlank() || inMM.isBlank()) {
            showStatus("Please fill out all time fields completely.", true); return;
        }

        // Validate time ranges and working hours
        try {
            int outHourInt = Integer.parseInt(outHH);
            int outMinInt = Integer.parseInt(outMM);
            int inHourInt = Integer.parseInt(inHH);
            int inMinInt = Integer.parseInt(inMM);

            if (outHourInt < 1 || outHourInt > 12 || inHourInt < 1 || inHourInt > 12) {
                showStatus("Hours must be between 01 and 12.", true); return;
            }
            if (outMinInt < 0 || outMinInt > 59 || inMinInt < 0 || inMinInt > 59) {
                showStatus("Minutes must be between 00 and 59.", true); return;
            }

            // NEW: Working hours validation (8:00 AM to 9:00 PM)
            if (!isWithinWorkingHours(outHourInt, outMinInt, timeOutAmPmComboBox.getValue())) {
                showStatus("Estimated Time Out must be between 08:00 AM and 09:00 PM.", true); return;
            }
            if (!isWithinWorkingHours(inHourInt, inMinInt, timeInAmPmComboBox.getValue())) {
                showStatus("Estimated Time In must be between 08:00 AM and 09:00 PM.", true); return;
            }

        } catch (NumberFormatException e) {
            showStatus("Invalid numbers in time fields.", true); return;
        }

        // Combine into final string format
        String formattedTimeOut = String.format("%02d:%02d %s", Integer.parseInt(outHH), Integer.parseInt(outMM), timeOutAmPmComboBox.getValue());
        String formattedTimeIn = String.format("%02d:%02d %s", Integer.parseInt(inHH), Integer.parseInt(inMM), timeInAmPmComboBox.getValue());

        String finalReason = buildReason(passType, destination, reason)
                + " | Est. Out: " + formattedTimeOut + " | Est. In: " + formattedTimeIn;

        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("Asia/Manila"));
        String currentTimeRequested = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        int issuedByUserId = 1; // Temporary ID

        IssuePassSlipResult result = passSlipRepository.issuePassSlip(
                selectedEmployee.getEmployeeId(),
                finalReason,
                issuedByUserId,
                currentTimeRequested // <--- Pass the real time here
        );
        if (result.isSuccess()) {
            showStatus("Pass slip issued for " + selectedEmployee.getFullName() + ". Slip ID: " + result.getPassSlipId(), false);
            clearForm();
            setupEmployeeComboBox();
        } else {
            showStatus(result.getErrorMessage(), true);
        }
        if (result.isSuccess()) {
            // 1. Show the success Alert
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText("Pass Slip Issued Successfully");
            alert.setContentText("The pass slip for " + selectedEmployee.getFullName()
                    + " has been recorded. (Slip ID: " + result.getPassSlipId() + ")");
            alert.showAndWait();

            // 2. Clear the form after the user closes the alert
            clearForm();
            setupEmployeeComboBox();
        } else {
            showStatus(result.getErrorMessage(), true);
        }
    }

    // NEW Helper: Checks if the time is between 8:00 AM and 9:00 PM
    private boolean isWithinWorkingHours(int hour12, int minutes, String amPm) {
        int hour24 = hour12;

        // Convert to 24-hour format for easier math
        if ("AM".equals(amPm) && hour12 == 12) {
            hour24 = 0; // Midnight
        } else if ("PM".equals(amPm) && hour12 < 12) {
            hour24 += 12; // 1 PM becomes 13, etc.
        }

        // Before 8:00 AM
        if (hour24 < 8) return false;

        // After 9:00 PM (21:00)
        if (hour24 > 21) return false;

        // Exactly 9:01 PM or later
        if (hour24 == 21 && minutes > 0) return false;

        return true;
    }

    @FXML
    private void clearForm() {
        employeeComboBox.getSelectionModel().clearSelection();
        departmentField.clear();
        positionField.clear();
        supervisorField.clear();

        passTypeComboBox.getSelectionModel().selectFirst();
        destinationField.clear();
        reasonTextArea.clear();

        outHourField.clear();
        outMinuteField.clear();
        timeOutAmPmComboBox.getSelectionModel().select("AM");

        inHourField.clear();
        inMinuteField.clear();
        timeInAmPmComboBox.getSelectionModel().select("PM");

        statusLabel.setText("");
    }

    private String buildReason(String passType, String destination, String reason) {
        return "Type: " + passType + " | Destination: " + destination + " | Reason: " + reason;
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
}