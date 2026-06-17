package backend.passslip;

public class DashboardSlipRecord {
    private final int passSlipId;
    private final String employeeName;
    private final String department;
    private final String timeOut;
    private final String timeIn;
    private final String duration;
    private final String status;

    public DashboardSlipRecord(int passSlipId, String employeeName, String department,
                               String timeOut, String timeIn, String duration, String status) {
        this.passSlipId = passSlipId;
        this.employeeName = employeeName;
        this.department = department;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.duration = duration;
        this.status = status;
    }

    public int getPassSlipId() {
        return passSlipId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDepartment() {
        return department;
    }

    public String getTimeOut() {
        return timeOut;
    }

    public String getTimeIn() {
        return timeIn;
    }

    public String getDuration() {
        return duration;
    }

    public String getStatus() {
        return status;
    }
}