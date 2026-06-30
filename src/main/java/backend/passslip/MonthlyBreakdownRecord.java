package backend.passslip;

public class MonthlyBreakdownRecord {
    private final String monthName;
    private final int totalSlips;
    private final int approved;
    private final int rejected;
    private final int awol;
    private final int timeOuts;

    public MonthlyBreakdownRecord(String monthName, int totalSlips, int approved, int rejected, int awol, int timeOuts) {
        this.monthName = monthName;
        this.totalSlips = totalSlips;
        this.approved = approved;
        this.rejected = rejected;
        this.awol = awol;
        this.timeOuts = timeOuts;
    }

    public String getMonthName() { return monthName; }
    public int getTotalSlips() { return totalSlips; }
    public int getApproved() { return approved; }
    public int getRejected() { return rejected; }
    public int getAwol() { return awol; }
    public int getTimeOuts() { return timeOuts; }

    public double getApprovalRate() {
        return totalSlips == 0 ? 0 : ((double) approved / totalSlips) * 100;
    }
}