package com.example.frontend_emp_pass_slip.model;

public class ReportSummary {
    private String department;
    private int totalSlips;
    private int official;
    private int personal;
    private String avgDuration;

    public ReportSummary(String department, int totalSlips, int official, int personal, String avgDuration) {
        this.department = department;
        this.totalSlips = totalSlips;
        this.official = official;
        this.personal = personal;
        this.avgDuration = avgDuration;
    }

    public String getDepartment() { return department; }
    public int getTotalSlips() { return totalSlips; }
    public int getOfficial() { return official; }
    public int getPersonal() { return personal; }
    public String getAvgDuration() { return avgDuration; }
}
