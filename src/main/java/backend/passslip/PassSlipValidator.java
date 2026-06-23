package backend.passslip;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless validator for pass slip issuance form parameters.
 *
 * <p>Validates all user-submitted fields before any database interaction occurs.
 * All constraint violations are collected into a single result object so the UI
 * can display a complete list of errors in one pass rather than failing field-by-field.</p>
 */
public class PassSlipValidator {

    /** Maximum allowed character length for the destination field (VARCHAR constraint). */
    public static final int MAX_DESTINATION_LENGTH = 255;

    /** Maximum allowed character length for the reason field (VARCHAR constraint). */
    public static final int MAX_REASON_LENGTH = 500;

    /** Maximum allowed character length for the employee ID field. */
    public static final int MAX_EMPLOYEE_ID_LENGTH = 50;

    /**
     * Validates the provided pass slip issuance parameters.
     *
     * <p>Checks performed:
     * <ul>
     *   <li>Employee ID: not null, not blank, within length bounds.</li>
     *   <li>Destination: not null, not blank, within VARCHAR length bounds.</li>
     *   <li>Reason: not null, not blank, within VARCHAR length bounds.</li>
     * </ul>
     * </p>
     *
     * @param employeeId  The employee identifier submitted from the UI form.
     * @param destination The declared outbound destination.
     * @param reason      The stated reason for leaving.
     * @return A {@link ValidationResult} containing pass/fail status and all violation messages.
     */
    public ValidationResult validate(String employeeId, String destination, String reason) {
        List<String> violations = new ArrayList<>();

        validateEmployeeId(employeeId, violations);
        validateDestination(destination, violations);
        validateReason(reason, violations);

        return violations.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(violations);
    }

    private void validateEmployeeId(String employeeId, List<String> violations) {
        if (employeeId == null || employeeId.isBlank()) {
            violations.add("Employee ID must not be empty.");
            return;
        }
        if (employeeId.length() > MAX_EMPLOYEE_ID_LENGTH) {
            violations.add(String.format(
                    "Employee ID exceeds maximum length of %d characters (provided: %d).",
                    MAX_EMPLOYEE_ID_LENGTH, employeeId.length()
            ));
        }
    }

    private void validateDestination(String destination, List<String> violations) {
        if (destination == null || destination.isBlank()) {
            violations.add("Destination must not be empty.");
            return;
        }
        if (destination.length() > MAX_DESTINATION_LENGTH) {
            violations.add(String.format(
                    "Destination exceeds maximum length of %d characters (provided: %d).",
                    MAX_DESTINATION_LENGTH, destination.length()
            ));
        }
    }

    private void validateReason(String reason, List<String> violations) {
        if (reason == null || reason.isBlank()) {
            violations.add("Reason must not be empty.");
            return;
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            violations.add(String.format(
                    "Reason exceeds maximum length of %d characters (provided: %d).",
                    MAX_REASON_LENGTH, reason.length()
            ));
        }
    }

    /**
     * Encapsulates the outcome of a validation pass.
     *
     * <p>Immutable after construction. Call {@link #isValid()} to branch on success,
     * and {@link #getViolations()} to retrieve all accumulated error messages.</p>
     */
    public static final class ValidationResult {

        private final boolean valid;
        private final List<String> violations;

        private ValidationResult(boolean valid, List<String> violations) {
            this.valid = valid;
            this.violations = List.copyOf(violations);
        }

        /**
         * Creates a successful (no-violation) result.
         *
         * @return A valid {@code ValidationResult}.
         */
        public static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        /**
         * Creates a failed result with the provided violation messages.
         *
         * @param violations The non-empty list of violation messages.
         * @return An invalid {@code ValidationResult}.
         */
        public static ValidationResult failure(List<String> violations) {
            return new ValidationResult(false, violations);
        }

        /**
         * Returns {@code true} if all validation rules passed.
         *
         * @return Whether this result represents a fully valid form submission.
         */
        public boolean isValid() {
            return valid;
        }

        /**
         * Returns an unmodifiable list of all collected violation messages.
         * Empty when {@link #isValid()} returns {@code true}.
         *
         * @return List of human-readable validation error messages.
         */
        public List<String> getViolations() {
            return violations;
        }

        /**
         * Returns a concatenated, newline-separated string of all violations.
         * Useful for logging and simple UI error display.
         *
         * @return All violation messages joined by newline characters.
         */
        public String getViolationsAsString() {
            return String.join(System.lineSeparator(), violations);
        }
    }
}
