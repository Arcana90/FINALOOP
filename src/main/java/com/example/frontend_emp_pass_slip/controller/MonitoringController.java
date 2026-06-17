package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.MonitoringJdbcRepository;
import backend.passslip.PassSlipMonitoringRecord;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import backend.app.AppSettingsManager;
import java.util.Optional;import javafx.stage.FileChooser;
import javafx.print.PrinterJob;
import java.io.File;
import java.io.PrintWriter;import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Element;
import java.awt.Color;
import java.io.FileOutputStream;

public class MonitoringController {

    @FXML private Label totalEmployeesLabel;
    @FXML private Label activePassSlipsLabel;
    @FXML private Label totalRecordsLabel;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> statusFilter;
    @FXML private Label countLabel;

    @FXML private TableView<PassSlipMonitoringRecord> monitoringTable;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> slipNoColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> employeeIdColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> departmentColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> dateColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> nameColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> timeOutColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> timeInColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> durationColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> typeColumn;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> statusColumn;

    private final MonitoringJdbcRepository monitoringRepository = new MonitoringJdbcRepository();
    private final ObservableList<PassSlipMonitoringRecord> records = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        setupColumns();
        setupFilters();
        loadRecordsFromDatabase();

        monitoringTable.setOnMouseClicked(event -> {
            PassSlipMonitoringRecord selected = monitoringTable.getSelectionModel().getSelectedItem();

            if (event.getClickCount() == 2 && selected != null && "Out".equalsIgnoreCase(selected.getStatus())) {
                showTimeInDialog(selected);
            }
        });
    }

    private void setupColumns() {
        slipNoColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getSlipNo()));
        employeeIdColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getEmployeeId()));
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getName()));
        departmentColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDepartment()));
        durationColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDuration()));
        typeColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getType()));
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getStatus()));

        dateColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(AppSettingsManager.getInstance().formatDateString(data.getValue().getDate())));

        timeOutColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(AppSettingsManager.getInstance().formatTimeString(data.getValue().getTimeOut())));

        timeInColumn.setCellValueFactory(data ->
                new ReadOnlyStringWrapper(AppSettingsManager.getInstance().formatTimeString(data.getValue().getTimeIn())));
    }

    private void setupFilters() {
        statusFilter.setItems(FXCollections.observableArrayList(
                "All Status", "Out", "Returned"
        ));

        statusFilter.getSelectionModel().selectFirst();

        FilteredList<PassSlipMonitoringRecord> filtered =
                new FilteredList<>(records, record -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) ->
                applyFilters(filtered)
        );

        statusFilter.valueProperty().addListener((observable, oldValue, newValue) ->
                applyFilters(filtered)
        );

        filtered.addListener((javafx.collections.ListChangeListener<PassSlipMonitoringRecord>) change ->
                updateCount(filtered.size())
        );

        monitoringTable.setItems(filtered);
    }

    private void loadRecordsFromDatabase() {
        records.setAll(monitoringRepository.findAll());
        updateCount(records.size());
        updateStatistics();
    }

    private void updateStatistics() {
        long totalRecords = records.size();

        long currentlyOut = records.stream()
                .filter(r -> "Out".equalsIgnoreCase(r.getStatus()))
                .count();

        long returned = records.stream()
                .filter(r -> "Returned".equalsIgnoreCase(r.getStatus()))
                .count();

        totalEmployeesLabel.setText(String.valueOf(totalRecords));
        activePassSlipsLabel.setText(String.valueOf(currentlyOut));
        totalRecordsLabel.setText(String.valueOf(returned));
    }

    private void applyFilters(FilteredList<PassSlipMonitoringRecord> filtered) {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase();

        String status = statusFilter.getValue() == null
                ? "All Status"
                : statusFilter.getValue();

        filtered.setPredicate(record -> {
            boolean matchesQuery = query.isEmpty()
                    || record.getName().toLowerCase().contains(query)
                    || record.getSlipNo().toLowerCase().contains(query)
                    || record.getEmployeeId().toLowerCase().contains(query)
                    || record.getDepartment().toLowerCase().contains(query);

            boolean matchesStatus =
                    "All Status".equals(status) || record.getStatus().equalsIgnoreCase(status);

            return matchesQuery && matchesStatus;
        });
    }

    private void updateCount(int count) {
        countLabel.setText("Showing " + count + " of " + records.size() + " records");
    }

    private void showTimeInDialog(PassSlipMonitoringRecord record) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Employee Time-In");
        dialog.setHeaderText("Record Employee Time-In: " + record.getName());

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(12);
        form.setPadding(new Insets(8, 10, 4, 10));

        ComboBox<String> remarks = new ComboBox<>(FXCollections.observableArrayList(
                "Returned"
        ));
        remarks.getSelectionModel().selectFirst();

        String formattedTimeOut = AppSettingsManager.getInstance().formatTimeString(record.getTimeOut());

        form.addRow(0, new Label("Slip No:"), new Label(record.getSlipNo()));
        form.addRow(1, new Label("Employee Name:"), new Label(record.getName()));
        form.addRow(2, new Label("Department:"), new Label(record.getDepartment()));
        form.addRow(3, new Label("Time Out:"), new Label(formattedTimeOut));
        form.addRow(4, new Label("Remarks:"), remarks);

        dialog.getDialogPane().setContent(form);

        ButtonType confirmButtonType = new ButtonType("Confirm Time-In", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == confirmButtonType) {
                boolean updated = monitoringRepository.markAsReturned(record.getPassSlipId());

                if (updated) {
                    loadRecordsFromDatabase();
                    showInfo("Time-In Recorded", "Employee has been marked as returned.");
                } else {
                    showError("Update Failed", "Could not mark this pass slip as returned.");
                }
            }
        });
    }

    @FXML
    private void handleExportOptions() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Export Options");
        alert.setHeaderText("Choose Export Format");
        alert.setContentText("How would you like to save or print this report?");

        ButtonType pdfButton = new ButtonType("Print / Save as PDF", ButtonBar.ButtonData.OK_DONE);
        ButtonType csvButton = new ButtonType("Export to Excel (CSV)", ButtonBar.ButtonData.OTHER);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(pdfButton, csvButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent()) {
            if (result.get() == pdfButton) {
                exportToPdf();
            } else if (result.get() == csvButton) {
                exportToCsv();
            }
        }
    }

    private void exportToPdf() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF File");
        fileChooser.setInitialFileName("PassSlip_Monitoring_Report.pdf");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));

        File file = fileChooser.showSaveDialog(monitoringTable.getScene().getWindow());

        if (file != null) {
            try {
                // 1. Initialize Document (Landscape mode to fit all your columns)
                Document document = new Document(PageSize.A4.rotate());
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                // 2. Setup the Document Table to match your JavaFX TableView
                int columnCount = monitoringTable.getColumns().size();
                PdfPTable pdfTable = new PdfPTable(columnCount);
                pdfTable.setWidthPercentage(100);

                // 3. Draw the Headers
                for (TableColumn<PassSlipMonitoringRecord, ?> column : monitoringTable.getColumns()) {
                    PdfPCell cell = new PdfPCell(new Phrase(column.getText()));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setBackgroundColor(Color.LIGHT_GRAY);
                    cell.setPadding(5);
                    pdfTable.addCell(cell);
                }

                // 4. Extract Data and Draw the Rows
                for (PassSlipMonitoringRecord item : monitoringTable.getItems()) {
                    for (TableColumn<PassSlipMonitoringRecord, ?> column : monitoringTable.getColumns()) {
                        String cellValue = "";
                        if (column.getCellObservableValue(item) != null && column.getCellObservableValue(item).getValue() != null) {
                            cellValue = column.getCellObservableValue(item).getValue().toString();
                        }

                        PdfPCell dataCell = new PdfPCell(new Phrase(cellValue));
                        dataCell.setPadding(4);
                        pdfTable.addCell(dataCell);
                    }
                }

                // 5. Add it to the file and finish!
                document.add(pdfTable);
                document.close();

                showInfo("Export Successful", "PDF file successfully saved to:\n" + file.getAbsolutePath());

            } catch (Exception e) {
                showError("Export Failed", "An error occurred while generating the PDF:\n" + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void exportToCsv() {
        // 1. Open a FileChooser so the user can pick where to save the file
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV File");
        fileChooser.setInitialFileName("PassSlip_Monitoring_Export.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showSaveDialog(monitoringTable.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                // 2. Write the column headers
                StringBuilder headerLine = new StringBuilder();
                for (TableColumn<PassSlipMonitoringRecord, ?> column : monitoringTable.getColumns()) {
                    headerLine.append("\"").append(column.getText()).append("\",");
                }
                writer.println(headerLine.substring(0, headerLine.length() - 1));

                // 3. Write the actual data currently displayed in the table (respects search/filters!)
                for (PassSlipMonitoringRecord item : monitoringTable.getItems()) {
                    StringBuilder dataLine = new StringBuilder();
                    for (TableColumn<PassSlipMonitoringRecord, ?> column : monitoringTable.getColumns()) {

                        String cellValue = "";
                        // Grab the exact text displayed in the cell
                        if (column.getCellObservableValue(item) != null && column.getCellObservableValue(item).getValue() != null) {
                            cellValue = column.getCellObservableValue(item).getValue().toString();
                        }

                        // Escape quotes to prevent CSV formatting issues
                        cellValue = cellValue.replace("\"", "\"\"");
                        dataLine.append("\"").append(cellValue).append("\",");
                    }
                    writer.println(dataLine.substring(0, dataLine.length() - 1));
                }

                showInfo("Export Successful", "CSV file successfully saved to:\n" + file.getAbsolutePath());

            } catch (Exception e) {
                showError("Export Failed", "An error occurred while saving the file:\n" + e.getMessage());
            }
        }
    }
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}