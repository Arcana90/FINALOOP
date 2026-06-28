package backend.passslip;

public class DailyActivitySummary {
    private final String dayName;
    private final int officialCount;
    private final int personalCount;
    private final int emergencyCount;

    public DailyActivitySummary(String dayName, int officialCount, int personalCount, int emergencyCount) {
        this.dayName = dayName;
        this.officialCount = officialCount;
        this.personalCount = personalCount;
        this.emergencyCount = emergencyCount;
    }

    public String getDayName() { return dayName; }
    public int getOfficialCount() { return officialCount; }
    public int getPersonalCount() { return personalCount; }
    public int getEmergencyCount() { return emergencyCount; }
}