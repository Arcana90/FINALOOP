package backend.auth;

import backend.shared.ApplicationConstants;
import backend.validation.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates administrator credential fields before they are forwarded
 * to {@link AuthenticationService}.
 *
 * <p>Throws {@link ValidationException} carrying a field-keyed error map
 * so the controller can highlight individual TextField controls.
 */
public final class AdminAuthValidator {

    /** Field key constants referenced by the controller for UI feedback. */
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_PASSWORD = "password";

    private static volatile AdminAuthValidator instance;

    private AdminAuthValidator() {}

    public static AdminAuthValidator getInstance() {
        if (instance == null) {
            synchronized (AdminAuthValidator.class) {
                if (instance == null) instance = new AdminAuthValidator();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Validates the login form payload.
     *
     * @param username raw text from the username TextField
     * @param password raw chars from the password PasswordField
     * @throws ValidationException if any field fails its constraint
     */
    public void validateLoginPayload(String username, char[] password) {
        Map<String, String> errors = new LinkedHashMap<>();

        // ── Username ─────────────────────────────────────────────────────────
        if (username == null || username.isBlank()) {
            errors.put(FIELD_USERNAME, "Username is required.");
        } else if (username.length() < ApplicationConstants.AUTH_USERNAME_MIN_LEN) {
            errors.put(FIELD_USERNAME,
                    "Username must be at least " + ApplicationConstants.AUTH_USERNAME_MIN_LEN
                            + " characters.");
        } else if (username.length() > ApplicationConstants.AUTH_USERNAME_MAX_LEN) {
            errors.put(FIELD_USERNAME,
                    "Username must not exceed " + ApplicationConstants.AUTH_USERNAME_MAX_LEN
                            + " characters.");
        } else if (!username.matches("^[a-zA-Z0-9_\\.\\-]+$")) {
            errors.put(FIELD_USERNAME,
                    "Username may only contain letters, digits, underscores, hyphens, and dots.");
        }

        // ── Password ─────────────────────────────────────────────────────────
        validatePasswordChars(password, errors);

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    /**
     * Validates an unlock payload (password only — username is already known from the session).
     *
     * @param password raw chars from the password PasswordField
     * @throws ValidationException if the password field is invalid
     */
    public void validateUnlockPayload(char[] password) {
        Map<String, String> errors = new LinkedHashMap<>();
        validatePasswordChars(password, errors);
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    // ── Private helpers ─────────────────────────────────────────────────────────

    private void validatePasswordChars(char[] password, Map<String, String> errors) {
        if (password == null || password.length == 0) {
            errors.put(FIELD_PASSWORD, "Password is required.");
        } else if (password.length < ApplicationConstants.AUTH_PASSWORD_MIN_LEN) {
            errors.put(FIELD_PASSWORD,
                    "Password must be at least " + ApplicationConstants.AUTH_PASSWORD_MIN_LEN
                            + " characters.");
        } else if (password.length > ApplicationConstants.AUTH_PASSWORD_MAX_LEN) {
            errors.put(FIELD_PASSWORD,
                    "Password must not exceed " + ApplicationConstants.AUTH_PASSWORD_MAX_LEN
                            + " characters.");
        }
    }
}
