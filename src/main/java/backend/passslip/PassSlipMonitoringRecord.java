package backend.passslip;

public class PassSlipMonitoringRecord {
    private final int passSlipId;
    private final String slipNo;
    private final String employeeId;
    private final String name;
    private final String department;
    private final String date;
    private final String timeRequested;
    private final String expectedTimeOut; // 🟢 Added
    private final String expectedTimeIn;  // 🟢 Added
    private final String timeOut;         // Actual out
    private final String timeIn;          // Actual in
    private final String duration;
    private final String reasonForLeaving; // 🟢 Set via constructor now
    private final String status;

    public PassSlipMonitoringRecord(int passSlipId, String slipNo, String employeeId,
                                    String name, String department, String date,
                                    String timeRequested, String expectedTimeOut, String expectedTimeIn,
                                    String timeOut, String timeIn, String duration,
                                    String reasonForLeaving, String status) {
        this.passSlipId = passSlipId;
        this.slipNo = slipNo;
        this.employeeId = employeeId;
        this.name = name;
        this.department = department;
        this.date = date;
        this.timeRequested = timeRequested;
        this.expectedTimeOut = expectedTimeOut;
        this.expectedTimeIn = expectedTimeIn;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.duration = duration;
        this.reasonForLeaving = reasonForLeaving;
        this.status = status;
    }

    public String getFullName() {
        return this.name;
    }

    // 🟢 Extract Destination safely from string formatting
    public String getDestination() {
        // 1. Safety check first to prevent NullPointerExceptions
        if (this.reasonForLeaving == null || this.reasonForLeaving.isBlank() || this.reasonForLeaving.equals("-")) {
            return "N/A";
        }

        // 2. Safely parse the string
        if (this.reasonForLeaving.contains("|")) {
            String[] parts = this.reasonForLeaving.split("\\|");

            // Loop through to find the actual Destination part, ignoring where it sits in the array
            for (String part : parts) {
                String p = part.trim();
                if (p.startsWith("Destination:")) {
                    return p.replaceFirst("Destination:", "").trim();
                }
            }
        }
        return "N/A";
    }

    // 🟢 Extract Reason safely from string formatting
    public String getReason() {
        if (this.reasonForLeaving == null || this.reasonForLeaving.isBlank() || this.reasonForLeaving.equals("-")) return "N/A";
        if (this.reasonForLeaving.contains("|")) {
            String[] parts = this.reasonForLeaving.split("\\|");
            if (parts.length > 1) {
                return parts[1].replace("Reason:", "").trim();
            }
        }
        return this.reasonForLeaving; // Fallback to raw text if it doesn't contain a pipe
    }

    // 🟢 Directly pull cleaner values for your modal windows
    public String getEstimatedOut() {
        return (expectedTimeOut == null || expectedTimeOut.equals("-")) ? "N/A" : expectedTimeOut;
    }

    public String getEstimatedIn() {
        return (expectedTimeIn == null || expectedTimeIn.equals("-")) ? "N/A" : expectedTimeIn;
    }

    // --- STANDARD GETTERS ---
    public int getPassSlipId() { return passSlipId; }
    public String getSlipNo() { return slipNo; }
    public String getEmployeeId() { return employeeId; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public String getDate() { return date; }
    public String getTimeRequested() { return timeRequested; }
    public String getExpectedTimeOut() { return expectedTimeOut; }
    public String getExpectedTimeIn() { return expectedTimeIn; }
    public String getTimeOut() { return timeOut; }
    public String getTimeIn() { return timeIn; }
    public String getDuration() { return duration; }
    public String getReasonForLeaving() { return reasonForLeaving; }
    public String getStatus() { return status; }
}