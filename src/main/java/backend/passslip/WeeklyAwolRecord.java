package backend.passslip;

public class WeeklyAwolRecord {
    private final String employeeId;
    private final String name;
    private final String department;
    private final String dateIssued;

    public WeeklyAwolRecord(String employeeId, String name, String department, String dateIssued) {
        this.employeeId = employeeId;
        this.name = name;
        this.department = department;
        this.dateIssued = dateIssued;
    }

    public String getEmployeeId() { return employeeId; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public String getDateIssued() { return dateIssued; }
}