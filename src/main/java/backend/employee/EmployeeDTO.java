package backend.employee;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable data transfer object representing a single employee record.
 *
 * <p>This class is used as the boundary type between the repository layer
 * and the service / controller layers. It is also used as the JavaFX
 * TableView row model (controllers wrap it in a {@code SimpleObjectProperty}
 * or extract fields into {@code SimpleStringProperty} cells directly).
 */
public final class EmployeeDTO {

    private final String          employeeId;
    private final String          firstName;
    private final String          lastName;
    private final String          department;
    private final EmployeeStatus  status;
    private final boolean         archived;
    private final LocalDateTime   createdAt;
    private final LocalDateTime   updatedAt;

    // ── Constructor ──────────────────────────────────────────────────────────────

    public EmployeeDTO(
            String         employeeId,
            String         firstName,
            String         lastName,
            String         department,
            EmployeeStatus status,
            boolean        archived,
            LocalDateTime  createdAt,
            LocalDateTime  updatedAt) {

        this.employeeId = Objects.requireNonNull(employeeId, "employeeId must not be null");
        this.firstName  = Objects.requireNonNull(firstName,  "firstName must not be null");
        this.lastName   = Objects.requireNonNull(lastName,   "lastName must not be null");
        this.department = Objects.requireNonNull(department, "department must not be null");
        this.status     = Objects.requireNonNull(status,     "status must not be null");
        this.archived   = archived;
        this.createdAt  = createdAt;
        this.updatedAt  = updatedAt;
    }

    // ── Factory — convenience for INSERT payloads (no timestamps yet) ────────────

    public static EmployeeDTO forInsert(
            String employeeId, String firstName, String lastName,
            String department, EmployeeStatus status) {
        return new EmployeeDTO(employeeId, firstName, lastName,
                department, status, false, null, null);
    }

    // ── Accessors ────────────────────────────────────────────────────────────────

    public String         getEmployeeId() { return employeeId; }
    public String         getFirstName()  { return firstName; }
    public String         getLastName()   { return lastName; }
    public String         getFullName()   { return firstName + " " + lastName; }
    public String         getDepartment() { return department; }
    public EmployeeStatus getStatus()     { return status; }
    public boolean        isArchived()    { return archived; }
    public LocalDateTime  getCreatedAt()  { return createdAt; }
    public LocalDateTime  getUpdatedAt()  { return updatedAt; }

    // ── Mutating-copy helpers (returns new instance) ─────────────────────────────

    public EmployeeDTO withStatus(EmployeeStatus newStatus) {
        return new EmployeeDTO(employeeId, firstName, lastName,
                department, newStatus, archived, createdAt, updatedAt);
    }

    public EmployeeDTO asArchived() {
        return new EmployeeDTO(employeeId, firstName, lastName,
                department, status, true, createdAt, updatedAt);
    }

    // ── Object overrides ─────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeDTO that)) return false;
        return Objects.equals(employeeId, that.employeeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(employeeId);
    }

    @Override
    public String toString() {
        return "EmployeeDTO{id='" + employeeId + "', name='" + getFullName()
                + "', dept='" + department + "', status=" + status
                + ", archived=" + archived + "}";
    }
}
