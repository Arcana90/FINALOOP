package backend.passslip;

public class WeeklyAwolRecord {
    private final String employeeId;
    private final String name;
    private final String department;
    private final String date;

    public WeeklyAwolRecord(String employeeId, String name, String department, String date) {
        this.employeeId = employeeId;
        this.name = name;
        this.department = department;
        this.date = date;
    }

    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return name; }
    public String getDepartment() { return department; }
    public String getDate() { return date; } // The getter is .getDate()
}