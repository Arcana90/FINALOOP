package com.example.frontend_emp_pass_slip.controller;

import backend.employee.Employee;
import backend.employee.EmployeeRepository;
import backend.passslip.PassSlipJdbcRepository;
import backend.passslip.PassSlipJdbcRepository.IssuePassSlipResult;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
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
            public Employee fromString(String string) {
                return null;
            }
        });

        if (activeEmployees.isEmpty()) {
            employeeComboBox.getItems().clear();
            showStatus("No active employees available for pass slip issuance.", true);
        }
    }

    private void setupPassTypeComboBox() {
        passTypeComboBox.setItems(FXCollections.observableArrayList("Official Business", "Personal", "Emergency"));
        passTypeComboBox.getSelectionModel().selectFirst();

        passTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isEmergency = "Emergency".equals(newVal);

            outHourField.setDisable(isEmergency);
            outMinuteField.setDisable(isEmergency);
            timeOutAmPmComboBox.setDisable(isEmergency);

            inHourField.setDisable(isEmergency);
            inMinuteField.setDisable(isEmergency);
            timeInAmPmComboBox.setDisable(isEmergency);

            if (isEmergency) {
                outHourField.clear();
                outMinuteField.clear();
                inHourField.clear();
                inMinuteField.clear();
            }
        });
    }

    private void setupTimeFields() {
        timeOutAmPmComboBox.setItems(FXCollections.observableArrayList("AM", "PM"));
        timeOutAmPmComboBox.getSelectionModel().select("AM");

        timeInAmPmComboBox.setItems(FXCollections.observableArrayList("AM", "PM"));
        timeInAmPmComboBox.getSelectionModel().select("PM");

        UnaryOperator<TextFormatter.Change> twoDigitFilter = change -> {
            String text = change.getControlNewText();
            if (text.matches("[0-9]{0,2}")) return change;
            return null;
        };

        outHourField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        outMinuteField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        inHourField.setTextFormatter(new TextFormatter<>(twoDigitFilter));
        inMinuteField.setTextFormatter(new TextFormatter<>(twoDigitFilter));

        addPaddingFocusListener(outHourField);
        addPaddingFocusListener(outMinuteField);
        addPaddingFocusListener(inHourField);
        addPaddingFocusListener(inMinuteField);

        // MODIFIED: Auto-tab now flows directly from Out Minutes to In Hours
        setupAutoTab(outHourField, outMinuteField);
        setupAutoTab(outMinuteField, inHourField);
        setupAutoTab(inHourField, inMinuteField);
        // Note: We removed the auto-tab from inMinuteField so the cursor stays there when done.

        setupSmartAmPm(outHourField, timeOutAmPmComboBox);
        setupSmartAmPm(inHourField, timeInAmPmComboBox);
    }

    private void setupSmartAmPm(TextField hourField, ComboBox<String> amPmBox) {
        hourField.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && !newText.trim().isEmpty()) {
                try {
                    int hour = Integer.parseInt(newText.trim());
                    // 8, 9, 10, 11 -> AM
                    if (hour >= 8 && hour <= 11) {
                        amPmBox.getSelectionModel().select("AM");
                    }
                    // 12, 1, 2, 3, 4, 5, 6, 7 -> PM
                    else if (hour == 12 || (hour >= 1 && hour <= 7)) {
                        amPmBox.getSelectionModel().select("PM");
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        });
    }

    private void setupAutoTab(TextField current, Control next) {
        current.textProperty().addListener((obs, oldText, newText) -> {
            if (newText != null && newText.length() == 2 && (oldText == null || oldText.length() < 2)) {
                next.requestFocus();
            }
        });
    }

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
        boolean isEmergency = "Emergency".equals(passType);
        String destination = destinationField.getText() == null ? "" : destinationField.getText().trim();
        String reason = reasonTextArea.getText() == null ? "" : reasonTextArea.getText().trim();

        String outHH = outHourField.getText() == null ? "" : outHourField.getText().trim();
        String outMM = outMinuteField.getText() == null ? "" : outMinuteField.getText().trim();
        String inHH = inHourField.getText() == null ? "" : inHourField.getText().trim();
        String inMM = inMinuteField.getText() == null ? "" : inMinuteField.getText().trim();

        if (destination.isBlank()) {
            showStatus("Destination is required.", true);
            return;
        }
        if (reason.isBlank()) {
            showStatus("Reason / nature of pass is required.", true);
            return;
        }

        String dbExpectedTimeOut = null;
        String dbExpectedTimeIn = null;
        String formattedTimeOut = "N/A";
        String formattedTimeIn = "N/A";

        if (!isEmergency) {
            if (outHH.isBlank() || outMM.isBlank() || inHH.isBlank() || inMM.isBlank()) {
                showStatus("Please fill out all time fields completely.", true);
                return;
            }

            try {
                int outHourInt = Integer.parseInt(outHH);
                int outMinInt = Integer.parseInt(outMM);
                int inHourInt = Integer.parseInt(inHH);
                int inMinInt = Integer.parseInt(inMM);

                if (outHourInt < 1 || outHourInt > 12 || inHourInt < 1 || inHourInt > 12) {
                    showStatus("Hours must be between 01 and 12.", true);
                    return;
                }
                if (outMinInt < 0 || outMinInt > 59 || inMinInt < 0 || inMinInt > 59) {
                    showStatus("Minutes must be between 00 and 59.", true);
                    return;
                }

                if (!isWithinWorkingHours(outHourInt, outMinInt, timeOutAmPmComboBox.getValue())) {
                    showStatus("Estimated Time Out must be between 08:00 AM and 09:00 PM.", true);
                    return;
                }
                if (!isWithinWorkingHours(inHourInt, inMinInt, timeInAmPmComboBox.getValue())) {
                    showStatus("Estimated Time In must be between 08:00 AM and 09:00 PM.", true);
                    return;
                }

                formattedTimeOut = String.format("%02d:%02d %s", outHourInt, outMinInt, timeOutAmPmComboBox.getValue());
                formattedTimeIn = String.format("%02d:%02d %s", inHourInt, inMinInt, timeInAmPmComboBox.getValue());

                int out24Hour = outHourInt;
                if ("AM".equals(timeOutAmPmComboBox.getValue()) && outHourInt == 12) out24Hour = 0;
                else if ("PM".equals(timeOutAmPmComboBox.getValue()) && outHourInt < 12) out24Hour += 12;

                int in24Hour = inHourInt;
                if ("AM".equals(timeInAmPmComboBox.getValue()) && inHourInt == 12) in24Hour = 0;
                else if ("PM".equals(timeInAmPmComboBox.getValue()) && inHourInt < 12) in24Hour += 12;

                LocalTime currentTime = LocalTime.now();
                LocalTime estimatedOutTime = LocalTime.of(out24Hour, outMinInt);
                LocalTime estimatedInTime = LocalTime.of(in24Hour, inMinInt);

                if (estimatedOutTime.isBefore(currentTime)) {
                    String formattedCurrentTime = currentTime.format(DateTimeFormatter.ofPattern("hh:mm a"));
                    showStatus("Estimated Time Out cannot be earlier than the current time (" + formattedCurrentTime + ").", true);
                    return;
                }

                if (estimatedInTime.isBefore(estimatedOutTime) || estimatedInTime.equals(estimatedOutTime)) {
                    showStatus("Estimated Time In must be later than Estimated Time Out.", true);
                    return;
                }

                dbExpectedTimeOut = String.format("%02d:%02d", out24Hour, outMinInt);
                dbExpectedTimeIn = String.format("%02d:%02d", in24Hour, inMinInt);

            } catch (NumberFormatException e) {
                showStatus("Invalid numbers in time fields.", true);
                return;
            }
        }

        String finalReason = buildReason(passType, destination, reason)
                + " | Est. Out: " + formattedTimeOut + " | Est. In: " + formattedTimeIn;

        int issuedByUserId = 1;

        IssuePassSlipResult result = passSlipRepository.issuePassSlip(
                selectedEmployee.getEmployeeId(),
                finalReason,
                issuedByUserId,
                dbExpectedTimeOut,
                dbExpectedTimeIn
        );

        if (result.isSuccess()) {
            showStatus("Pass slip issued for " + selectedEmployee.getFullName() + ". Slip ID: " + result.getPassSlipId(), false);

            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Success");
            successAlert.setHeaderText("Pass Slip Issued Successfully");
            successAlert.setContentText("The pass slip for " + selectedEmployee.getFullName()
                    + " has been recorded. (Slip ID: " + result.getPassSlipId() + ")");
            successAlert.showAndWait();

            resetFormFields();
            setupEmployeeComboBox();
        } else {
            showStatus(result.getErrorMessage(), true);
        }
    }

    private boolean isWithinWorkingHours(int hour12, int minutes, String amPm) {
        int hour24 = hour12;
        if ("AM".equals(amPm) && hour12 == 12) hour24 = 0;
        else if ("PM".equals(amPm) && hour12 < 12) hour24 += 12;

        if (hour24 < 8) return false;
        if (hour24 > 21) return false;
        if (hour24 == 21 && minutes > 0) return false;
        return true;
    }

    @FXML
    private void clearForm() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Action");
        alert.setHeaderText("Cancel Pass Slip Registration");
        alert.setContentText("Are you sure you want to cancel this employee's pass slip request?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            resetFormFields();
        }
    }

    private void resetFormFields() {
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