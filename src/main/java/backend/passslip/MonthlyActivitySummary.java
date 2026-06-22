package backend.passslip;

public class MonthlyActivitySummary {

    private final String month;
    private final int totalSlips;

    public MonthlyActivitySummary(String month, int totalSlips) {
        this.month = month;
        this.totalSlips = totalSlips;
    }

    public String getMonth() {
        return month;
    }

    public int getTotalSlips() {
        return totalSlips;
    }
}