package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.ReportDepartmentSummary;
import backend.passslip.ReportsJdbcRepository;
import backend.passslip.ReportsStats;
import backend.passslip.DailyActivitySummary;
import backend.passslip.MonthlyActivitySummary;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;

import java.io.File;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportsController {

    @FXML private Label totalSlipsLabel;
    @FXML private Label currentlyOutLabel;
    @FXML private Label officialLabel;
    @FXML private Label avgDurationLabel;

    // UI Controls
    @FXML private Label chartTitleLabel;
    @FXML private Button dailyBtn;
    @FXML private Button monthlyBtn;
    @FXML private Button printBtn;

    // Table
    @FXML private TableView<ReportDepartmentSummary> reportsTable;
    @FXML private TableColumn<ReportDepartmentSummary, String> departmentCol;
    @FXML private TableColumn<ReportDepartmentSummary, Integer> totalCol;
    @FXML private TableColumn<ReportDepartmentSummary, Integer> officialCol;
    @FXML private TableColumn<ReportDepartmentSummary, Integer> personalCol;
    @FXML private TableColumn<ReportDepartmentSummary, String> avgDurationCol;

    // Chart
    @FXML private BarChart<String, Number> activityChart;
    @FXML private CategoryAxis dayAxis;
    @FXML private NumberAxis valueAxis;
    private final XYChart.Series<String, Number> officialSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> personalSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> monthlyTotalSeries = new XYChart.Series<>();

    private final ReportsJdbcRepository reportsRepository = new ReportsJdbcRepository();
    private final Popup customPopup = new Popup();
    private boolean isDailyView = true;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupChartBase();
        setupButtons();
        loadReportsFromDatabase();
    }

    private void setupButtons() {
        dailyBtn.setOnAction(e -> switchToDailyView());
        monthlyBtn.setOnAction(e -> switchToMonthlyView());
        printBtn.setOnAction(e -> showPrintOptions());
    }

    private void setupTableColumns() {
        departmentCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDepartment()));
        totalCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getTotalSlips()));
        officialCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getOfficial()));
        personalCol.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getPersonal()));
        avgDurationCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getAvgDuration()));
    }

    private void setupChartBase() {
        officialSeries.setName("Official");
        personalSeries.setName("Personal");
        monthlyTotalSeries.setName("Total Slips");

        valueAxis.setAutoRanging(true);
        valueAxis.setTickUnit(1);
        valueAxis.setMinorTickCount(0);

        // --- DISABLE ANIMATIONS TO FIX CHART OVERLAP BUG ---
        activityChart.setAnimated(false);
        dayAxis.setAnimated(false);
        valueAxis.setAnimated(false);
    }

    private void loadReportsFromDatabase() {
        ReportsStats stats = reportsRepository.getStats();
        totalSlipsLabel.setText(String.valueOf(stats.getTotalSlips()));
        currentlyOutLabel.setText(String.valueOf(stats.getCurrentlyOut()));
        officialLabel.setText(String.valueOf(stats.getOfficial()));
        avgDurationLabel.setText(stats.getAvgDuration());
        reportsTable.setItems(FXCollections.observableArrayList(reportsRepository.findDepartmentSummaries()));

        switchToDailyView(); // Default landing view
    }

    // --- VIEW TOGGLING ---

    private void switchToDailyView() {
        isDailyView = true;
        if (chartTitleLabel != null) chartTitleLabel.setText("DAILY ACTIVITY - THIS WEEK");

        dailyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white;");
        monthlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3;");

        activityChart.getData().clear();
        // Added Sat and Sun here
        dayAxis.setCategories(FXCollections.observableArrayList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        updateDailyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void switchToMonthlyView() {
        isDailyView = false;
        if(chartTitleLabel != null) chartTitleLabel.setText("MONTHLY SUMMARY — YEAR TO DATE");

        monthlyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white;");
        dailyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3;");

        activityChart.getData().clear();
        dayAxis.setCategories(FXCollections.observableArrayList(
                "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"
        ));
        updateMonthlyData();
        activityChart.getData().add(monthlyTotalSeries);
    }

    // --- DATA POPULATION ---

    private void updateDailyData() {
        officialSeries.getData().clear();
        personalSeries.getData().clear();

        List<DailyActivitySummary> dbData = reportsRepository.findWeeklyDailyActivity();
        Map<String, DailyActivitySummary> dataMap = new HashMap<>();
        for (DailyActivitySummary s : dbData) dataMap.put(s.getDay(), s);

        // Added Sat and Sun here
        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        for (String day : days) {
            DailyActivitySummary summary = dataMap.getOrDefault(day, new DailyActivitySummary(day, 0, 0));
            XYChart.Data<String, Number> offData = new XYChart.Data<>(day, summary.getOfficialCount());
            XYChart.Data<String, Number> persData = new XYChart.Data<>(day, summary.getPersonalCount());

            officialSeries.getData().add(offData);
            personalSeries.getData().add(persData);

            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
                attachHoverEffect(persData.getNode(), day, "Official : " + summary.getOfficialCount(), "Personal : " + summary.getPersonalCount());
            });
        }
    }

    private void updateMonthlyData() {
        monthlyTotalSeries.getData().clear();

        List<MonthlyActivitySummary> dbData = reportsRepository.findMonthlyActivity();
        Map<String, Integer> dataMap = new HashMap<>();
        for (MonthlyActivitySummary s : dbData) dataMap.put(s.getMonth(), s.getTotalSlips());

        String[] months = {"Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"};

        for (String month : months) {
            int total = dataMap.getOrDefault(month, 0);
            XYChart.Data<String, Number> monthData = new XYChart.Data<>(month, total);
            monthlyTotalSeries.getData().add(monthData);

            Platform.runLater(() -> {
                attachHoverEffect(monthData.getNode(), month, "Total Slips : " + total, null);
            });
        }
    }

    // --- HOVER LOGIC ---

    private void attachHoverEffect(Node barNode, String titleText, String line1, String line2) {
        if (barNode == null) return;

        barNode.setOnMouseEntered(event -> {
            VBox content = createPopupBox(titleText, line1, line2);
            content.setMouseTransparent(true);
            customPopup.getContent().setAll(content);

            Bounds bounds = barNode.localToScreen(barNode.getBoundsInLocal());
            customPopup.show(barNode.getScene().getWindow(),
                    bounds.getMinX() + (bounds.getWidth() / 2) - 50,
                    bounds.getMinY() - 70);
        });

        barNode.setOnMouseExited(event -> customPopup.hide());
    }

    private VBox createPopupBox(String titleText, String line1Text, String line2Text) {
        VBox box = new VBox(5);
        box.setStyle("-fx-background-color: #ffffff; -fx-padding: 10; -fx-border-color: #cccccc; -fx-background-radius: 5; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 0);");

        Label title = new Label(titleText);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #333333;");
        box.getChildren().add(title);

        Label l1 = new Label(line1Text);
        l1.setStyle("-fx-text-fill: #2196F3;");
        box.getChildren().add(l1);

        if (line2Text != null) {
            Label l2 = new Label(line2Text);
            l2.setStyle("-fx-text-fill: #FF5722;");
            box.getChildren().add(l2);
        }

        return box;
    }

    // --- EXPORT & PRINT LOGIC ---

    private void showPrintOptions() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Export Options");
        alert.setHeaderText("Choose Export Format");
        alert.setContentText("How would you like to save or print this report?");

        ButtonType buttonPdf = new ButtonType("Print / Save as PDF");
        ButtonType buttonCsv = new ButtonType("Export to Excel (CSV)");
        ButtonType buttonCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(buttonPdf, buttonCsv, buttonCancel);

        alert.showAndWait().ifPresent(type -> {
            if (type == buttonPdf) {
                printChartToPdf();
            } else if (type == buttonCsv) {
                exportDataToCsv();
            }
        });
    }

    private void printChartToPdf() {
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job != null) {
            boolean showDialog = job.showPrintDialog(printBtn.getScene().getWindow());

            if (showDialog) {
                // 1. Create a virtual document layout
                VBox printRoot = new VBox(20); // 20px spacing between elements
                printRoot.setStyle("-fx-background-color: white; -fx-padding: 30;");

                // 2. Determine Title based on current view
                String titleText = isDailyView ? "DAILY ACTIVITY - THIS WEEK" : "MONTHLY SUMMARY - YEAR TO DATE";
                Label titleLabel = new Label(titleText);
                titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #333333;");
                printRoot.getChildren().add(titleLabel);

                // 3. Add the Chart Snapshot to the document
                javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                params.setFill(javafx.scene.paint.Color.WHITE);
                javafx.scene.image.WritableImage chartSnapshot = activityChart.snapshot(params, null);
                javafx.scene.image.ImageView chartImage = new javafx.scene.image.ImageView(chartSnapshot);
                printRoot.getChildren().add(chartImage);

                // 4. Build and add the Data Table to the document
                printRoot.getChildren().add(createPrintableTable());

                // 5. Force the layout to render in the background so we can snapshot it
                new javafx.scene.Scene(printRoot);
                printRoot.applyCss();
                printRoot.layout();

                // 6. Snapshot the ENTIRE document (Title + Chart + Table)
                javafx.scene.image.WritableImage fullPageSnapshot = printRoot.snapshot(params, null);
                javafx.scene.image.ImageView printImage = new javafx.scene.image.ImageView(fullPageSnapshot);

                // 7. Scale the full document to fit the printer page
                javafx.print.PageLayout pageLayout = job.getJobSettings().getPageLayout();
                double printableWidth = pageLayout.getPrintableWidth();
                double printableHeight = pageLayout.getPrintableHeight();

                double scaleX = printableWidth / fullPageSnapshot.getWidth();
                double scaleY = printableHeight / fullPageSnapshot.getHeight();
                double scale = Math.min(scaleX, scaleY);

                if (scale < 1.0) {
                    printImage.setFitWidth(fullPageSnapshot.getWidth() * scale);
                    printImage.setFitHeight(fullPageSnapshot.getHeight() * scale);
                    printImage.setPreserveRatio(true);
                }

                // 8. Print the scaled document
                boolean success = job.printPage(printImage);

                if (success) {
                    job.endJob();
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Print Successful");
                    successAlert.setHeaderText(null);
                    successAlert.setContentText("The report was successfully printed / saved as a PDF.");
                    successAlert.showAndWait();
                } else {
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Print Failed");
                    errorAlert.setHeaderText("Could not complete the print job.");
                    errorAlert.setContentText("There was an error communicating with the printer or PDF writer.");
                    errorAlert.showAndWait();
                }
            }
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("No Printer Found");
            alert.setHeaderText(null);
            alert.setContentText("Could not find any installed printers on this system.");
            alert.showAndWait();
        }
    }

    private void exportDataToCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Excel Report (CSV)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));

        // Dynamically name the file based on the current view
        String defaultName = isDailyView ? "Daily_Activity_Report.csv" : "Monthly_Summary_Report.csv";
        fileChooser.setInitialFileName(defaultName);

        File file = fileChooser.showSaveDialog(printBtn.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {

                // --- 1. WRITE THE TITLE AND CHART DATA ---
                if (isDailyView) {
                    writer.println("DAILY ACTIVITY - THIS WEEK");
                    writer.println(); // Empty line for spacing
                    writer.println("Day,Official,Personal"); // Headers

                    // Loop through the chart categories
                    for (String day : dayAxis.getCategories()) {
                        Number offVal = getSeriesValue(officialSeries, day);
                        Number persVal = getSeriesValue(personalSeries, day);
                        writer.println(day + "," + offVal + "," + persVal);
                    }
                } else {
                    writer.println("MONTHLY SUMMARY - YEAR TO DATE");
                    writer.println(); // Empty line for spacing
                    writer.println("Month,Total Slips"); // Headers

                    // Loop through the chart categories
                    for (String month : dayAxis.getCategories()) {
                        Number totalVal = getSeriesValue(monthlyTotalSeries, month);
                        writer.println(month + "," + totalVal);
                    }
                }

                writer.println(); // Empty line for spacing
                writer.println(); // Empty line for spacing

                // --- 2. WRITE THE DEPARTMENT TABLE DATA ---
                writer.println("DEPARTMENT SUMMARY");
                writer.println("Department,Total Slips,Official,Personal,Avg Duration");

                for (ReportDepartmentSummary row : reportsTable.getItems()) {
                    writer.println(
                            row.getDepartment() + "," +
                                    row.getTotalSlips() + "," +
                                    row.getOfficial() + "," +
                                    row.getPersonal() + "," +
                                    row.getAvgDuration()
                    );
                }

                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle("Export Successful");
                successAlert.setHeaderText(null);
                successAlert.setContentText("Excel report saved successfully to:\n" + file.getAbsolutePath());
                successAlert.showAndWait();

            } catch (Exception e) {
                e.printStackTrace();
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Export Failed");
                errorAlert.setHeaderText("Could not save the file");
                errorAlert.setContentText("Please make sure the file is not currently open in Excel and try again.");
                errorAlert.showAndWait();
            }
        }
    }
    // Builds a grid table containing the data from the current chart view
    private javafx.scene.Node createPrintableTable() {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(40);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 10; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-background-color: white;");

        if (isDailyView) {
            // --- DAILY TABLE HEADERS ---
            addTableCell(grid, "Day", 0, 0, true);
            addTableCell(grid, "Official", 1, 0, true);
            addTableCell(grid, "Personal", 2, 0, true);

            // --- DAILY TABLE DATA ---
            int row = 1;
            for (String day : dayAxis.getCategories()) {
                Number offVal = getSeriesValue(officialSeries, day);
                Number persVal = getSeriesValue(personalSeries, day);

                addTableCell(grid, day, 0, row, false);
                addTableCell(grid, offVal.toString(), 1, row, false);
                addTableCell(grid, persVal.toString(), 2, row, false);
                row++;
            }
        } else {
            // --- MONTHLY TABLE HEADERS ---
            addTableCell(grid, "Month", 0, 0, true);
            addTableCell(grid, "Total Slips", 1, 0, true);

            // --- MONTHLY TABLE DATA ---
            int row = 1;
            for (String month : dayAxis.getCategories()) {
                Number totalVal = getSeriesValue(monthlyTotalSeries, month);

                addTableCell(grid, month, 0, row, false);
                addTableCell(grid, totalVal.toString(), 1, row, false);
                row++;
            }
        }

        return grid;
    }

    // Formats individual cells for the printable table
    private void addTableCell(javafx.scene.layout.GridPane grid, String text, int col, int row, boolean isHeader) {
        Label label = new Label(text);
        if (isHeader) {
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: transparent transparent black transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 5 0;");
        } else {
            label.setStyle("-fx-font-size: 13px; -fx-text-fill: #333333;");
        }
        grid.add(label, col, row);
    }

    // Safely extracts a Y-value for a given X-category string
    private Number getSeriesValue(XYChart.Series<String, Number> series, String category) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getXValue().equals(category)) {
                return data.getYValue();
            }
        }
        return 0; // Return 0 if the category has no data
    }
}