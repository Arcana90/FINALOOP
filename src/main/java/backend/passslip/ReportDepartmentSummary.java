package backend.passslip;

public class ReportDepartmentSummary {
    private final String department;
    private final int totalSlips;
    private final int official;
    private final int personal;
    private final String avgDuration;

    public ReportDepartmentSummary(String department, int totalSlips, int official,
                                   int personal, String avgDuration) {
        this.department = department;
        this.totalSlips = totalSlips;
        this.official = official;
        this.personal = personal;
        this.avgDuration = avgDuration;
    }

    public String getDepartment() {
        return department;
    }

    public int getTotalSlips() {
        return totalSlips;
    }

    public int getOfficial() {
        return official;
    }

    public int getPersonal() {
        return personal;
    }

    public String getAvgDuration() {
        return avgDuration;
    }
}