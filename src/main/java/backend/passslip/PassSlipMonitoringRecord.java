package backend.passslip;

public class PassSlipMonitoringRecord {
    private final int passSlipId;
    private final String slipNo;
    private final String employeeId;
    private final String name;
    private final String department;
    private final String date;
    private final String timeRequested; // NEW FIELD
    private final String timeOut;
    private final String timeIn;
    private final String duration;
    private final String type;
    private final String status;

    public PassSlipMonitoringRecord(int passSlipId, String slipNo, String employeeId,
                                    String name, String department, String date,
                                    String timeRequested, String timeOut, String timeIn,
                                    String duration, String type, String status) {
        this.passSlipId = passSlipId;
        this.slipNo = slipNo;
        this.employeeId = employeeId;
        this.name = name;
        this.department = department;
        this.date = date;
        this.timeRequested = timeRequested;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.duration = duration;
        this.type = type;
        this.status = status;
    }

    public String getTimeRequested() { return timeRequested; }

    public int getPassSlipId() {
        return passSlipId;
    }

    public String getSlipNo() {
        return slipNo;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public String getDepartment() {
        return department;
    }

    public String getDate() {
        return date;
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

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }
}