package backend.passslip;

public class EmployeePassSlipDetail {
    private final String employeeId;
    private final String employeeName;
    private final String typeOfPass;
    private final String destination;
    private final String reason;
    private final String timeOut;
    private final String timeIn;
    private final String expectedTime;
    private final String status;

    public EmployeePassSlipDetail(String employeeId, String employeeName, String typeOfPass,
                                  String destination, String reason, String timeOut,
                                  String timeIn, String expectedTime, String status) {
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.typeOfPass = typeOfPass;
        this.destination = destination;
        this.reason = reason;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.expectedTime = expectedTime;
        this.status = status;
    }

    // Getters
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getTypeOfPass() { return typeOfPass; }
    public String getDestination() { return destination; }
    public String getReason() { return reason; }
    public String getTimeOut() { return timeOut; }
    public String getTimeIn() { return timeIn; }
    public String getExpectedTime() { return expectedTime; }
    public String getStatus() { return status; }
}