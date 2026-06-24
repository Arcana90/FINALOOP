package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.ReportsJdbcRepository;
import backend.passslip.ReportsStats;
import backend.passslip.DailyActivitySummary;
import backend.passslip.MonthlyActivitySummary;
import backend.passslip.ReportEmployeeSummary;
import backend.passslip.EmployeePassSlipDetail;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.util.Callback;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ReportsController {

    @FXML private Label totalSlipsLabel;
    @FXML private Label currentlyOutLabel;
    @FXML private Label officialLabel;
    @FXML private Label avgDurationLabel;

    @FXML private Label chartTitleLabel;
    @FXML private Button dailyBtn;
    @FXML private Button monthlyBtn;
    @FXML private Button printBtn;

    @FXML private BarChart<String, Number> activityChart;
    private CategoryAxis dayAxis;
    private NumberAxis valueAxis;

    @FXML private TableView<ReportEmployeeSummary> reportsTable;
    @FXML private TableColumn<ReportEmployeeSummary, String> employeeIdCol;
    @FXML private TableColumn<ReportEmployeeSummary, String> employeeNameCol;
    @FXML private TableColumn<ReportEmployeeSummary, Integer> personalCol;
    @FXML private TableColumn<ReportEmployeeSummary, Integer> officialCol;
    @FXML private TableColumn<ReportEmployeeSummary, Integer> totalCol;
    @FXML private TableColumn<ReportEmployeeSummary, Void> actionCol;

    private final XYChart.Series<String, Number> officialSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> personalSeries = new XYChart.Series<>();

    private final ReportsJdbcRepository reportsRepository = new ReportsJdbcRepository();
    private final Popup customPopup = new Popup();
    private boolean isDailyView = true;

    @FXML
    public void initialize() {
        setupChartBase();
        setupButtons();
        setupTableColumns();
        loadReportsFromDatabase();
    }

    private void setupButtons() {
        dailyBtn.setOnAction(e -> switchToDailyView());
        monthlyBtn.setOnAction(e -> switchToMonthlyView());
        printBtn.setOnAction(e -> exportDataToCsv());
    }

    private void setupChartBase() {
        this.dayAxis = (CategoryAxis) activityChart.getXAxis();
        this.valueAxis = (NumberAxis) activityChart.getYAxis();

        officialSeries.setName("Official");
        personalSeries.setName("Personal");

        valueAxis.setAutoRanging(true);
        valueAxis.setTickUnit(1);
        valueAxis.setMinorTickCount(0);

        activityChart.setAnimated(false);
        dayAxis.setAnimated(false);
        valueAxis.setAnimated(false);
    }

    private void setupTableColumns() {
        employeeIdCol.setCellValueFactory(new PropertyValueFactory<>("employeeId"));
        employeeNameCol.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        personalCol.setCellValueFactory(new PropertyValueFactory<>("personalCount"));
        officialCol.setCellValueFactory(new PropertyValueFactory<>("officialCount"));
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalCount"));

        addButtonToTable();
    }

    private void addButtonToTable() {
        Callback<TableColumn<ReportEmployeeSummary, Void>, TableCell<ReportEmployeeSummary, Void>> cellFactory = param -> new TableCell<>() {
            private final Button btn = new Button("Print");
            {
                btn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand; -fx-font-size: 11px; -fx-padding: 4 10;");
                btn.setOnAction(event -> {
                    ReportEmployeeSummary data = getTableView().getItems().get(getIndex());
                    handleIndividualExport(data);
                });
            }

            @Override
            public void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        };
        actionCol.setCellFactory(cellFactory);
    }

    private void loadReportsFromDatabase() {
        ReportsStats stats = reportsRepository.getStats();
        totalSlipsLabel.setText(String.valueOf(stats.getTotalSlips()));
        currentlyOutLabel.setText(String.valueOf(stats.getCurrentlyOut()));
        officialLabel.setText(String.valueOf(stats.getOfficial()));
        avgDurationLabel.setText(stats.getAvgDuration());

        List<ReportEmployeeSummary> employeeSummaries = reportsRepository.getEmployeeSummaries();
        reportsTable.setItems(FXCollections.observableArrayList(employeeSummaries));

        switchToDailyView();
    }

    private void handleIndividualExport(ReportEmployeeSummary employee) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Export Individual Report");
        alert.setHeaderText("Export options for: " + employee.getEmployeeName());
        alert.setContentText("Choose your output document format:");

        ButtonType buttonPdf = new ButtonType("Save as PDF");
        ButtonType buttonCsv = new ButtonType("Export to Excel (CSV)");
        ButtonType buttonCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonPdf, buttonCsv, buttonCancel);

        alert.showAndWait().ifPresent(type -> {
            List<EmployeePassSlipDetail> details = reportsRepository.getEmployeePassSlipDetails(employee.getEmployeeId());
            if (type == buttonPdf) {
                printIndividualPdf(employee, details);
            } else if (type == buttonCsv) {
                exportIndividualCsv(employee, details);
            }
        });
    }

    private void printIndividualPdf(ReportEmployeeSummary emp, List<EmployeePassSlipDetail> details) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save PDF Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
        chooser.setInitialFileName(emp.getEmployeeName().replace(" ", "_") + "_PassSlip_Report.pdf");

        File file = chooser.showSaveDialog(reportsTable.getScene().getWindow());
        if (file != null) {
            try {
                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                document.add(new Paragraph("INDIVIDUAL PASS SLIP HISTORY REPORT"));
                document.add(new Paragraph("Employee ID: " + emp.getEmployeeId() + " | Name: " + emp.getEmployeeName()));
                document.add(new Paragraph("\n"));

                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{14f, 11f, 15f, 24f, 12f, 12f, 12f});

                table.addCell("Date Issued");
                table.addCell("Type");
                table.addCell("Destination");
                table.addCell("Reason");
                table.addCell("Time Out");
                table.addCell("Time In");
                table.addCell("Status");

                for (EmployeePassSlipDetail d : details) {
                    table.addCell(d.getExpectedTime() != null ? d.getExpectedTime() : "");
                    table.addCell(d.getTypeOfPass() != null ? d.getTypeOfPass() : "");
                    table.addCell(d.getDestination() != null ? d.getDestination() : "");
                    table.addCell(d.getReason() != null ? d.getReason() : "");
                    table.addCell(formatTimeForReport(d.getTimeOut())); // Merged AM/PM formatter
                    table.addCell(formatTimeForReport(d.getTimeIn()));  // Merged AM/PM formatter
                    table.addCell(d.getStatus() != null ? d.getStatus() : "");
                }

                document.add(table);
                document.add(new Paragraph("\nSUMMARY METRICS"));
                document.add(new Paragraph("Total Personal Slips: " + emp.getPersonalCount()));
                document.add(new Paragraph("Total Official Slips: " + emp.getOfficialCount()));
                document.add(new Paragraph("Total Slips: " + emp.getTotalCount()));

                document.close();

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "PDF generated successfully!");
                alert.showAndWait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void exportIndividualCsv(ReportEmployeeSummary emp, List<EmployeePassSlipDetail> details) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Excel Report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        chooser.setInitialFileName(emp.getEmployeeName().replace(" ", "_") + "_PassSlip_Report.csv");

        File file = chooser.showSaveDialog(reportsTable.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("INDIVIDUAL PASS SLIP RECORD REPORT");
                writer.println("Employee ID," + emp.getEmployeeId());
                writer.println("Employee Name," + emp.getEmployeeName());
                writer.println();

                // Headers matched to the PDF for consistency
                writer.println("Date Issued,Type of Pass,Destination,Reason/Nature,Time Out,Time In,Status");

                // FIXED: Replaced invalid table.addCell with correct CSV string building.
                // Wrapped fields in quotes to prevent commas inside destinations/reasons from breaking the file format.
                for (EmployeePassSlipDetail d : details) {
                    writer.println(
                            "\"" + (d.getExpectedTime() != null ? d.getExpectedTime() : "") + "\"," +
                                    "\"" + (d.getTypeOfPass() != null ? d.getTypeOfPass() : "") + "\"," +
                                    "\"" + (d.getDestination() != null ? d.getDestination() : "") + "\"," +
                                    "\"" + (d.getReason() != null ? d.getReason() : "") + "\"," +
                                    "\"" + formatTimeForReport(d.getTimeOut()) + "\"," +
                                    "\"" + formatTimeForReport(d.getTimeIn()) + "\"," +
                                    "\"" + (d.getStatus() != null ? d.getStatus() : "") + "\""
                    );
                }

                writer.println();
                writer.println("SUMMARY METRICS");
                writer.println("Total Personal Slips," + emp.getPersonalCount());
                writer.println("Total Official Business Slips," + emp.getOfficialCount());
                writer.println("Total Slips for the Employee," + emp.getTotalCount());

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setContentText("Excel report generated successfully!");
                alert.showAndWait();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void switchToDailyView() {
        isDailyView = true;
        if (chartTitleLabel != null) chartTitleLabel.setText("DAILY ACTIVITY - THIS WEEK");
        dailyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand;");
        monthlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");
        activityChart.getData().clear();
        dayAxis.setCategories(FXCollections.observableArrayList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        updateDailyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void switchToMonthlyView() {
        isDailyView = false;
        if (chartTitleLabel != null) chartTitleLabel.setText("MONTHLY SUMMARY — YEAR TO DATE");
        monthlyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand;");
        dailyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");
        activityChart.getData().clear();
        dayAxis.setCategories(FXCollections.observableArrayList("Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"));
        updateMonthlyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void updateDailyData() {
        officialSeries.getData().clear(); personalSeries.getData().clear();
        List<DailyActivitySummary> dbData = reportsRepository.findWeeklyDailyActivity();
        Map<String, DailyActivitySummary> dataMap = new HashMap<>();
        for (DailyActivitySummary s : dbData) dataMap.put(s.getDay(), s);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String day : days) {
            DailyActivitySummary summary = dataMap.getOrDefault(day, new DailyActivitySummary(day, 0, 0));
            XYChart.Data<String, Number> offData = new XYChart.Data<>(day, summary.getOfficialCount());
            XYChart.Data<String, Number> persData = new XYChart.Data<>(day, summary.getPersonalCount());
            officialSeries.getData().add(offData); personalSeries.getData().add(persData);
            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
                attachHoverEffect(persData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
            });
        }
    }

    private void updateMonthlyData() {
        officialSeries.getData().clear(); personalSeries.getData().clear();
        List<MonthlyActivitySummary> dbData = reportsRepository.findMonthlyActivity();
        Map<String, MonthlyActivitySummary> dataMap = new HashMap<>();
        for (MonthlyActivitySummary s : dbData) dataMap.put(s.getMonth(), s);
        String[] months = {"Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"};
        for (String month : months) {
            MonthlyActivitySummary summary = dataMap.get(month);
            int offVal = (summary != null) ? summary.getOfficialCount() : 0;
            int persVal = (summary != null) ? summary.getPersonalCount() : 0;
            XYChart.Data<String, Number> offData = new XYChart.Data<>(month, offVal);
            XYChart.Data<String, Number> persData = new XYChart.Data<>(month, persVal);
            officialSeries.getData().add(offData); personalSeries.getData().add(persData);
            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), month, "Official : " + offVal, "Personal : " + persVal);
                attachHoverEffect(persData.getNode(), month, "Official : " + offVal, "Personal : " + persVal);
            });
        }
    }

    private void attachHoverEffect(Node barNode, String titleText, String line1, String line2) {
        if (barNode == null) return;
        barNode.setOnMouseEntered(event -> {
            VBox content = createPopupBox(titleText, line1, line2);
            content.setMouseTransparent(true);
            customPopup.getContent().setAll(content);
            Bounds bounds = barNode.localToScreen(barNode.getBoundsInLocal());
            customPopup.show(barNode.getScene().getWindow(), bounds.getMinX() + (bounds.getWidth() / 2) - 50, bounds.getMinY() - 70);
        });
        barNode.setOnMouseExited(event -> customPopup.hide());
    }

    private VBox createPopupBox(String titleText, String line1Text, String line2Text) {
        VBox box = new VBox(5);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 10; -fx-border-color: #cccccc; -fx-background-radius: 5;");
        Label title = new Label(titleText); title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label l1 = new Label(line1Text); l1.setStyle("-fx-text-fill: #2196F3;");
        box.getChildren().addAll(title, l1);
        if (line2Text != null) {
            Label l2 = new Label(line2Text); l2.setStyle("-fx-text-fill: #FF5722;");
            box.getChildren().add(l2);
        }
        return box;
    }

    private void exportDataToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Overall Chart Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
        fileChooser.setInitialFileName("Overall_PassSlip_Chart_Report.csv");
        File file = fileChooser.showSaveDialog(printBtn.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println(isDailyView ? "Day,Official,Personal" : "Month,Official,Personal");
                for (String category : dayAxis.getCategories()) {
                    writer.println(category + "," + getSeriesValue(officialSeries, category) + "," + getSeriesValue(personalSeries, category));
                }
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Chart export saved successfully!");
                alert.showAndWait();
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private Number getSeriesValue(XYChart.Series<String, Number> series, String category) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getXValue().equals(category)) return data.getYValue();
        }
        return 0;
    }

    private String formatTimeForReport(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty() || rawTime.equalsIgnoreCase("null")) {
            return "";
        }
        try {
            LocalTime time = LocalTime.parse(rawTime);
            return time.format(DateTimeFormatter.ofPattern("hh:mm a"));
        } catch (DateTimeParseException e) {
            return rawTime;
        }
    }
}