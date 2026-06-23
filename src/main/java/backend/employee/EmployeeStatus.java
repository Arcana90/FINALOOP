package backend.employee;

/**
 * Deterministic lifecycle enum enforcing the pipeline:
 * AVAILABLE -> OUT -> RETURNED -> (reset to AVAILABLE)
 */
public enum EmployeeStatus {
    AVAILABLE,
    OUT,
    RETURNED;

    public static EmployeeStatus fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("EmployeeStatus value must not be null.");
        }

        return switch (value.trim().toUpperCase()) {
            case "AVAILABLE" -> AVAILABLE;
            case "OUT" -> OUT;
            case "RETURNED" -> RETURNED;
            default -> throw new IllegalArgumentException("Unknown EmployeeStatus value: '" + value + "'");
        };
    }
}
