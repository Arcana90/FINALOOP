package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.ReportsJdbcRepository;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ReportsController {
    @FXML private Label chartTitleLabel;
    @FXML private Button dailyBtn;
    @FXML private Button monthlyBtn;
    @FXML private Button quarterlyBtn;
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
    // 🟢 ADD THIS MISSING LINE HERE:
    @FXML private TableColumn<ReportEmployeeSummary, Integer> emergencyCol;


    private final XYChart.Series<String, Number> officialSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> personalSeries = new XYChart.Series<>();

    private final ReportsJdbcRepository reportsRepository = new ReportsJdbcRepository();
    private final Popup customPopup = new Popup();

    private String currentView = "DAILY";

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
        quarterlyBtn.setOnAction(e -> switchToQuarterlyView());
        printBtn.setOnAction(e -> handleOverallExport());
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

        // 🟢 ADD THIS LINE HERE:
        emergencyCol.setCellValueFactory(new PropertyValueFactory<>("emergencyCount"));

        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalCount"));

        addButtonToTable();
    }

    private void addButtonToTable() {
        Callback<TableColumn<ReportEmployeeSummary, Void>, TableCell<ReportEmployeeSummary, Void>> cellFactory = param -> new TableCell<> () {
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
        List<ReportEmployeeSummary> employeeSummaries = reportsRepository.getEmployeeSummaries();
        reportsTable.setItems(FXCollections.observableArrayList(employeeSummaries));
        switchToDailyView();
    }

    private void clearAndResetChartAxis(List<String> categories) {
        activityChart.getData().clear();
        officialSeries.getData().clear();
        personalSeries.getData().clear();
        dayAxis.getCategories().clear();
        dayAxis.setCategories(FXCollections.observableArrayList(categories));
    }

    private void switchToDailyView() {
        currentView = "DAILY";
        if (chartTitleLabel != null) chartTitleLabel.setText("DAILY ACTIVITY - THIS WEEK");

        dailyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand;");
        monthlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");
        quarterlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");

        clearAndResetChartAxis(List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        updateDailyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void switchToMonthlyView() {
        currentView = "MONTHLY";
        if (chartTitleLabel != null) chartTitleLabel.setText("MONTHLY SUMMARY — YEAR TO DATE");

        monthlyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand;");
        dailyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");
        quarterlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");

        clearAndResetChartAxis(List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"));
        updateMonthlyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void switchToQuarterlyView() {
        currentView = "QUARTERLY";
        if (chartTitleLabel != null) chartTitleLabel.setText("QUARTERLY SUMMARY — YEAR TO DATE");

        quarterlyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white; -fx-cursor: hand;");
        dailyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");
        monthlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3; -fx-cursor: hand;");

        clearAndResetChartAxis(List.of("Q1", "Q2", "Q3", "Q4"));
        updateQuarterlyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void updateDailyData() {
        List<DailyActivitySummary> dbData = reportsRepository.findWeeklyDailyActivity();
        Map<String, DailyActivitySummary> dataMap = new HashMap<>();
        for (DailyActivitySummary s : dbData) dataMap.put(s.getDayName(), s);
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (String day : days) {
            DailyActivitySummary summary = dataMap.getOrDefault(day, new DailyActivitySummary(day, 0, 0, 0));            XYChart.Data<String, Number> offData = new XYChart.Data<>(day, summary.getOfficialCount());
            XYChart.Data<String, Number> persData = new XYChart.Data<>(day, summary.getPersonalCount());
            officialSeries.getData().add(offData); personalSeries.getData().add(persData);
            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
                attachHoverEffect(persData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
            });
        }
    }

    private void updateMonthlyData() {
        List<MonthlyActivitySummary> dbData = reportsRepository.findMonthlyActivity();
        Map<String, MonthlyActivitySummary> dataMap = new HashMap<>();
        for (MonthlyActivitySummary s : dbData) dataMap.put(s.getMonthName(), s);
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
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

    private void updateQuarterlyData() {
        List<MonthlyActivitySummary> dbData = reportsRepository.findMonthlyActivity();

        int[] qOfficial = new int[4];
        int[] qPersonal = new int[4];

        for (MonthlyActivitySummary s : dbData) {
            String m = s.getMonthName();
            int idx = -1;
            if (m.equals("Jan") || m.equals("Feb") || m.equals("Mar")) idx = 0;
            else if (m.equals("Apr") || m.equals("May") || m.equals("Jun")) idx = 1;
            else if (m.equals("Jul") || m.equals("Aug") || m.equals("Sep")) idx = 2;
            else if (m.equals("Oct") || m.equals("Nov") || m.equals("Dec")) idx = 3;

            if (idx != -1) {
                qOfficial[idx] += s.getOfficialCount();
                qPersonal[idx] += s.getPersonalCount();
            }
        }

        String[] quarters = {"Q1", "Q2", "Q3", "Q4"};
        for (int i = 0; i < 4; i++) {
            String q = quarters[i];
            int offVal = qOfficial[i];
            int persVal = qPersonal[i];

            XYChart.Data<String, Number> offData = new XYChart.Data<>(q, offVal);
            XYChart.Data<String, Number> persData = new XYChart.Data<>(q, persVal);
            officialSeries.getData().add(offData); personalSeries.getData().add(persData);

            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), q, "Official : " + offVal, "Personal : " + persVal);
                attachHoverEffect(persData.getNode(), q, "Official : " + offVal, "Personal : " + persVal);
            });
        }
    }

    private void handleOverallExport() {
        if (currentView.equals("QUARTERLY")) {
            List<String> choices = Arrays.asList(
                    "All Quarters Summary",
                    "Q1 (Jan, Feb, Mar)",
                    "Q2 (Apr, May, Jun)",
                    "Q3 (Jul, Aug, Sep)",
                    "Q4 (Oct, Nov, Dec)"
            );

            ChoiceDialog<String> choiceDialog = new ChoiceDialog<>("All Quarters Summary", choices);
            choiceDialog.setTitle("Quarterly Export Options");
            choiceDialog.setHeaderText("Select the Quarter you want to print:");
            choiceDialog.setContentText("Target Quarter:");

            Optional<String> choiceResult = choiceDialog.showAndWait();
            if (choiceResult.isPresent()) {
                showFormatSelectionAlert(choiceResult.get());
            }
        } else {
            showFormatSelectionAlert(null);
        }
    }

    private void showFormatSelectionAlert(String quarterDetailOption) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Export Chart Data");

        boolean isQuarterDetail = (quarterDetailOption != null && !quarterDetailOption.equals("All Quarters Summary"));

        String headerText = "Export overall " + currentView.toLowerCase() + " statistics:";
        if (isQuarterDetail) {
            headerText = "Export detailed statistics for " + quarterDetailOption.substring(0, 2) + ":";
        }

        alert.setHeaderText(headerText);
        alert.setContentText("Choose your output document format:");

        ButtonType buttonPdf = new ButtonType("Save as PDF");
        ButtonType buttonCsv = new ButtonType("Export to Excel (CSV)");
        ButtonType buttonCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonPdf, buttonCsv, buttonCancel);

        String timeFrame = currentView.substring(0, 1) + currentView.substring(1).toLowerCase();
        String defaultFileName = timeFrame + "_Overall_PassSlip_Report";
        if (isQuarterDetail) {
            defaultFileName = quarterDetailOption.substring(0, 2) + "_Detailed_PassSlip_Report";
        }

        final String finalFileName = defaultFileName;
        alert.showAndWait().ifPresent(type -> {
            if (type == buttonPdf) {
                exportOverallPdf(finalFileName, quarterDetailOption);
            } else if (type == buttonCsv) {
                exportOverallCsv(finalFileName, quarterDetailOption);
            }
        });
    }

    private void exportOverallPdf(String defaultFileName, String quarterDetailOption) {
        if (currentView.equals("DAILY")) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Weekly PDF Report");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf"));
            chooser.setInitialFileName("Weekly_PassSlip_Operational_Report.pdf");

            File file = chooser.showSaveDialog(printBtn.getScene().getWindow());
            if (file != null) {
                try {
                    // Determine the dates of the week chosen by your UI view configuration
                    java.time.LocalDate startDate = java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    java.time.LocalDate endDate = startDate.plusDays(6);

                    // Pass the matching dates down to every repository lookup call
                    java.util.List<backend.passslip.DailyActivitySummary> dailySummaries = reportsRepository.findWeeklyDailyActivity(startDate, endDate);
                    java.util.List<backend.passslip.ReportEmployeeSummary> employeeSummaries = reportsRepository.getEmployeeSummariesForWeek(startDate, endDate);
                    java.util.List<backend.passslip.WeeklyAwolRecord> awolRecords = reportsRepository.getWeeklyAwolRecords(startDate, endDate);
                    java.util.List<backend.passslip.WeeklySlipDetailRecord> slipDetails = reportsRepository.getWeeklySlipDetails(startDate, endDate);

                    com.example.frontend_emp_pass_slip.service.WeeklyReportExporter.exportToPdf(
                            file, dailySummaries, employeeSummaries, awolRecords, slipDetails
                    );

                    new Alert(Alert.AlertType.INFORMATION, "Weekly PDF Report generated successfully!").showAndWait();
                } catch (Exception e) {
                    e.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Failed to compile the Weekly PDF report.").showAndWait();
                }
            }
            return;
        }
    }

    private void exportOverallCsv(String defaultFileName, String quarterDetailOption) {
        if (currentView.equals("DAILY")) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Weekly Excel Report");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            chooser.setInitialFileName("Weekly_PassSlip_Operational_Report.csv");

            File file = chooser.showSaveDialog(printBtn.getScene().getWindow());
            if (file != null) {
                try {
                    List<backend.passslip.DailyActivitySummary> dailySummaries = reportsRepository.findWeeklyDailyActivity();
                    List<backend.passslip.ReportEmployeeSummary> employeeSummaries = reportsRepository.getEmployeeSummaries();

                    // 🟢 Inline fully qualified paths prevent "cannot find symbol" errors
                    java.util.List<backend.passslip.WeeklyAwolRecord> awolRecords = new java.util.ArrayList<>();
                    java.util.List<backend.passslip.WeeklySlipDetailRecord> slipDetails = new java.util.ArrayList<>();

                    com.example.frontend_emp_pass_slip.service.WeeklyReportExporter.exportToCsv(
                            file, dailySummaries, employeeSummaries, awolRecords, slipDetails
                    );

                    new Alert(Alert.AlertType.INFORMATION, "Weekly CSV Report saved successfully!").showAndWait();
                } catch (Exception e) {
                    e.printStackTrace();
                    new Alert(Alert.AlertType.ERROR, "Failed to compile CSV data spreadsheet.").showAndWait();
                }
            }
            return;
        }
        // Your remaining Monthly/Quarterly fallback lines stay below untouched...
    }

    private List<String> getTargetMonths(String quarterDetailOption) {
        if (quarterDetailOption.startsWith("Q1")) return List.of("Jan", "Feb", "Mar");
        if (quarterDetailOption.startsWith("Q2")) return List.of("Apr", "May", "Jun");
        if (quarterDetailOption.startsWith("Q3")) return List.of("Jul", "Aug", "Sep");
        return List.of("Oct", "Nov", "Dec");
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
                    table.addCell(d.getDateIssued() != null ? d.getDateIssued() : "");
                    table.addCell(d.getTypeOfPass() != null ? d.getTypeOfPass() : "");
                    table.addCell(d.getDestination() != null ? d.getDestination() : "");
                    table.addCell(d.getReason() != null ? d.getReason() : "");
                    table.addCell(formatTimeForReport(d.getTimeOut()));
                    table.addCell(formatTimeForReport(d.getTimeIn()));
                    table.addCell(d.getStatus() != null ? d.getStatus() : "");
                }

                document.add(table);
                document.add(new Paragraph("\nSUMMARY METRICS"));
                document.add(new Paragraph("Total Personal Slips: " + emp.getPersonalCount()));
                document.add(new Paragraph("Total Official Slips: " + emp.getOfficialCount()));
                document.add(new Paragraph("Total Slips: " + emp.getTotalCount()));

                document.close();
                new Alert(Alert.AlertType.INFORMATION, "PDF generated successfully!").showAndWait();
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
                writer.println("INDIVIDUAL PASS SLIP HISTORY REPORT");
                writer.println("Employee ID: " + emp.getEmployeeId() + " | Name: " + emp.getEmployeeName());
                writer.println();

                writer.println("Date Issued,Type,Destination,Reason,Time Out,Time In,Status");

                for (EmployeePassSlipDetail d : details) {
                    writer.println(
                            "\"" + (d.getDateIssued() != null ? d.getDateIssued() : "") + "\"," +
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
                writer.println("Total Personal Slips: " + emp.getPersonalCount());
                writer.println("Total Official Slips: " + emp.getOfficialCount());
                writer.println("Total Slips: " + emp.getTotalCount());

                new Alert(Alert.AlertType.INFORMATION, "Excel report generated successfully!").showAndWait();
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            // Extracts time block cleanly if database appends a full date component
            if (rawTime.contains(" ")) {
                rawTime = rawTime.split(" ")[1];
            }
            if (rawTime.contains(".")) {
                rawTime = rawTime.split("\\.")[0];
            }
            LocalTime time = LocalTime.parse(rawTime);
            return time.format(DateTimeFormatter.ofPattern("hh:mm a"));
        } catch (DateTimeParseException e) {
            return rawTime;
        }
    }
}