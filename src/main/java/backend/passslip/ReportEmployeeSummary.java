package backend.passslip;

public class ReportEmployeeSummary {
    private final String employeeId;
    private final String employeeName;
    private final int personalCount;
    private final int officialCount;
    private final int emergencyCount; // 🟢 Add this
    private final int totalCount;
    private final int approvedCount;
    private final int canceledCount;
    private final int rejectedCount;
    private final int awolCount;

    public ReportEmployeeSummary(String employeeId, String employeeName, int personalCount, int officialCount,
                                 int emergencyCount, // 🟢 Add this
                                 int approvedCount, int canceledCount, int rejectedCount, int awolCount) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.personalCount = personalCount;
        this.officialCount = officialCount;
        this.emergencyCount = emergencyCount; // 🟢 Add this
        this.totalCount = personalCount + officialCount + emergencyCount; // 🟢 Update total
        this.approvedCount = approvedCount;
        this.canceledCount = canceledCount;
        this.rejectedCount = rejectedCount;
        this.awolCount = awolCount;
    }

    // Getters
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public int getPersonalCount() { return personalCount; }
    public int getOfficialCount() { return officialCount; }
    public int getEmergencyCount() { return emergencyCount; } // 🟢 Add this
    public int getTotalCount() { return totalCount; }
    public int getApprovedCount() { return approvedCount; }
    public int getCanceledCount() { return canceledCount; }
    public int getRejectedCount() { return rejectedCount; }
    public int getAwolCount() { return awolCount; }
}