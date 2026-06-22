package backend.employee;

/**
 * Thrown by {@link EmployeeRepository} when a database operation cannot be completed
 * for a business or structural reason (e.g. duplicate ID, record not found, constraint
 * violation). Wraps {@link java.sql.SQLException} where applicable.
 */
public final class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
