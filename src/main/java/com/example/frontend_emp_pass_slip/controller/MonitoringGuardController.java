package com.example.frontend_emp_pass_slip.controller;

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
        timeOutColumn.setCellValueFactory(new PropertyValueFactory<>("timeOut"));
        timeInColumn.setCellValueFactory(new PropertyValueFactory<>("timeIn"));

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
                    if (repository.markAsOut(record.getPassSlipId())) {
                        loadData();
                    }
                });

                btnIn.setOnAction(event -> {
                    PassSlipMonitoringRecord record = getTableView().getItems().get(getIndex());
                    if (repository.markAsReturned(record.getPassSlipId())) {
                        loadData();
                    }
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

                    if ("Approved".equalsIgnoreCase(status)) {
                        btnOut.setVisible(true);
                        btnOut.setManaged(true);
                        btnIn.setVisible(false);
                        btnIn.setManaged(false);
                        setGraphic(pane);
                    } else if ("Out".equalsIgnoreCase(status)) {
                        btnOut.setVisible(false);
                        btnOut.setManaged(false);
                        btnIn.setVisible(true);
                        btnIn.setManaged(true);
                        setGraphic(pane);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList("All", "Approved", "Out", "Returned"));
        statusFilter.setValue("All");

        statusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void loadData() {
        List<PassSlipMonitoringRecord> allRecords = repository.findAll();

        // 🟢 DATE FILTER APPLIED HERE
        String todayString = java.time.LocalDate.now().toString();

        List<PassSlipMonitoringRecord> guardRecords = allRecords.stream()
                .filter(r -> r.getDate() != null && r.getDate().toString().equals(todayString)) // Must be today's date
                .filter(r -> "Approved".equalsIgnoreCase(r.getStatus()) ||
                        "Out".equalsIgnoreCase(r.getStatus()) ||
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
        long outCount = records.stream().filter(r -> "Out".equalsIgnoreCase(r.getStatus())).count();
        long returnedCount = records.stream().filter(r -> "Returned".equalsIgnoreCase(r.getStatus())).count();

        approvedSlipsLabel.setText(String.valueOf(approvedCount));
        currentlyOutLabel.setText(String.valueOf(outCount));
        returnedLabel.setText(String.valueOf(returnedCount));
    }
}