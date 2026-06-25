package backend.passslip;

public record EmployeePassSlipDetail(
        String dateIssued,
        String employeeId,
        String employeeName,
        String typeOfPass,
        String destination,
        String reason,
        String timeOut,
        String timeIn,
        String status
) {
    // Getters for standard bean/property compatibility
    public String getDateIssued() { return dateIssued; }
    public String getTypeOfPass() { return typeOfPass; }
    public String getDestination() { return destination; }
    public String getReason() { return reason; }
    public String getTimeOut() { return timeOut; }
    public String getTimeIn() { return timeIn; }
    public String getStatus() { return status; }
}