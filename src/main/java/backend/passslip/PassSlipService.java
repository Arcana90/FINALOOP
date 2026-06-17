package backend.passslip;

import backend.employee.EmployeeStatus;
import backend.events.EventPublisher;
import backend.events.PassSlipIssuedEvent;
import backend.passslip.PassSlipRepository.PassSlipRepositoryException;
import backend.passslip.PassSlipValidator.ValidationResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class orchestrating the complete Pass Slip Issuance workflow.
 *
 * <p>Enforces the correct sequence of operations:
 * <ol>
 *   <li>Validate the submitted form parameters via {@link PassSlipValidator}.</li>
 *   <li>Confirm the employee's current status is {@link EmployeeStatus#AVAILABLE}.</li>
 *   <li>Assign a unique slip ID and the current system timestamp.</li>
 *   <li>Persist the slip and update the employee status atomically via {@link PassSlipRepository}.</li>
 *   <li>Broadcast a {@link PassSlipIssuedEvent} through the {@link EventPublisher}.</li>
 * </ol>
 * </p>
 *
 * <p>This class is the single authority for the issuance pipeline. No caller may
 * bypass validation or status checks by interacting with the repository directly.</p>
 */
public class PassSlipService {

    private static final Logger LOGGER = Logger.getLogger(PassSlipService.class.getName());

    private final PassSlipValidator validator;
    private final PassSlipRepository repository;
    private final EventPublisher eventPublisher;

    /**
     * Constructs the PassSlipService with its required collaborators.
     *
     * @param validator      Form validation component. Must not be null.
     * @param repository     Database persistence component. Must not be null.
     * @param eventPublisher System-wide event broadcaster. Must not be null.
     */
    public PassSlipService(PassSlipValidator validator,
                           PassSlipRepository repository,
                           EventPublisher eventPublisher) {
        if (validator == null) throw new IllegalArgumentException("PassSlipValidator must not be null.");
        if (repository == null) throw new IllegalArgumentException("PassSlipRepository must not be null.");
        if (eventPublisher == null) throw new IllegalArgumentException("EventPublisher must not be null.");

        this.validator = validator;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Executes the full pass slip issuance workflow for the given parameters.
     *
     * @param employeeId  The ID of the employee requesting the pass slip.
     * @param destination The employee's declared outbound destination.
     * @param reason      The employee's stated reason for leaving.
     * @return An {@link IssuanceResult} indicating success or a specific failure cause.
     */
    public IssuanceResult issuePassSlip(String employeeId, String destination, String reason) {
        LOGGER.info(String.format(
                "Issuance requested: employee=[%s], destination=[%s].", employeeId, destination
        ));

        ValidationResult validationResult = validator.validate(employeeId, destination, reason);
        if (!validationResult.isValid()) {
            LOGGER.warning(String.format(
                    "Issuance validation failed for employee [%s]: %s",
                    employeeId, validationResult.getViolationsAsString()
            ));
            return IssuanceResult.validationFailure(validationResult.getViolations());
        }

        EmployeeStatus currentStatus;
        try {
            currentStatus = repository.getEmployeeStatus(employeeId);
        } catch (PassSlipRepositoryException e) {
            LOGGER.log(Level.SEVERE,
                    "Failed to retrieve status for employee [" + employeeId + "].", e);
            return IssuanceResult.systemError(
                    "Unable to verify employee status. Please try again. Details: " + e.getMessage()
            );
        }

        if (currentStatus == EmployeeStatus.OUT) {
            LOGGER.warning(String.format(
                    "Issuance blocked: employee [%s] is currently OUT and cannot receive a new slip.",
                    employeeId
            ));
            return IssuanceResult.blocked(
                    "Employee [" + employeeId + "] already has an active pass slip (status: OUT). " +
                    "A Time-In must be processed before a new slip can be issued."
            );
        }

        String slipId = generateSlipId();
        LocalDateTime timeOut = LocalDateTime.now();

        PassSlipDTO dto = new PassSlipDTO(
                slipId,
                employeeId,
                destination.trim(),
                reason.trim(),
                timeOut,
                null,
                null,
                EmployeeStatus.OUT.name()
        );

        try {
            repository.issuePassSlip(dto);
        } catch (PassSlipRepositoryException e) {
            LOGGER.log(Level.SEVERE,
                    "Repository transaction failed for slip [" + slipId + "].", e);
            return IssuanceResult.systemError(
                    "Database error during pass slip issuance. Transaction rolled back. " +
                    "Details: " + e.getMessage()
            );
        }

        PassSlipIssuedEvent event = new PassSlipIssuedEvent(slipId, employeeId, timeOut);
        eventPublisher.publish(event);

        LOGGER.info(String.format(
                "Pass slip [%s] successfully issued for employee [%s] at [%s]. Event published.",
                slipId, employeeId, timeOut
        ));

        return IssuanceResult.success(dto);
    }

    /**
     * Generates a universally unique identifier for a new pass slip.
     *
     * @return A UUID string to be used as the slip's primary key.
     */
    private String generateSlipId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Represents the outcome of a pass slip issuance attempt.
     *
     * <p>Callers check {@link #getOutcome()} or use the typed accessor methods
     * to branch on success, validation failure, blocked status, or system errors.</p>
     */
    public static final class IssuanceResult {

        /**
         * Enumeration of possible issuance outcomes for clean branching in controllers.
         */
        public enum Outcome {
            /** Slip was created and persisted successfully. */
            SUCCESS,
            /** One or more form fields failed validation. */
            VALIDATION_FAILURE,
            /** Employee lifecycle state prevents slip issuance. */
            BLOCKED,
            /** An unrecoverable infrastructure or database error occurred. */
            SYSTEM_ERROR
        }

        private final Outcome outcome;
        private final PassSlipDTO createdSlip;
        private final java.util.List<String> violations;
        private final String errorMessage;

        private IssuanceResult(Outcome outcome, PassSlipDTO createdSlip,
                               java.util.List<String> violations, String errorMessage) {
            this.outcome = outcome;
            this.createdSlip = createdSlip;
            this.violations = violations != null ? java.util.List.copyOf(violations) : java.util.List.of();
            this.errorMessage = errorMessage;
        }

        static IssuanceResult success(PassSlipDTO slip) {
            return new IssuanceResult(Outcome.SUCCESS, slip, null, null);
        }

        static IssuanceResult validationFailure(java.util.List<String> violations) {
            return new IssuanceResult(Outcome.VALIDATION_FAILURE, null, violations, null);
        }

        static IssuanceResult blocked(String message) {
            return new IssuanceResult(Outcome.BLOCKED, null, null, message);
        }

        static IssuanceResult systemError(String message) {
            return new IssuanceResult(Outcome.SYSTEM_ERROR, null, null, message);
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public boolean isSuccess() {
            return outcome == Outcome.SUCCESS;
        }

        /**
         * Returns the created {@link PassSlipDTO} on success. Returns null for all other outcomes.
         *
         * @return The newly created slip DTO, or null.
         */
        public PassSlipDTO getCreatedSlip() {
            return createdSlip;
        }

        /**
         * Returns validation violation messages. Non-empty only when outcome is
         * {@link Outcome#VALIDATION_FAILURE}.
         *
         * @return Unmodifiable list of human-readable violation messages.
         */
        public java.util.List<String> getViolations() {
            return violations;
        }

        /**
         * Returns the error or block message for non-success outcomes.
         *
         * @return Descriptive error message, or null for validation failures (use getViolations()).
         */
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
