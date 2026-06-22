package backend.employee;

import backend.auth.SessionActiveGuard;
import backend.logging.ActivityLogger;
import backend.shared.ApplicationConstants;
import backend.validation.ValidationException;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Orchestrates the employee management business logic.
 *
 * <p>Every public method:
 * <ol>
 *   <li>Calls {@link SessionActiveGuard} to assert a valid session.</li>
 *   <li>Validates input via {@link EmployeeValidator}.</li>
 *   <li>Delegates persistence to {@link EmployeeRepository}.</li>
 *   <li>Writes an immutable event to {@link ActivityLogger}.</li>
 * </ol>
 *
 * <p>Controllers must pass the current JavaFX {@link Window} for the session guard
 * so that an invalid session can redirect to the lock screen.
 */
public final class EmployeeService {

    private static final Logger LOG = Logger.getLogger(EmployeeService.class.getName());

    private static volatile EmployeeService instance;

    private final EmployeeValidator    validator  = EmployeeValidator.getInstance();
    private final EmployeeRepository   repository = EmployeeRepository.getInstance();
    private final ActivityLogger       logger     = ActivityLogger.getInstance();
    private final SessionActiveGuard   guard      = SessionActiveGuard.getInstance();

    private EmployeeService() {}

    public static EmployeeService getInstance() {
        if (instance == null) {
            synchronized (EmployeeService.class) {
                if (instance == null) instance = new EmployeeService();
            }
        }
        return instance;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Registers a new employee in the directory.
     *
     * @param dto    validated-ready employee record (use {@link EmployeeDTO#forInsert})
     * @param window the calling scene's window, for session-guard redirects
     * @throws SecurityException    if the session is not active
     * @throws ValidationException  if any field fails validation
     * @throws RepositoryException  if the employee ID already exists or a DB error occurs
     */
    public void registerEmployee(EmployeeDTO dto, Window window) {
        guard.assertSessionActive(window);
        validator.validateEmployeeDTO(dto);
        repository.insert(dto);
        logger.log(ApplicationConstants.LOG_EVENT_EMP_CREATE,
                "Employee registered: " + dto.getEmployeeId()
                        + " (" + dto.getFullName() + ") — Dept: " + dto.getDepartment());
        LOG.info("Employee registered: " + dto.getEmployeeId());
    }

    /**
     * Updates all mutable fields of an existing employee.
     *
     * @param dto    record containing updated values; the {@code employeeId} identifies the row
     * @param window the calling scene's window, for session-guard redirects
     * @throws SecurityException    if the session is not active
     * @throws ValidationException  if any field fails validation
     * @throws RepositoryException  if no active employee with that ID exists
     */
    public void updateEmployee(EmployeeDTO dto, Window window) {
        guard.assertSessionActive(window);
        validator.validateEmployeeDTO(dto);
        repository.update(dto);
        logger.log(ApplicationConstants.LOG_EVENT_EMP_UPDATE,
                "Employee updated: " + dto.getEmployeeId()
                        + " (" + dto.getFullName() + ") — Status: " + dto.getStatus());
        LOG.info("Employee updated: " + dto.getEmployeeId());
    }

    /**
     * Changes only the status field of an employee (e.g. OUT → RETURNED).
     *
     * @param employeeId target employee ID
     * @param newStatus  desired new status
     * @param window     the calling scene's window, for session-guard redirects
     * @throws SecurityException    if the session is not active
     * @throws ValidationException  if the employee ID is malformed
     * @throws RepositoryException  if no active employee with that ID exists
     */
    public void changeEmployeeStatus(String employeeId, EmployeeStatus newStatus, Window window) {
        guard.assertSessionActive(window);
        validator.validateEmployeeId(employeeId);
        if (newStatus == null) {
            throw new ValidationException(EmployeeValidator.FIELD_STATUS,
                    "New status must not be null.");
        }
        repository.updateStatus(employeeId, newStatus);
        logger.log(ApplicationConstants.LOG_EVENT_EMP_UPDATE,
                "Status change: " + employeeId + " → " + newStatus.name());
        LOG.info("Employee status changed: " + employeeId + " → " + newStatus);
    }

    /**
     * Soft-deletes (archives) an employee. The record is preserved for audit purposes.
     *
     * @param employeeId target employee ID
     * @param window     the calling scene's window, for session-guard redirects
     * @throws SecurityException   if the session is not active
     * @throws ValidationException if the employee ID is malformed
     * @throws RepositoryException if no active employee with that ID exists
     */
    public void archiveEmployee(String employeeId, Window window) {
        guard.assertSessionActive(window);
        validator.validateEmployeeId(employeeId);
        repository.archive(employeeId);
        logger.log(ApplicationConstants.LOG_EVENT_EMP_ARCHIVE,
                "Employee archived: " + employeeId);
        LOG.info("Employee archived: " + employeeId);
    }

    /**
     * Retrieves a single active employee by ID.
     *
     * @param employeeId target employee ID
     * @param window     the calling scene's window, for session-guard redirects
     * @return an {@link Optional} containing the record, or empty if not found
     */
    public Optional<EmployeeDTO> findById(String employeeId, Window window) {
        guard.assertSessionActive(window);
        validator.validateEmployeeId(employeeId);
        return repository.findById(employeeId);
    }

    /**
     * Returns all non-archived employees, ordered by last name.
     *
     * @param window the calling scene's window, for session-guard redirects
     */
    public List<EmployeeDTO> listAllActive(Window window) {
        guard.assertSessionActive(window);
        return repository.findAllActive();
    }

    /**
     * Returns all non-archived employees filtered by status.
     *
     * @param status the desired status filter
     * @param window the calling scene's window, for session-guard redirects
     */
    public List<EmployeeDTO> listByStatus(EmployeeStatus status, Window window) {
        guard.assertSessionActive(window);
        if (status == null) throw new ValidationException(EmployeeValidator.FIELD_STATUS,
                "Status filter must not be null.");
        return repository.findByStatus(status);
    }
}
