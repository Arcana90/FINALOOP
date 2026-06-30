package backend.passslip;

public class AggregatedReportData {
    private String periodLabel;
    private int totalSlips;
    private int officialCount;
    private int personalCount;
    private int emergencyCount;
    private int approvedCount;
    private int pendingCount;
    private int rejectedCount;
    private int awolCount;

    // Full constructor for SQL mapping
    public AggregatedReportData(String periodLabel, int totalSlips, int officialCount, int personalCount,
                                int emergencyCount, int approvedCount, int pendingCount,
                                int rejectedCount, int awolCount) {
        this.periodLabel = periodLabel;
        this.totalSlips = totalSlips;
        this.officialCount = officialCount;
        this.personalCount = personalCount;
        this.emergencyCount = emergencyCount;
        this.approvedCount = approvedCount;
        this.pendingCount = pendingCount;
        this.rejectedCount = rejectedCount;
        this.awolCount = awolCount;
    }

    public double getApprovalRate() {
        if (totalSlips == 0) return 0.0;
        return ((double) approvedCount / totalSlips) * 100;
    }

    public String getPeriodLabel() { return periodLabel; }
    public int getTotalSlips() { return totalSlips; }
    public int getOfficialCount() { return officialCount; }
    public int getPersonalCount() { return personalCount; }
    public int getEmergencyCount() { return emergencyCount; }
    public int getApprovedCount() { return approvedCount; }
    public int getPendingCount() { return pendingCount; }
    public int getRejectedCount() { return rejectedCount; }
    public int getAwolCount() { return awolCount; }
}