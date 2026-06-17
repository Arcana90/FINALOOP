package backend.passslip;

import java.util.List;

public class DashboardSummary {
    private final int totalEmployees;
    private final int activePassSlips;
    private final int todaysSlips;
    private final int totalRecords;
    private final int officialBusinessToday;
    private final int personalToday;
    private final int returnedToday;
    private final int stillOutToday;
    private final List<DashboardSlipRecord> currentlyOut;
    private final List<DashboardSlipRecord> recentActivity;

    public DashboardSummary(int totalEmployees, int activePassSlips, int todaysSlips,
                            int totalRecords, int officialBusinessToday, int personalToday,
                            int returnedToday, int stillOutToday,
                            List<DashboardSlipRecord> currentlyOut,
                            List<DashboardSlipRecord> recentActivity) {
        this.totalEmployees = totalEmployees;
        this.activePassSlips = activePassSlips;
        this.todaysSlips = todaysSlips;
        this.totalRecords = totalRecords;
        this.officialBusinessToday = officialBusinessToday;
        this.personalToday = personalToday;
        this.returnedToday = returnedToday;
        this.stillOutToday = stillOutToday;
        this.currentlyOut = currentlyOut;
        this.recentActivity = recentActivity;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public int getActivePassSlips() {
        return activePassSlips;
    }

    public int getTodaysSlips() {
        return todaysSlips;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public int getOfficialBusinessToday() {
        return officialBusinessToday;
    }

    public int getPersonalToday() {
        return personalToday;
    }

    public int getReturnedToday() {
        return returnedToday;
    }

    public int getStillOutToday() {
        return stillOutToday;
    }

    public List<DashboardSlipRecord> getCurrentlyOut() {
        return currentlyOut;
    }

    public List<DashboardSlipRecord> getRecentActivity() {
        return recentActivity;
    }
}