package backend.passslip;

import java.time.LocalDateTime;

/**
 * Data Transfer Object carrying the full state of a Pass Slip record.
 *
 * <p>Used to ferry pass slip data between layers (repository -> service -> controller -> UI)
 * without exposing domain model internals. Fields are intentionally mutable to allow
 * progressive assembly during the issuance and return workflows.</p>
 */
public class PassSlipDTO {

    private String slipId;
    private String employeeId;
    private String destination;
    private String reason;
    private LocalDateTime timeOut;
    private LocalDateTime timeIn;
    private String totalDuration;
    private String status;

    public PassSlipDTO() {}

    /**
     * Full constructor for assembling a complete DTO from a repository query result.
     *
     * @param slipId        The unique pass slip identifier.
     * @param employeeId    The employee this slip belongs to.
     * @param destination   The declared destination of the outbound trip.
     * @param reason        The stated reason for leaving.
     * @param timeOut       Timestamp when the slip was issued and the employee left.
     * @param timeIn        Timestamp when the employee returned. Null if still OUT.
     * @param totalDuration Elapsed duration in "Xh Ym" format. Null if still OUT.
     * @param status        Current lifecycle status string matching {@link backend.employee.EmployeeStatus}.
     */
    public PassSlipDTO(String slipId, String employeeId, String destination, String reason,
                       LocalDateTime timeOut, LocalDateTime timeIn,
                       String totalDuration, String status) {
        this.slipId = slipId;
        this.employeeId = employeeId;
        this.destination = destination;
        this.reason = reason;
        this.timeOut = timeOut;
        this.timeIn = timeIn;
        this.totalDuration = totalDuration;
        this.status = status;
    }

    public String getSlipId() {
        return slipId;
    }

    public void setSlipId(String slipId) {
        this.slipId = slipId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(LocalDateTime timeOut) {
        this.timeOut = timeOut;
    }

    public LocalDateTime getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(LocalDateTime timeIn) {
        this.timeIn = timeIn;
    }

    public String getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(String totalDuration) {
        this.totalDuration = totalDuration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "PassSlipDTO{" +
               "slipId='" + slipId + '\'' +
               ", employeeId='" + employeeId + '\'' +
               ", destination='" + destination + '\'' +
               ", reason='" + reason + '\'' +
               ", timeOut=" + timeOut +
               ", timeIn=" + timeIn +
               ", totalDuration='" + totalDuration + '\'' +
               ", status='" + status + '\'' +
               '}';
    }
}
