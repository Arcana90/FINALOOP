package backend.employee;

import backend.shared.ApplicationConstants;
import backend.validation.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates all mutable employee fields before they are persisted.
 *
 * <p>Field key constants are public so controllers can map them to specific
 * TextField nodes for inline error highlighting.
 */
public final class EmployeeValidator {

    public static final String FIELD_EMPLOYEE_ID = "employeeId";
    public static final String FIELD_FIRST_NAME  = "firstName";
    public static final String FIELD_LAST_NAME   = "lastName";
    public static final String FIELD_DEPARTMENT  = "department";
    public static final String FIELD_STATUS      = "status";

    private static volatile EmployeeValidator instance;

    private EmployeeValidator() {}

    public static EmployeeValidator getInstance() {
        if (instance == null) {
            synchronized (EmployeeValidator.class) {
                if (instance == null) instance = new EmployeeValidator();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Validates a complete registration or update payload.
     *
     * @param dto the employee record to validate
     * @throws ValidationException if any field fails its constraint
     */
    public void validateEmployeeDTO(EmployeeDTO dto) {
        Map<String, String> errors = new LinkedHashMap<>();

        validateEmployeeId(dto.getEmployeeId(), errors);
        validateName(dto.getFirstName(),  FIELD_FIRST_NAME,  "First name",  errors);
        validateName(dto.getLastName(),   FIELD_LAST_NAME,   "Last name",   errors);
        validateDepartment(dto.getDepartment(), errors);
        validateStatus(dto.getStatus(), errors);

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    /**
     * Validates only the Employee ID — used for lookup-only operations.
     *
     * @param employeeId raw employee ID string
     * @throws ValidationException if the ID does not match the required pattern
     */
    public void validateEmployeeId(String employeeId) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateEmployeeId(employeeId, errors);
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    // ── Private constraint checks ────────────────────────────────────────────────

    private void validateEmployeeId(String id, Map<String, String> errors) {
        if (id == null || id.isBlank()) {
            errors.put(FIELD_EMPLOYEE_ID, "Employee ID is required.");
        } else if (!id.matches(ApplicationConstants.EMPLOYEE_ID_PATTERN)) {
            errors.put(FIELD_EMPLOYEE_ID,
                    "Employee ID must be 2 uppercase letters followed by 4 digits (e.g. EX0042).");
        }
    }

    private void validateName(String value, String field, String label,
                              Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            errors.put(field, label + " is required.");
        } else if (value.length() < ApplicationConstants.EMPLOYEE_NAME_MIN_LEN) {
            errors.put(field, label + " must be at least "
                    + ApplicationConstants.EMPLOYEE_NAME_MIN_LEN + " characters.");
        } else if (value.length() > ApplicationConstants.EMPLOYEE_NAME_MAX_LEN) {
            errors.put(field, label + " must not exceed "
                    + ApplicationConstants.EMPLOYEE_NAME_MAX_LEN + " characters.");
        } else if (!value.matches("^[a-zA-ZÀ-ÿ\\s'\\-]+$")) {
            errors.put(field, label + " may only contain letters, spaces, apostrophes, "
                    + "and hyphens.");
        }
    }

    private void validateDepartment(String dept, Map<String, String> errors) {
        if (dept == null || dept.isBlank()) {
            errors.put(FIELD_DEPARTMENT, "Department is required.");
        } else if (dept.length() > ApplicationConstants.EMPLOYEE_DEPT_MAX_LEN) {
            errors.put(FIELD_DEPARTMENT, "Department name must not exceed "
                    + ApplicationConstants.EMPLOYEE_DEPT_MAX_LEN + " characters.");
        }
    }

    private void validateStatus(EmployeeStatus status, Map<String, String> errors) {
        if (status == null) {
            errors.put(FIELD_STATUS, "Employee status is required.");
        }
    }
}
