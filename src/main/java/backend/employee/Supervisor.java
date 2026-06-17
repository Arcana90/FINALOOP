package backend.employee;

public class Supervisor {
    private final int supervisorId;
    private final String firstName;
    private final String lastName;
    private final String department;
    private final String position;

    public Supervisor(int supervisorId, String firstName, String lastName,
                      String department, String position) {
        this.supervisorId = supervisorId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.department = department;
        this.position = position;
    }

    public int getSupervisorId() {
        return supervisorId;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String getDepartment() {
        return department;
    }

    public String getPosition() {
        return position;
    }
}