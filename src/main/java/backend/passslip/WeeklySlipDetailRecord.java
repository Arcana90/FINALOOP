package backend.passslip;

// For the comprehensive Leave Slip details table
public class WeeklySlipDetailRecord {
    private final String slipId;
    private final String name;
    private final String leaveType;
    private final String status;
    private final String date;

    public WeeklySlipDetailRecord(String slipId, String name, String leaveType, String status, String date) {
        this.slipId = slipId;
        this.name = name;
        this.leaveType = leaveType;
        this.status = status;
        this.date = date;
    }

    public String getSlipId() { return slipId; }
    public String getEmployeeName() { return name; }
    public String getLeaveType() { return leaveType; }
    public String getStatus() { return status; }
    public String getTopicDate() { return date; }
}