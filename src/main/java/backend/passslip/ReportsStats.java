package backend.passslip;

public class ReportsStats {
    private final int totalSlips;
    private final int currentlyOut;
    private final int official;
    private final String avgDuration;

    public ReportsStats(int totalSlips, int currentlyOut, int official, String avgDuration) {
        this.totalSlips = totalSlips;
        this.currentlyOut = currentlyOut;
        this.official = official;
        this.avgDuration = avgDuration;
    }

    public int getTotalSlips() {
        return totalSlips;
    }

    public int getCurrentlyOut() {
        return currentlyOut;
    }

    public int getOfficial() {
        return official;
    }

    public String getAvgDuration() {
        return avgDuration;
    }
}