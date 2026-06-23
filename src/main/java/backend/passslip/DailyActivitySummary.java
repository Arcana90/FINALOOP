package backend.passslip;

public class DailyActivitySummary {
    private final String day;
    private final int officialCount;
    private final int personalCount;

    public DailyActivitySummary(String day, int officialCount, int personalCount) {
        this.day = day;
        this.officialCount = officialCount;
        this.personalCount = personalCount;
    }

    public String getDay() { return day; }
    public int getOfficialCount() { return officialCount; }
    public int getPersonalCount() { return personalCount; }
}