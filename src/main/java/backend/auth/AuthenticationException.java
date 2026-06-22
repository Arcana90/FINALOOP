package backend.auth;

/**
 * Thrown when an authentication or unlock attempt fails for a business reason
 * (wrong password, no admin seeded, etc.) rather than a validation constraint.
 *
 * <p>Controllers catch this and display its message directly in the UI.
 */
public final class AuthenticationException extends RuntimeException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
