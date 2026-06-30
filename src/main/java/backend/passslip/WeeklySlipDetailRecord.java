package backend.passslip;

public class WeeklySlipDetailRecord {
    private final String slipId;
    private final String name;
    private final String leaveType;
    private final String status;
    private final String dateIssued;

    public WeeklySlipDetailRecord(String slipId, String name, String leaveType, String status, String dateIssued) {
        this.slipId = slipId;
        this.name = name;
        this.leaveType = leaveType;
        this.status = status;
        this.dateIssued = dateIssued;
    }

    public String getSlipId() { return slipId; }
    public String getName() { return name; }
    public String getLeaveType() { return leaveType; }
    public String getStatus() { return status; }
    public String getDateIssued() { return dateIssued; }
}