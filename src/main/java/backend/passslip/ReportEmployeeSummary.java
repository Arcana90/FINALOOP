package backend.passslip;

public class ReportEmployeeSummary {
    private final String employeeId;
    private final String employeeName;
    private final int personalCount;
    private final int officialCount;
    private final int totalCount;

    public ReportEmployeeSummary(String employeeId, String employeeName, int personalCount, int officialCount) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.personalCount = personalCount;
        this.officialCount = officialCount;
        this.totalCount = personalCount + officialCount;
    }

    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public int getPersonalCount() { return personalCount; }
    public int getOfficialCount() { return officialCount; }
    public int getTotalCount() { return totalCount; }
}