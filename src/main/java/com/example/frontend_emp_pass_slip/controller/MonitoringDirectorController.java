package com.example.frontend_emp_pass_slip.controller;

import backend.passslip.PassSlipJdbcRepository; // Assumes your mapping record object lives here
import backend.passslip.PassSlipMonitoringRecord;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;
import backend.passslip.MonitoringJdbcRepository;
import backend.passslip.PassSlipMonitoringRecord;

import java.io.IOException;
import java.util.List;

public class MonitoringDirectorController {

    @FXML private Label awaitingCountLabel;
    @FXML private TableView<PassSlipMonitoringRecord> awaitingTable;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> awaitingSlipCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> awaitingEmpIdCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> awaitingNameCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, Void> awaitingActionCol;

    @FXML private Label resolvedCountLabel;
    @FXML private TableView<PassSlipMonitoringRecord> resolvedTable;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> resolvedSlipCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> resolvedEmpIdCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> resolvedNameCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, String> resolvedStatusCol;
    @FXML private TableColumn<PassSlipMonitoringRecord, Void> resolvedActionCol;

    private final MonitoringJdbcRepository repository = new MonitoringJdbcRepository();
    @FXML
    private void initialize() {
        configureColumnFactories();
        refreshDashboardData();
    }

    private void configureColumnFactories() {
        // Awaiting Approval mapping
        awaitingSlipCol.setCellValueFactory(new PropertyValueFactory<>("slipNo"));
        awaitingEmpIdCol.setCellValueFactory(new PropertyValueFactory<>("employeeId"));
        awaitingNameCol.setCellValueFactory(new PropertyValueFactory<>("name")); // 🟢 Changed from "fullName" to "name"
        addButtonToTable(awaitingActionCol);

        // Resolved mapping
        resolvedSlipCol.setCellValueFactory(new PropertyValueFactory<>("slipNo"));
        resolvedEmpIdCol.setCellValueFactory(new PropertyValueFactory<>("employeeId"));
        resolvedNameCol.setCellValueFactory(new PropertyValueFactory<>("name")); // 🟢 Changed from "fullName" to "name"
        resolvedStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Add dynamic CSS styling badges to the Status column
        resolvedStatusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    Label badge = new Label(item.toUpperCase());
                    badge.setPrefWidth(100);
                    badge.setStyle("-fx-alignment: center; -fx-padding: 3 8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 12;");

                    if ("APPROVED".equalsIgnoreCase(item)) {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #e6f4ea; -fx-text-fill: #137333;");
                    } else {
                        badge.setStyle(badge.getStyle() + "-fx-background-color: #fce8e6; -fx-text-fill: #c5221f;");
                    }
                    setGraphic(badge);
                }
            }
        });
        addButtonToTable(resolvedActionCol);
    }

    public void refreshDashboardData() {
        List<PassSlipMonitoringRecord> activeDataset = repository.getAllMonitoringRecords();

        // 🟢 CHANGE "FOR_APPROVAL" TO "For Approval"
        List<PassSlipMonitoringRecord> pendingGroup = activeDataset.stream()
                .filter(r -> "For Approval".equalsIgnoreCase(r.getStatus()))
                .toList();

        List<PassSlipMonitoringRecord> resolvedGroup = activeDataset.stream()
                .filter(r -> !"For Approval".equalsIgnoreCase(r.getStatus()))
                .toList();

        awaitingTable.setItems(FXCollections.observableArrayList(pendingGroup));
        resolvedTable.setItems(FXCollections.observableArrayList(resolvedGroup));

        awaitingCountLabel.setText("AWAITING APPROVAL — " + pendingGroup.size());
        resolvedCountLabel.setText("RESOLVED — " + resolvedGroup.size());
    }

    private void addButtonToTable(TableColumn<PassSlipMonitoringRecord, Void> column) {
        column.setCellFactory(new Callback<>() {
            @Override
            public TableCell<PassSlipMonitoringRecord, Void> call(final TableColumn<PassSlipMonitoringRecord, Void> param) {
                return new TableCell<>() {
                    private final Button btn = new Button("👁 View");
                    {
                        btn.setStyle("-fx-background-color: transparent; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-text-fill: #0f172a; -fx-cursor: hand; -fx-font-size: 12px;");
                        btn.setOnAction(event -> {
                            PassSlipMonitoringRecord targetRow = getTableView().getItems().get(getIndex());
                            showDetailModal(targetRow);
                        });
                    }

                    @Override
                    protected void updateItem(Void item, boolean empty) {
                        super.updateItem(item, empty);
                        setGraphic(empty ? null : btn);
                    }
                };
            }
        });
    }

    private void showDetailModal(PassSlipMonitoringRecord record) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/frontend_emp_pass_slip/view/PassSlipDetailModal.fxml"));
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.UTILITY);
            dialogStage.setTitle("Review Pass Slip Authorization");

            dialogStage.setScene(new Scene(loader.load()));

            // Pass raw record fields down directly to modal window controller
            PassSlipDetailModalController structuralController = loader.getController();
            structuralController.setPassSlipData(record, this, dialogStage);

            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}