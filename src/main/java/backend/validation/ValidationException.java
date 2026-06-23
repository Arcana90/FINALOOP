package backend.validation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown by any Validator when one or more input fields fail their constraints.
 * Carries a field → message map so controllers can highlight individual UI controls.
 */
public final class ValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public ValidationException(Map<String, String> fieldErrors) {
        super("Validation failed: " + fieldErrors);
        this.fieldErrors = Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    /** Returns an unmodifiable view of all field-level error messages. */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    /** Convenience: returns the first error message regardless of field. */
    public String firstMessage() {
        return fieldErrors.values().iterator().next();
    }
}
