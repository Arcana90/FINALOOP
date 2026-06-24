package backend.passslip;

public class MonthlyActivitySummary {

    private final String month;
    private final int totalSlips;
    private final int officialCount;
    private final int personalCount;

    public MonthlyActivitySummary(String month, int totalSlips, int officialCount, int personalCount) {
        this.month = month;
        this.totalSlips = totalSlips;
        this.officialCount = officialCount;
        this.personalCount = personalCount;
    }

    public String getMonth() {
        return month;
    }

    public int getTotalSlips() {
        return totalSlips;
    }

    public int getOfficialCount() {
        return officialCount;
    }

    public int getPersonalCount() {
        return personalCount;
    }
}