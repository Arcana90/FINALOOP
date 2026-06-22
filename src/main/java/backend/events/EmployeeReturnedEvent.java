package backend.events;

import java.time.LocalDateTime;

/**
 * Immutable event record emitted after a Time-In is processed and the employee
 * and pass slip records have been transitioned to RETURNED status.
 *
 * @param slipId             The unique identifier of the resolved pass slip.
 * @param employeeId         The ID of the employee who has returned.
 * @param inboundTimestamp   The exact system timestamp recorded at the moment of return.
 * @param totalDuration      Human-readable elapsed duration string in "Xh Ym" format.
 */
public record EmployeeReturnedEvent(
        String slipId,
        String employeeId,
        LocalDateTime inboundTimestamp,
        String totalDuration
) {}
