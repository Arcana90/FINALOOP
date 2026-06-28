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

        // Dynamic Date Formatter
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        dateColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : AppSettingsManager.getInstance().formatDateString(item));
            }
        });

        // Dynamic Time Out Formatter
        timeOutColumn.setCellValueFactory(new PropertyValueFactory<>("timeOut"));
        timeOutColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : AppSettingsManager.getInstance().formatTimeString(item));
            }
        });

        // Dynamic Time In Formatter
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
                    PassSlipMonitoringRecord record = getTableView().getItems().get(getIndex());

                    // Call the dialog instead of the repository directly
                    showConfirmationDialog(record, "Time Out", () -> {
                        if (repository.markAsOut(record.getPassSlipId())) {
                            loadData();
                        }
                    });
                });

                btnIn.setOnAction(event -> {
                    PassSlipMonitoringRecord record = getTableView().getItems().get(getIndex());

                    // Call the dialog instead of the repository directly
                    showConfirmationDialog(record, "Time In", () -> {
                        if (repository.markAsReturned(record.getPassSlipId())) {
                            loadData();
                        }
                    });
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    PassSlipMonitoringRecord record = getTableView().getItems().get(getIndex());
                    String status = record.getStatus();

                    // 🟢 Extract the pass slip type from the reason string safely
                    String passSlipType = "";
                    String rawReason = record.getReason();
                    if (rawReason != null && rawReason.contains("|")) {
                        String[] parts = rawReason.split("\\|");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.toLowerCase().startsWith("type:")) {
                                passSlipType = part.substring(5).trim();
                                break;
                            }
                        }
                    }

                    // 🟢 Check if this is an Official Business, Personal, or Emergency pass slip
                    boolean isSpecialPass = passSlipType.equalsIgnoreCase("Official Business")
                            || passSlipType.equalsIgnoreCase("Personal")
                            || passSlipType.equalsIgnoreCase("Emergency")
                            || "Official Business".equalsIgnoreCase(status)
                            || "Personal".equalsIgnoreCase(status)
                            || "Emergency".equalsIgnoreCase(status);

                    // 🌟 FIX: Added check for "-" so empty times aren't treated as filled
                    boolean hasTimeIn = record.getTimeIn() != null
                            && !record.getTimeIn().trim().isEmpty()
                            && !record.getTimeIn().equalsIgnoreCase("null")
                            && !record.getTimeIn().equalsIgnoreCase("N/A")
                            && !record.getTimeIn().equals("-");

                    if ("Approved".equalsIgnoreCase(status)) {
                        btnOut.setVisible(true);
                        btnOut.setManaged(true);

                        // Allow "Log Time In" to be visible alongside Log Time Out for special passes
                        if (isSpecialPass && !hasTimeIn) {
                            btnIn.setVisible(true);
                            btnIn.setManaged(true);
                        } else {
                            btnIn.setVisible(false);
                            btnIn.setManaged(false);
                        }
                        setGraphic(pane);

                    } else if ("Out".equalsIgnoreCase(status)
                            || "Excused".equalsIgnoreCase(status)
                            || "Official Business".equalsIgnoreCase(status)
                            || "Personal".equalsIgnoreCase(status)
                            || "Emergency".equalsIgnoreCase(status)) {

                        btnOut.setVisible(false);
                        btnOut.setManaged(false);

                        if (!hasTimeIn) {
                            btnIn.setVisible(true);
                            btnIn.setManaged(true);
                            setGraphic(pane);
                        } else {
                            btnIn.setVisible(false);
                            btnIn.setManaged(false);
                            setGraphic(null);
                        }
                    } else {
                        setGraphic(null);
                    }
                }
            }
        }); // 🚨 FIX: Added closing parenthesis and brace for the cell factory
    } // 🚨 FIX: Added closing brace for setupColumns() method

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
                .filter(r -> "Approved".equalsIgnoreCase(r.getStatus()) ||
                        "Out".equalsIgnoreCase(r.getStatus()) ||
                        "Excused".equalsIgnoreCase(r.getStatus()) ||
                        "Returned".equalsIgnoreCase(r.getStatus()))
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
        long outCount = records.stream().filter(r -> "Out".equalsIgnoreCase(r.getStatus()) || "Excused".equalsIgnoreCase(r.getStatus())).count();
        long returnedCount = records.stream().filter(r -> "Returned".equalsIgnoreCase(r.getStatus())).count();

        approvedSlipsLabel.setText(String.valueOf(approvedCount));
        currentlyOutLabel.setText(String.valueOf(outCount));
        returnedLabel.setText(String.valueOf(returnedCount));
    }
    // 🌟 UPDATED METHOD: Now includes Pass Type extraction and display
    private void showConfirmationDialog(PassSlipMonitoringRecord record, String actionType, Runnable onConfirm) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Gate Log Confirmation");
        alert.setHeaderText("Confirm Log " + actionType);

        // 🌟 ROBUST PARSING: Strictly find one of the three categories
        String passSlipType = "";
        String rawReason = record.getReason();

        if (rawReason != null) {
            String lower = rawReason.toLowerCase();

            // Check for the three specific types regardless of their position in the string
            if (lower.contains("personal")) {
                passSlipType = "Personal";
            } else if (lower.contains("official business")) {
                passSlipType = "Official Business";
            } else if (lower.contains("emergency")) {
                passSlipType = "Emergency";
            }
        }

        // Build the dialog content
        String content = String.format(
                "Are you sure you want to log %s for the following employee?\n\n" +
                        "Slip No:\t\t%s\n" +
                        "Employee ID:\t%s\n" +
                        "Name:\t\t%s\n" +
                        "Pass Type:\t%s\n" +
                        "Reason:\t\t%s\n\n" +
                        "Please verify the employee's identity before confirming.",
                actionType,
                record.getSlipNo(),
                record.getEmployeeId(),
                record.getName(),
                passSlipType,
                record.getReason() != null ? record.getReason().replace("\n", " ") : "N/A"
        );
        alert.setContentText(content);

        ButtonType confirmButton = new ButtonType("Confirm " + actionType, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(confirmButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == confirmButton) {
                onConfirm.run();
            }
        });
    }
    }