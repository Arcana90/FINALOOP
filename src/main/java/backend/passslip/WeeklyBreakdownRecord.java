package backend.passslip;

public class WeeklyBreakdownRecord {
    private final String weekName;
    private final int totalSlips;
    private final int approved;
    private final int rejected;
    private final int awol;

    public WeeklyBreakdownRecord(String weekName, int totalSlips, int approved, int rejected, int awol) {
        this.weekName = weekName;
        this.totalSlips = totalSlips;
        this.approved = approved;
        this.rejected = rejected;
        this.awol = awol;
    }

    public String getWeekName() { return weekName; }
    public int getTotalSlips() { return totalSlips; }
    public int getApproved() { return approved; }
    public int getRejected() { return rejected; }
    public int getAwol() { return awol; }
}