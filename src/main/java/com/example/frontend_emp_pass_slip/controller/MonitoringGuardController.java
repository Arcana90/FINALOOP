package com.example.frontend_emp_pass_slip.controller;

import backend.app.AppSettingsManager;
import backend.passslip.MonitoringJdbcRepository;
import backend.passslip.PassSlipMonitoringRecord;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.stream.Collectors;

public class MonitoringGuardController {

    @FXML private Label approvedSlipsLabel;
    @FXML private Label currentlyOutLabel;
    @FXML private Label returnedLabel;
    @FXML private Label countLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;

    @FXML private TableView<PassSlipMonitoringRecord> monitoringTable;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> slipNoColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> employeeIdColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> nameColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> dateColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> timeOutColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> timeInColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, Void> actionColumn;

    private final MonitoringJdbcRepository repository = new MonitoringJdbcRepository();
    private ObservableList<PassSlipMonitoringRecord> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupColumns();
        setupFilters();
        loadData();
    }

    private void setupColumns() {
        slipNoColumn.setCellValueFactory(new PropertyValueFactory<>("slipNo"));
        employeeIdColumn.setCellValueFactory(new PropertyValueFactory<>("employeeId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : AppSettingsManager.getInstance().formatDateString(item));
            }
        });

        timeOutColumn.setCellValueFactory(new PropertyValueFactory<>("timeOut"));
        timeOutColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : AppSettingsManager.getInstance().formatTimeString(item));
            }
        });

        timeInColumn.setCellValueFactory(new PropertyValueFactory<>("timeIn"));
        timeInColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : AppSettingsManager.getInstance().formatTimeString(item));
            }
        });

        actionColumn.setCellFactory(param -> new TableCell<>() {
            private final Button btnOut = new Button("Log Time Out");
            private final Button btnIn = new Button("Log Time In");
            private final HBox pane = new HBox(btnOut, btnIn);

            {
                pane.setStyle("-fx-alignment: CENTER; -fx-spacing: 10;");
                btnOut.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
                btnIn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

                btnOut.setOnAction(event -> {
                    PassSlipMonitoringRecord record = getTableRow().getItem();
                    if (record != null) {
                        showConfirmationDialog(record, "Time Out", () -> {
                            if (repository.markAsOut(record.getPassSlipId())) loadData();
                        });
                    }
                });

                btnIn.setOnAction(event -> {
                    PassSlipMonitoringRecord record = getTableRow().getItem();
                    if (record != null) {
                        showConfirmationDialog(record, "Time In", () -> {
                            if (repository.markAsReturned(record.getPassSlipId())) loadData();
                        });
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    PassSlipMonitoringRecord record = getTableRow().getItem();
                    String status = record.getStatus();

                    // Check if the employee actually has a time in value
                    boolean hasTimeIn = record.getTimeIn() != null
                            && !record.getTimeIn().trim().isEmpty()
                            && !record.getTimeIn().equalsIgnoreCase("N/A")
                            && !record.getTimeIn().equals("-");

                    boolean isApproved = "Approved".equalsIgnoreCase(status);

                    // 🟢 FIX: Strictly require the status to be 'Out' for the time-in button to show.
                    // 'Excused' is a closed status and no longer requires manual guard intervention.
                    boolean isOut = "Out".equalsIgnoreCase(status);

                    if (isApproved) {
                        btnOut.setVisible(true); btnOut.setManaged(true);
                        btnIn.setVisible(false); btnIn.setManaged(false);
                        setGraphic(pane);
                    }
                    // 🟢 FIX: Only show Time In if they are strictly 'Out' and haven't been logged in yet
                    else if (isOut && !hasTimeIn) {
                        btnOut.setVisible(false); btnOut.setManaged(false);
                        btnIn.setVisible(true); btnIn.setManaged(true);
                        setGraphic(pane);
                    } else {
                        // For Returned, Cancelled, AWOL, and Excused, show nothing
                        setGraphic(null);
                    }
                }
            }
        });
    }

    private void showConfirmationDialog(PassSlipMonitoringRecord record, String actionType, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gate Log Confirmation");
        alert.setHeaderText("Confirm Log " + actionType);

        if (monitoringTable.getScene() != null && monitoringTable.getScene().getWindow() != null) {
            alert.initOwner(monitoringTable.getScene().getWindow());
        }

        String passType = "Standard";
        String destination = "N/A";
        String nature = "N/A";

        String rawReason = record.getReasonForLeaving();
        if (rawReason == null || rawReason.isBlank()) rawReason = record.getReason();

        if (rawReason != null && !rawReason.isBlank()) {
            rawReason = rawReason.replace("\n", " ").replace("\r", " ");
            if (rawReason.contains("|")) {
                String[] parts = rawReason.split("\\|");
                for (String part : parts) {
                    part = part.trim();
                    if (part.toLowerCase().startsWith("type:")) passType = part.substring(5).trim();
                    else if (part.toLowerCase().startsWith("destination:")) destination = part.substring(12).trim();
                    else if (part.toLowerCase().startsWith("reason:")) nature = part.substring(7).trim();
                }
            } else {
                if (rawReason.toLowerCase().startsWith("destination:")) destination = rawReason.substring(12).trim();
                else if (rawReason.toLowerCase().startsWith("reason:")) nature = rawReason.substring(7).trim();
                else if (rawReason.toLowerCase().startsWith("type:")) passType = rawReason.substring(5).trim();
                else nature = rawReason.trim();
            }
        }

        String content = String.format(
                "Are you sure you want to log %s for the following employee?\n\n" +
                        "Slip No:\t\t%s\n" +
                        "Employee ID:\t%s\n" +
                        "Name:\t\t%s\n" +
                        "Pass Type:\t%s\n" +
                        "Destination:\t%s\n" +
                        "Reason:\t\t%s\n\n" +
                        "Please verify the employee's identity before confirming.",
                actionType, record.getSlipNo(), record.getEmployeeId(), record.getName(),
                passType, destination, nature
        );

        alert.setContentText(content);
        ButtonType confirmButton = new ButtonType("Confirm " + actionType, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        alert.showAndWait().ifPresent(response -> { if (response == confirmButton) onConfirm.run(); });
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList("All", "Approved", "Out", "Excused", "Returned"));
        statusFilter.setValue("All");
        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void loadData() {
        List<PassSlipMonitoringRecord> allRecords = repository.findAll();
        String todayString = java.time.LocalDate.now().toString();
        List<PassSlipMonitoringRecord> guardRecords = allRecords.stream()
                .filter(r -> r.getDate() != null && r.getDate().toString().equals(todayString))
                .filter(r -> "Approved".equalsIgnoreCase(r.getStatus()) || "Out".equalsIgnoreCase(r.getStatus()) ||
                        "Excused".equalsIgnoreCase(r.getStatus()) || "Returned".equalsIgnoreCase(r.getStatus()))
                .collect(Collectors.toList());
        masterData.setAll(guardRecords);
        updateDashboardKPIs(guardRecords);
        applyFilters();
    }

    private void applyFilters() {
        FilteredList<PassSlipMonitoringRecord> filteredData = new FilteredList<>(masterData, b -> true);
        filteredData.setPredicate(record -> {
            String searchFilter = searchField.getText().toLowerCase();
            String statusCombo = statusFilter.getValue();
            boolean matchesSearch = record.getName().toLowerCase().contains(searchFilter) ||
                    record.getSlipNo().toLowerCase().contains(searchFilter) ||
                    record.getEmployeeId().toLowerCase().contains(searchFilter);
            boolean matchesStatus = statusCombo.equals("All") || record.getStatus().equalsIgnoreCase(statusCombo);
            return matchesSearch && matchesStatus;
        });
        SortedList<PassSlipMonitoringRecord> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(monitoringTable.comparatorProperty());
        monitoringTable.setItems(sortedData);
        countLabel.setText("Showing " + filteredData.size() + " active gate records");
    }

    private void updateDashboardKPIs(List<PassSlipMonitoringRecord> records) {
        long approvedCount = records.stream().filter(r -> "Approved".equalsIgnoreCase(r.getStatus())).count();

        // 🟢 FIX: Only count active 'Out' statuses, completely ignoring 'Excused'
        long outCount = records.stream().filter(r -> "Out".equalsIgnoreCase(r.getStatus())).count();

        long returnedCount = records.stream().filter(r -> "Returned".equalsIgnoreCase(r.getStatus())).count();

        approvedSlipsLabel.setText(String.valueOf(approvedCount));
        currentlyOutLabel.setText(String.valueOf(outCount));
        returnedLabel.setText(String.valueOf(returnedCount));
    }
}