package backend.employee;

import backend.db.ConnectionPoolManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmployeeRepository {
    public List<Employee> findAll() {
        List<Employee> employees = new ArrayList<>();
        Connection connection = null;

        // Fetches all employees, including their supervisor names via a LEFT JOIN
        String sql = """
            SELECT
                e.employee_id,
                e.first_name,
                e.last_name,
                e.department,
                e.position,
                e.email,
                e.contact_number,
                e.status::text AS status,
                e.supervisor_id,
                COALESCE(s.first_name || ' ' || s.last_name, '') AS supervisor_name
            FROM employees e
            LEFT JOIN supervisors s ON e.supervisor_id = s.supervisor_id
            ORDER BY e.employee_id
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    employees.add(new Employee(
                            resultSet.getString("employee_id"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("department"),
                            resultSet.getString("position"),
                            resultSet.getString("email"),
                            resultSet.getString("contact_number"),
                            resultSet.getString("status"),
                            (Integer) resultSet.getObject("supervisor_id"),
                            resultSet.getString("supervisor_name")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return employees;
    }
    /**
     * Updates an existing employee record with new validated field entries.
     */
    public boolean updateEmployee(Employee employee) {
        Connection connection = null;
        String sql = """
            UPDATE employees
            SET first_name = ?, 
                last_name = ?, 
                department = ?, 
                position = ?,
                email = ?, 
                contact_number = ?, 
                supervisor_id = ?
            WHERE employee_id = ?
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, employee.getFirstName());
                statement.setString(2, employee.getLastName());
                statement.setString(3, employee.getDepartment());
                statement.setString(4, employee.getPosition());
                statement.setString(5, employee.getEmail());
                statement.setString(6, employee.getContactNumber());

                if (employee.getSupervisorId() == null) {
                    statement.setNull(7, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(7, employee.getSupervisorId());
                }

                statement.setString(8, employee.getEmployeeId());

                return statement.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    /**
     * Permanently deletes an employee record based on data privacy requirements.
     */
    /**
     * Permanently deletes an employee record and forcefully removes any associated
     * pass slips to bypass database foreign key constraints.
     */
    public boolean deleteEmployee(String employeeId) {
        Connection connection = null;

        // 1. Delete dependent pass slips first to satisfy constraints
        String deletePassSlipsSql = "DELETE FROM pass_slips WHERE employee_id = ?";
        // 2. Then delete the actual employee
        String deleteEmployeeSql = "DELETE FROM employees WHERE employee_id = ?";

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            // 🟢 Start a SQL Transaction
            connection.setAutoCommit(false);

            try (PreparedStatement ps1 = connection.prepareStatement(deletePassSlipsSql);
                 PreparedStatement ps2 = connection.prepareStatement(deleteEmployeeSql)) {

                // Execute step 1: Wipe their pass slips
                ps1.setString(1, employeeId);
                ps1.executeUpdate();

                // Execute step 2: Delete the employee
                ps2.setString(1, employeeId);
                int rowsAffected = ps2.executeUpdate();

                // 🟢 Commit the transaction if both succeeded
                connection.commit();

                return rowsAffected > 0;

            } catch (Exception e) {
                // If anything fails, rollback the database to its previous safe state
                connection.rollback();
                e.printStackTrace();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (connection != null) {
                try {
                    // Always restore the connection's default behavior before returning to the pool
                    connection.setAutoCommit(true);
                } catch (SQLException ignored) {}

                ConnectionPoolManager.getInstance().release(connection);
            }
        }
    }

    private static volatile EmployeeRepository instance;

    public EmployeeRepository() {}

    public static EmployeeRepository getInstance() {
        if (instance == null) {
            synchronized (EmployeeRepository.class) {
                if (instance == null) {
                    instance = new EmployeeRepository();
                }
            }
        }
        return instance;
    }

    public List<Employee> findAvailableForIssuance() {
        List<Employee> employees = new ArrayList<>();
        Connection connection = null;

        // This SQL query checks the pass_slips table to hide anyone currently 'Out'
        String sql = """
            SELECT
                e.employee_id,
                e.first_name,
                e.last_name,
                e.department,
                e.position,
                e.email,
                e.contact_number,
                e.status::text AS status,
                e.supervisor_id,
                COALESCE(s.first_name || ' ' || s.last_name, '') AS supervisor_name
            FROM employees e
            LEFT JOIN supervisors s ON e.supervisor_id = s.supervisor_id
            WHERE e.employee_id NOT IN (
                SELECT employee_id FROM pass_slips WHERE status = 'Out'
            )
            ORDER BY e.employee_id
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    employees.add(new Employee(
                            resultSet.getString("employee_id"),
                            resultSet.getString("first_name"),
                            resultSet.getString("last_name"),
                            resultSet.getString("department"),
                            resultSet.getString("position"),
                            resultSet.getString("email"),
                            resultSet.getString("contact_number"),
                            resultSet.getString("status"),
                            (Integer) resultSet.getObject("supervisor_id"),
                            resultSet.getString("supervisor_name")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }

        return employees;
    }

    public AddEmployeeResult addEmployee(Employee employee) {
        Connection connection = null;

        String sql = """
            INSERT INTO employees (
                employee_id, first_name, last_name, department, position,
                email, contact_number, status, supervisor_id
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::employee_status, ?)
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, employee.getEmployeeId());
                statement.setString(2, employee.getFirstName());
                statement.setString(3, employee.getLastName());
                statement.setString(4, employee.getDepartment());
                statement.setString(5, employee.getPosition());
                statement.setString(6, employee.getEmail());
                statement.setString(7, employee.getContactNumber());
                statement.setString(8, employee.getStatus());

                if (employee.getSupervisorId() == null) {
                    statement.setNull(9, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(9, employee.getSupervisorId());
                }

                return statement.executeUpdate() > 0
                        ? AddEmployeeResult.SUCCESS
                        : AddEmployeeResult.FAILED;
            }
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return AddEmployeeResult.DUPLICATE;
            }

            e.printStackTrace();
            return AddEmployeeResult.FAILED;
        } catch (Exception e) {
            e.printStackTrace();
            return AddEmployeeResult.FAILED;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    public enum AddEmployeeResult {
        SUCCESS,
        DUPLICATE,
        FAILED
    }
    private static final String SQL_GET_LAST_ID =
            "SELECT employee_id FROM employees ORDER BY employee_id DESC LIMIT 1";
    public boolean deactivateEmployee(String employeeId) {
        return updateUiEmployeeStatus(employeeId, "Inactive");
    }

    public boolean reactivateEmployee(String employeeId) {
        return updateUiEmployeeStatus(employeeId, "Active");
    }
    /**
     * Fetches the highest current Employee ID sequence, increments it,
     * and formats it cleanly (e.g., "EMP-1001" -> "EMP-1002").
     */
    public String generateNextEmployeeId() {
        Connection connection = null;

        // FIX: Substring the ID starting at character 5, cast it to an INTEGER, and sort numerically
        String sql = """
            SELECT employee_id 
            FROM employees 
            WHERE employee_id LIKE 'EMP-%' 
            ORDER BY CAST(SUBSTRING(employee_id FROM 5) AS INTEGER) DESC 
            LIMIT 1
            """;

        try {
            connection = backend.db.ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    String highestId = resultSet.getString("employee_id"); // e.g., "EMP-0010"

                    // Extract the numbers after "EMP-"
                    String numericPart = highestId.substring(4);
                    int nextNumber = Integer.parseInt(numericPart) + 1; // Correctly advances 10 -> 11

                    // Format with 4-digit leading zeros padding
                    return String.format("EMP-%04d", nextNumber);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null) {
                backend.db.ConnectionPoolManager.getInstance().release(connection);
            }
        }

        // Default fallback if table is empty
        return "EMP-0001";
    }
    public void insert(EmployeeDTO dto) {
        if (existsById(dto.getEmployeeId())) {
            throw new RepositoryException(
                    "Employee ID '" + dto.getEmployeeId() + "' already exists.");
        }

        String sql = """
            INSERT INTO employees (employee_id, first_name, last_name, department, status)
            VALUES (?, ?, ?, ?, ?)
            """;

        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, dto.getEmployeeId());
                statement.setString(2, dto.getFirstName());
                statement.setString(3, dto.getLastName());
                statement.setString(4, dto.getDepartment());
                statement.setString(5, dto.getStatus().name());

                if (statement.executeUpdate() != 1) {
                    throw new RepositoryException("INSERT did not affect exactly 1 row.");
                }
            }
        });
    }

    public Optional<EmployeeDTO> findById(String employeeId) {
        String sql = """
            SELECT employee_id, first_name, last_name, department, status, archived,
                   created_at, updated_at
            FROM employees
            WHERE employee_id = ? AND archived = 0
            """;

        return withConnectionResult(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, employeeId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next()
                            ? Optional.of(mapDtoRow(resultSet))
                            : Optional.empty();
                }
            }
        });
    }

    public List<EmployeeDTO> findAllActive() {
        String sql = """
            SELECT employee_id, first_name, last_name, department, status, archived,
                   created_at, updated_at
            FROM employees
            WHERE archived = 0
            ORDER BY last_name, first_name
            """;

        return withConnectionResult(connection -> {
            List<EmployeeDTO> employees = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    employees.add(mapDtoRow(resultSet));
                }
            }
            return employees;
        });
    }

    public List<EmployeeDTO> findByStatus(EmployeeStatus status) {
        String sql = """
            SELECT employee_id, first_name, last_name, department, status, archived,
                   created_at, updated_at
            FROM employees
            WHERE status = ? AND archived = 0
            ORDER BY last_name, first_name
            """;

        return withConnectionResult(connection -> {
            List<EmployeeDTO> employees = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        employees.add(mapDtoRow(resultSet));
                    }
                }
            }
            return employees;
        });
    }

    public void update(EmployeeDTO dto) {
        String sql = """
            UPDATE employees
            SET first_name = ?, last_name = ?, department = ?, status = ?,
                updated_at = datetime('now')
            WHERE employee_id = ? AND archived = 0
            """;

        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, dto.getFirstName());
                statement.setString(2, dto.getLastName());
                statement.setString(3, dto.getDepartment());
                statement.setString(4, dto.getStatus().name());
                statement.setString(5, dto.getEmployeeId());

                if (statement.executeUpdate() == 0) {
                    throw new RepositoryException(
                            "No active employee found with ID '" + dto.getEmployeeId() + "'.");
                }
            }
        });
    }

    public void updateStatus(String employeeId, EmployeeStatus newStatus) {
        String sql = """
            UPDATE employees
            SET status = ?, updated_at = datetime('now')
            WHERE employee_id = ? AND archived = 0
            """;

        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, newStatus.name());
                statement.setString(2, employeeId);

                if (statement.executeUpdate() == 0) {
                    throw new RepositoryException(
                            "No active employee found with ID '" + employeeId + "'.");
                }
            }
        });
    }

    public void archive(String employeeId) {
        String sql = """
            UPDATE employees
            SET archived = 1, updated_at = datetime('now')
            WHERE employee_id = ? AND archived = 0
            """;

        withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, employeeId);

                if (statement.executeUpdate() == 0) {
                    throw new RepositoryException(
                            "No active employee found with ID '" + employeeId + "'.");
                }
            }
        });
    }

    public boolean existsById(String employeeId) {
        String sql = "SELECT COUNT(1) FROM employees WHERE employee_id = ? AND archived = 0";

        return withConnectionResult(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, employeeId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next() && resultSet.getInt(1) > 0;
                }
            }
        });
    }

    private boolean updateUiEmployeeStatus(String employeeId, String status) {
        Connection connection = null;

        String sql = """
            UPDATE employees
            SET status = ?::employee_status
            WHERE employee_id = ?
            """;

        try {
            connection = ConnectionPoolManager.getInstance().acquire();

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status);
                statement.setString(2, employeeId);
                return statement.executeUpdate() > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    private EmployeeDTO mapDtoRow(ResultSet resultSet) throws SQLException {
        return new EmployeeDTO(
                resultSet.getString("employee_id"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("department"),
                EmployeeStatus.fromString(resultSet.getString("status")),
                resultSet.getInt("archived") == 1,
                parseDateTime(resultSet.getString("created_at")),
                parseDateTime(resultSet.getString("updated_at"))
        );
    }

    private LocalDateTime parseDateTime(String value) {
        return value == null ? null : LocalDateTime.parse(value.replace(" ", "T"));
    }

    @FunctionalInterface
    private interface ConnectionAction {
        void execute(Connection connection) throws SQLException, InterruptedException;
    }

    @FunctionalInterface
    private interface ConnectionFunction<T> {
        T execute(Connection connection) throws SQLException, InterruptedException;
    }

    private void withConnection(ConnectionAction action) {
        Connection connection = null;
        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            action.execute(connection);
        } catch (RepositoryException e) {
            throw e;
        } catch (SQLException | InterruptedException e) {
            throw new RepositoryException("Database error: " + e.getMessage(), e);
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }

    private <T> T withConnectionResult(ConnectionFunction<T> function) {
        Connection connection = null;
        try {
            connection = ConnectionPoolManager.getInstance().acquire();
            return function.execute(connection);
        } catch (RepositoryException e) {
            throw e;
        } catch (SQLException | InterruptedException e) {
            throw new RepositoryException("Database error: " + e.getMessage(), e);
        } finally {
            ConnectionPoolManager.getInstance().release(connection);
        }
    }
}
