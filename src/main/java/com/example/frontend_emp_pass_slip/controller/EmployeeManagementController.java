package com.example.frontend_emp_pass_slip.controller;

import backend.employee.Employee;
import backend.employee.EmployeeRepository;
import backend.employee.Supervisor;
import backend.employee.SupervisorRepository;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent; // Added for intercepting the button click
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.util.List;
import java.util.regex.Pattern;

public class EmployeeManagementController {

    private static final Pattern LETTERS_ONLY = Pattern.compile("^[\\p{L}\\s\\-.]+$");
    private static final Pattern ALLOWED_EMAILS = Pattern.compile("^[a-zA-Z0-9._%+-]+@(gmail\\.com|example\\.com)$");
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("^\\d{11}$");

    private static final List<String> DEPARTMENT_OPTIONS = List.of(
            "Administration", "Engineering", "Finance", "Human Resources",
            "Information Technology", "Legal", "Marketing", "Operations",
            "Procurement", "Research & Development", "Sales", "Security"
    );

    @FXML private TextField searchField;
    @FXML private Label statusLabel;
    @FXML private MenuButton filterButton;

    @FXML private TableView<Employee> employeeTable;
    @FXML private TableColumn<Employee, String> employeeIdColumn;
    @FXML private TableColumn<Employee, String> nameColumn;
    @FXML private TableColumn<Employee, String> departmentColumn;
    @FXML private TableColumn<Employee, String> positionColumn;
    @FXML private TableColumn<Employee, String> supervisorColumn;
    @FXML private TableColumn<Employee, String> contactNumberColumn;
    @FXML private TableColumn<Employee, String> emailColumn;
    @FXML private TableColumn<Employee, String> statusColumn;

    private final EmployeeRepository employeeRepository = new EmployeeRepository();
    private final ObservableList<Employee> employees = FXCollections.observableArrayList();
    private final SupervisorRepository supervisorRepository = new SupervisorRepository();

    private FilteredList<Employee> filteredEmployees;
    private String currentStatusFilter = "All";

    @FXML
    private void initialize() {
        setupTableColumns();

        filteredEmployees = new FilteredList<>(employees, employee -> true);
        employeeTable.setItems(filteredEmployees);

        loadEmployeesFromDatabase();
        setupSearch();
    }

    private void setupTableColumns() {
        employeeIdColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getEmployeeId()));
        nameColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getFullName()));
        departmentColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getDepartment()));
        positionColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getPosition()));
        supervisorColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getSupervisorName()));
        contactNumberColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getContactNumber()));
        emailColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getEmail()));
        statusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().getStatus()));
    }

    private void loadEmployeesFromDatabase() {
        employees.setAll(employeeRepository.findAll());
        applyFilter();
    }

    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
    }

    private void applyFilter() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();

        filteredEmployees.setPredicate(employee -> {
            if ("Active".equalsIgnoreCase(currentStatusFilter) && !"Active".equalsIgnoreCase(employee.getStatus())) {
                return false;
            }
            if ("Inactive".equalsIgnoreCase(currentStatusFilter) && !"Inactive".equalsIgnoreCase(employee.getStatus())) {
                return false;
            }

            if (query.isEmpty()) {
                return true;
            }

            return employee.getEmployeeId().toLowerCase().contains(query)
                    || employee.getFullName().toLowerCase().contains(query)
                    || employee.getDepartment().toLowerCase().contains(query)
                    || employee.getPosition().toLowerCase().contains(query)
                    || employee.getSupervisorName().toLowerCase().contains(query)
                    || employee.getEmail().toLowerCase().contains(query)
                    || employee.getStatus().toLowerCase().contains(query);
        });

        long activeCount = employees.stream().filter(e -> "Active".equalsIgnoreCase(e.getStatus())).count();
        if (query.isEmpty() && "All".equalsIgnoreCase(currentStatusFilter)) {
            statusLabel.setText(employees.size() + " total employees (" + activeCount + " active)");
        } else {
            statusLabel.setText(filteredEmployees.size() + " employees shown matching criteria");
        }
        statusLabel.setStyle("");
    }

    @FXML
    private void addEmployee() {
        String generatedId = employeeRepository.generateNextEmployeeId();

        Dialog<Employee> dialog = new Dialog<>();
        dialog.setTitle("Add Employee");
        dialog.setHeaderText("Enter employee information");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField employeeIdField = new TextField(generatedId);
        employeeIdField.setEditable(false);
        employeeIdField.setStyle("-fx-background-color: #f4f4f4;");

        TextField firstNameField = new TextField();
        firstNameField.setPromptText("First name (letters only)");

        TextField lastNameField = new TextField();
        lastNameField.setPromptText("Last name (letters only)");

        ComboBox<String> departmentBox = new ComboBox<>();
        departmentBox.setItems(FXCollections.observableArrayList(DEPARTMENT_OPTIONS));
        departmentBox.setPromptText("Select department");
        departmentBox.setMaxWidth(Double.MAX_VALUE);

        TextField positionField = new TextField();
        positionField.setPromptText("Position (letters only)");

        TextField emailField = new TextField();
        emailField.setPromptText("username@gmail.com or @example.com");

        TextField contactNumberField = new TextField();
        contactNumberField.setPromptText("09123456789 (11 digits)");

        ComboBox<Supervisor> supervisorBox = new ComboBox<>();
        supervisorBox.setItems(FXCollections.observableArrayList(supervisorRepository.findActiveSupervisors()));
        supervisorBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Supervisor supervisor) {
                return supervisor == null ? "" : supervisor.getFullName();
            }

            @Override
            public Supervisor fromString(String string) {
                return null;
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Employee ID:"), 0, 0);
        grid.add(employeeIdField, 1, 0);
        grid.add(new Label("First Name:"), 0, 1);
        grid.add(firstNameField, 1, 1);
        grid.add(new Label("Last Name:"), 0, 2);
        grid.add(lastNameField, 1, 2);
        grid.add(new Label("Department:"), 0, 3);
        grid.add(departmentBox, 1, 3);
        grid.add(new Label("Position:"), 0, 4);
        grid.add(positionField, 1, 4);
        grid.add(new Label("Email:"), 0, 5);
        grid.add(emailField, 1, 5);
        grid.add(new Label("Contact No.:"), 0, 6);
        grid.add(contactNumberField, 1, 6);
        grid.add(new Label("Supervisor:"), 0, 7);
        grid.add(supervisorBox, 1, 7);

        Label hintLabel = new Label("Validation Requirements:\n• Names/Position: Letters only.\n• Contact No: Exactly 11 digits.\n• Email: Must end with @gmail.com or @example.com");
        hintLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        hintLabel.setWrapText(true);
        grid.add(hintLabel, 0, 8, 2, 1);

        dialog.getDialogPane().setContent(grid);

        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        Runnable checkFields = () -> {
            boolean ready = !firstNameField.getText().trim().isEmpty()
                    && !lastNameField.getText().trim().isEmpty()
                    && departmentBox.getValue() != null
                    && !positionField.getText().trim().isEmpty()
                    && !emailField.getText().trim().isEmpty()
                    && !contactNumberField.getText().trim().isEmpty();
            saveButton.setDisable(!ready);
        };

        firstNameField.textProperty().addListener((obs, o, n) -> checkFields.run());
        lastNameField.textProperty().addListener((obs, o, n) -> checkFields.run());
        departmentBox.valueProperty().addListener((obs, o, n) -> checkFields.run());
        positionField.textProperty().addListener((obs, o, n) -> checkFields.run());
        emailField.textProperty().addListener((obs, o, n) -> checkFields.run());
        contactNumberField.textProperty().addListener((obs, o, n) -> checkFields.run());

        // 🔥 MAGIC HAPPENS HERE: Intercept click event BEFORE dialog evaluates closure logic
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String firstName  = firstNameField.getText().trim();
            String lastName   = lastNameField.getText().trim();
            String position   = positionField.getText().trim();
            String email      = emailField.getText().trim();
            String contactNum = contactNumberField.getText().trim();

            String validationError = validateTextFields(firstName, lastName, position, contactNum, email);
            if (validationError != null) {
                showError("Invalid Input", validationError);
                event.consume(); // Consuming the event strictly prevents the dialog from closing!
            }
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton != saveButtonType) {
                return null;
            }

            // Since our Event Filter caught errors earlier, we know data is clean here
            Supervisor selectedSupervisor = supervisorBox.getValue();

            return new Employee(
                    employeeIdField.getText().trim(),
                    firstNameField.getText().trim(),
                    lastNameField.getText().trim(),
                    departmentBox.getValue(),
                    positionField.getText().trim(),
                    emailField.getText().trim(),
                    contactNumberField.getText().trim(),
                    "Active",
                    selectedSupervisor == null ? null : selectedSupervisor.getSupervisorId(),
                    selectedSupervisor == null ? "" : selectedSupervisor.getFullName()
            );
        });

        dialog.showAndWait().ifPresent(employee -> {
            EmployeeRepository.AddEmployeeResult result = employeeRepository.addEmployee(employee);

            switch (result) {
                case SUCCESS -> {
                    loadEmployeesFromDatabase();
                    showInfo("Employee Added", "Employee " + employee.getFullName()
                            + " was added successfully with ID " + employee.getEmployeeId() + ".");
                    statusLabel.setText("Employee added: " + employee.getFullName() + " (" + employee.getEmployeeId() + ")");
                    statusLabel.setStyle("-fx-text-fill: #008000;");
                }
                case DUPLICATE -> {
                    showError("Duplicate Employee Detected", """
                            A duplicate record was found.
                            
                            An employee with the same ID, email, or the same Name + Department + Position combination already exists.
                            
                            Please verify the details and try again.""");
                    statusLabel.setText("Duplicate entry blocked — employee already exists.");
                    statusLabel.setStyle("-fx-text-fill: #cc0000;");
                }
                case FAILED -> {
                    showError("Database Error", "Failed to add employee. Please check the console for details.");
                    statusLabel.setText("Failed to add employee.");
                    statusLabel.setStyle("-fx-text-fill: #cc0000;");
                }
            }
        });
    }

    private String validateTextFields(String firstName, String lastName, String position, String contactNumber, String email) {
        if (!LETTERS_ONLY.matcher(firstName).matches()) {
            return "First Name must contain letters only. Numbers and special characters are not allowed.";
        }
        if (!LETTERS_ONLY.matcher(lastName).matches()) {
            return "Last Name must contain letters only. Numbers and special characters are not allowed.";
        }
        if (!LETTERS_ONLY.matcher(position).matches()) {
            return "Position must contain letters only. Numbers and special characters are not allowed.";
        }
        if (!ELEVEN_DIGITS.matcher(contactNumber).matches()) {
            return "Contact Number must be exactly 11 digits (e.g., 09123456789). Special characters, spaces, or letters are not allowed.";
        }
        if (!ALLOWED_EMAILS.matcher(email).matches()) {
            return "Email is invalid. It must be a complete email address ending strictly in @gmail.com or @example.com.";
        }
        return null;
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

    @FXML
    private void filterAllEmployees() {
        currentStatusFilter = "All";
        filterButton.setText("Filter: All");
        applyFilter();
    }

    @FXML
    private void filterActiveEmployees() {
        currentStatusFilter = "Active";
        filterButton.setText("Filter: Active Only");
        applyFilter();
    }

    @FXML
    private void filterInactiveEmployees() {
        currentStatusFilter = "Inactive";
        filterButton.setText("Filter: Inactive Only");
        applyFilter();
    }

    @FXML
    private void deactivateSelectedEmployee() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showError("No Employee Selected", "Please select an employee from the table first.");
            return;
        }

        if ("Inactive".equalsIgnoreCase(selected.getStatus())) {
            showInfo("Already Inactive", "This employee is already inactive.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Deactivate Employee");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Are you sure you want to deactivate " + selected.getFullName() + "?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean updated = employeeRepository.deactivateEmployee(selected.getEmployeeId());

                if (updated) {
                    loadEmployeesFromDatabase();
                    statusLabel.setText(selected.getFullName() + " has been deactivated.");
                    statusLabel.setStyle("-fx-text-fill: #008000;");
                    showInfo("Employee Deactivated", "Employee status was changed to Inactive.");
                } else {
                    statusLabel.setText("Failed to deactivate employee.");
                    statusLabel.setStyle("-fx-text-fill: #cc0000;");
                    showError("Database Error", "Failed to deactivate employee. Please check the console.");
                }
            }
        });
    }

    @FXML
    private void reactivateSelectedEmployee() {
        Employee selected = employeeTable.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showError("No Employee Selected", "Please select an employee from the table first.");
            return;
        }

        if ("Active".equalsIgnoreCase(selected.getStatus())) {
            showInfo("Already Active", "This employee is already active.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Reactivate Employee");
        confirmation.setHeaderText(null);
        confirmation.setContentText("Reactivate " + selected.getFullName() + " and set status back to Active?");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean updated = employeeRepository.reactivateEmployee(selected.getEmployeeId());

                if (updated) {
                    loadEmployeesFromDatabase();
                    statusLabel.setText(selected.getFullName() + " has been reactivated.");
                    statusLabel.setStyle("-fx-text-fill: #008000;");
                    showInfo("Employee Reactivated", "Employee status was changed to Active.");
                } else {
                    statusLabel.setText("Failed to reactivate employee.");
                    statusLabel.setStyle("-fx-text-fill: #cc0000;");
                    showError("Database Error", "Failed to reactivate employee. Please check the console.");
                }
            }
        });
    }
}