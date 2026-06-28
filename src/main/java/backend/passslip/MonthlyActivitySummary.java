package backend.passslip;

public class MonthlyActivitySummary {
    private final String monthName;
    private final int totalSlips;
    private final int officialCount;
    private final int personalCount;
    private final int emergencyCount;

    public MonthlyActivitySummary(String monthName, int totalSlips, int officialCount, int personalCount, int emergencyCount) {
        this.monthName = monthName;
        this.totalSlips = totalSlips;
        this.officialCount = officialCount;
        this.personalCount = personalCount;
        this.emergencyCount = emergencyCount;
    }

    public String getMonthName() { return monthName; }
    public int getTotalSlips() { return totalSlips; }
    public int getOfficialCount() { return officialCount; }
    public int getPersonalCount() { return personalCount; }
    public int getEmergencyCount() { return emergencyCount; }
}