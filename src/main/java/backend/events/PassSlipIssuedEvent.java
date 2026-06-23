package backend.events;

import java.time.LocalDateTime;

/**
 * Immutable event record emitted immediately after a pass slip is successfully
 * persisted and the employee's status transitions to OUT.
 *
 * @param slipId             The generated unique identifier of the new pass slip.
 * @param employeeId         The ID of the employee who received the pass slip.
 * @param outboundTimestamp  The exact system timestamp recorded at the moment of issuance.
 */
public record PassSlipIssuedEvent(
        String slipId,
        String employeeId,
        LocalDateTime outboundTimestamp
) {}
