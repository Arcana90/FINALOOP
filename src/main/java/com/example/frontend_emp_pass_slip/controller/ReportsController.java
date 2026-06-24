package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.ReportsJdbcRepository;
import backend.passslip.ReportsStats;
import backend.passslip.DailyActivitySummary;
import backend.passslip.MonthlyActivitySummary;

import javafx.application.Platform;
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

    // Chart
    @FXML private BarChart<String, Number> activityChart;
    @FXML private CategoryAxis dayAxis;
    @FXML private NumberAxis valueAxis;

    // Series for data views
    private final XYChart.Series<String, Number> officialSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> personalSeries = new XYChart.Series<>();

    private final ReportsJdbcRepository reportsRepository = new ReportsJdbcRepository();
    private final Popup customPopup = new Popup();
    private boolean isDailyView = true;

    @FXML
    public void initialize() {
        setupChartBase();
        setupButtons();
        loadReportsFromDatabase();
    }

    private void setupButtons() {
        dailyBtn.setOnAction(e -> switchToDailyView());
        monthlyBtn.setOnAction(e -> switchToMonthlyView());
        printBtn.setOnAction(e -> showPrintOptions());
    }

    private void setupChartBase() {
        officialSeries.setName("Official");
        personalSeries.setName("Personal");

        valueAxis.setAutoRanging(true);
        valueAxis.setTickUnit(1);
        valueAxis.setMinorTickCount(0);

        // Disable animations to fix overlapping chart bugs
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

        switchToDailyView(); // Default landing view
    }

    // --- VIEW TOGGLING ---

    private void switchToDailyView() {
        isDailyView = true;
        if (chartTitleLabel != null) chartTitleLabel.setText("DAILY ACTIVITY - THIS WEEK");

        dailyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white;");
        monthlyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3;");

        activityChart.getData().clear();
        dayAxis.setCategories(FXCollections.observableArrayList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
        updateDailyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    private void switchToMonthlyView() {
        isDailyView = false;
        if (chartTitleLabel != null) chartTitleLabel.setText("MONTHLY SUMMARY — YEAR TO DATE");

        monthlyBtn.setStyle("-fx-background-color: #2962ff; -fx-text-fill: white;");
        dailyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #333333; -fx-border-color: #cccccc; -fx-border-radius: 3;");

        activityChart.getData().clear();
        dayAxis.setCategories(FXCollections.observableArrayList(
                "Aug", "Sep", "Oct", "Nov", "Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul"
        ));
        updateMonthlyData();
        activityChart.getData().addAll(List.of(officialSeries, personalSeries));
    }

    // --- DATA POPULATION ---

    private void updateDailyData() {
        officialSeries.getData().clear();
        personalSeries.getData().clear();

        List<DailyActivitySummary> dbData = reportsRepository.findWeeklyDailyActivity();
        Map<String, DailyActivitySummary> dataMap = new HashMap<>();
        for (DailyActivitySummary s : dbData) dataMap.put(s.getDay(), s);

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
        officialSeries.getData().clear();
        personalSeries.getData().clear();

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

            officialSeries.getData().add(offData);
            personalSeries.getData().add(persData);

            Platform.runLater(() -> {
                attachHoverEffect(offData.getNode(), month, "Official : " + offVal, "Personal : " + persVal);
                attachHoverEffect(persData.getNode(), month, "Official : " + offVal, "Personal : " + persVal);
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
                VBox printRoot = new VBox(20);
                printRoot.setStyle("-fx-background-color: white; -fx-padding: 30;");

                String titleText = isDailyView ? "DAILY ACTIVITY - THIS WEEK" : "MONTHLY SUMMARY - YEAR TO DATE";
                Label titleLabel = new Label(titleText);
                titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #333333;");
                printRoot.getChildren().add(titleLabel);

                javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
                params.setFill(javafx.scene.paint.Color.WHITE);
                javafx.scene.image.WritableImage chartSnapshot = activityChart.snapshot(params, null);
                javafx.scene.image.ImageView chartImage = new javafx.scene.image.ImageView(chartSnapshot);
                printRoot.getChildren().add(chartImage);

                printRoot.getChildren().add(createPrintableTable());

                new javafx.scene.Scene(printRoot);
                printRoot.applyCss();
                printRoot.layout();

                javafx.scene.image.WritableImage fullPageSnapshot = printRoot.snapshot(params, null);
                javafx.scene.image.ImageView printImage = new javafx.scene.image.ImageView(fullPageSnapshot);

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

        String defaultName = isDailyView ? "Daily_Activity_Report.csv" : "Monthly_Summary_Report.csv";
        fileChooser.setInitialFileName(defaultName);

        File file = fileChooser.showSaveDialog(printBtn.getScene().getWindow());

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {

                String headerTitle = isDailyView ? "DAILY ACTIVITY - THIS WEEK" : "MONTHLY SUMMARY - YEAR TO DATE";
                String categoryLabel = isDailyView ? "Day" : "Month";

                writer.println(headerTitle);
                writer.println();
                writer.println(categoryLabel + ",Official,Personal");

                for (String category : dayAxis.getCategories()) {
                    Number offVal = getSeriesValue(officialSeries, category);
                    Number persVal = getSeriesValue(personalSeries, category);
                    writer.println(category + "," + offVal + "," + persVal);
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

    private javafx.scene.Node createPrintableTable() {
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(40);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 10; -fx-border-color: #cccccc; -fx-border-width: 1; -fx-background-color: white;");

        String categoryHeader = isDailyView ? "Day" : "Month";
        addTableCell(grid, categoryHeader, 0, 0, true);
        addTableCell(grid, "Official", 1, 0, true);
        addTableCell(grid, "Personal", 2, 0, true);

        int row = 1;
        for (String category : dayAxis.getCategories()) {
            Number offVal = getSeriesValue(officialSeries, category);
            Number persVal = getSeriesValue(personalSeries, category);

            addTableCell(grid, category, 0, row, false);
            addTableCell(grid, offVal.toString(), 1, row, false);
            addTableCell(grid, persVal.toString(), 2, row, false);
            row++;
        }

        return grid;
    }

    private void addTableCell(javafx.scene.layout.GridPane grid, String text, int col, int row, boolean isHeader) {
        Label label = new Label(text);
        if (isHeader) {
            label.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-border-color: transparent transparent black transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 0 5 0;");
        } else {
            label.setStyle("-fx-font-size: 13px; -fx-text-fill: #333333;");
        }
        grid.add(label, col, row);
    }

    private Number getSeriesValue(XYChart.Series<String, Number> series, String category) {
        for (XYChart.Data<String, Number> data : series.getData()) {
            if (data.getXValue().equals(category)) {
                return data.getYValue();
            }
        }
        return 0;
    }
}