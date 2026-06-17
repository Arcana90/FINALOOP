package backend.employee;

public class Employee {
    private final String employeeId;
    private final String firstName;
    private final String lastName;
    private final String department;
    private final String position;
    private final String email;
    private final String contactNumber;
    private final String status;
    private final String supervisorName;
    private final Integer supervisorId;

    public Employee(String employeeId, String firstName, String lastName,
                    String department, String position, String email,
                    String contactNumber, String status,
                    Integer supervisorId, String supervisorName) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.department = department;
        this.position = position;
        this.email = email;
        this.contactNumber = contactNumber;
        this.status = status;
        this.supervisorId = supervisorId;
        this.supervisorName = supervisorName;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getDepartment() {
        return department;
    }

    public String getPosition() {
        return position;
    }

    public Integer getSupervisorId() {
        return supervisorId;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public String getEmail() {
        return email;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}