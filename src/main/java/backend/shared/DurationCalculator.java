package backend.shared;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Stateless, thread-safe utility class for computing human-readable duration strings
 * between two {@link LocalDateTime} timestamps.
 *
 * <p>This calculator is the single authority for duration formatting within the system.
 * It uses {@link java.time.Duration} for precise, calendar-agnostic elapsed time computation
 * and formats the result exactly as "Xh Ym" (e.g., "1h 45m", "0h 30m", "3h 0m").</p>
 *
 * <p>All methods are static. This class may not be instantiated.</p>
 */
public final class DurationCalculator {

    private DurationCalculator() {
        throw new UnsupportedOperationException(
                "DurationCalculator is a utility class and may not be instantiated."
        );
    }

    /**
     * Computes the elapsed duration between a TimeOut and a TimeIn timestamp, returning
     * a formatted string in "Xh Ym" notation.
     *
     * <p>The total elapsed time is decomposed into whole hours and the remaining minutes.
     * Seconds and sub-second components are truncated (not rounded).</p>
     *
     * <p>Examples:
     * <ul>
     *   <li>90 minutes elapsed → "1h 30m"</li>
     *   <li>30 minutes elapsed → "0h 30m"</li>
     *   <li>180 minutes elapsed → "3h 0m"</li>
     *   <li>0 minutes elapsed → "0h 0m"</li>
     * </ul>
     * </p>
     *
     * @param timeOut The timestamp when the pass slip was issued and the employee departed.
     *                Must not be null.
     * @param timeIn  The timestamp when the employee returned and the Time-In was recorded.
     *                Must not be null.
     * @return A non-null, non-empty string in "Xh Ym" format representing total elapsed time.
     * @throws IllegalArgumentException if either timestamp is null, or if {@code timeIn}
     *                                  is before {@code timeOut}.
     */
    public static String calculate(LocalDateTime timeOut, LocalDateTime timeIn) {
        if (timeOut == null) {
            throw new IllegalArgumentException("timeOut must not be null.");
        }
        if (timeIn == null) {
            throw new IllegalArgumentException("timeIn must not be null.");
        }
        if (timeIn.isBefore(timeOut)) {
            throw new IllegalArgumentException(String.format(
                    "timeIn [%s] must not be before timeOut [%s].", timeIn, timeOut
            ));
        }

        Duration duration = Duration.between(timeOut, timeIn);

        long totalMinutes = duration.toMinutes();
        long hours = totalMinutes / 60;
        long remainingMinutes = totalMinutes % 60;

        return formatDuration(hours, remainingMinutes);
    }

    /**
     * Formats hours and remaining minutes into the canonical "Xh Ym" string representation.
     *
     * @param hours            Whole hours component of the elapsed duration.
     * @param remainingMinutes Minutes beyond the whole hours (0–59).
     * @return The formatted duration string.
     */
    private static String formatDuration(long hours, long remainingMinutes) {
        return hours + "h " + remainingMinutes + "m";
    }

    /**
     * Returns the total elapsed duration in whole minutes between two timestamps.
     * Useful for numeric comparisons or audit logging alongside the formatted string.
     *
     * @param timeOut The departure timestamp.
     * @param timeIn  The return timestamp.
     * @return Total elapsed minutes as a non-negative long.
     * @throws IllegalArgumentException if either timestamp is null, or timeIn is before timeOut.
     */
    public static long totalMinutes(LocalDateTime timeOut, LocalDateTime timeIn) {
        if (timeOut == null) {
            throw new IllegalArgumentException("timeOut must not be null.");
        }
        if (timeIn == null) {
            throw new IllegalArgumentException("timeIn must not be null.");
        }
        if (timeIn.isBefore(timeOut)) {
            throw new IllegalArgumentException(String.format(
                    "timeIn [%s] must not be before timeOut [%s].", timeIn, timeOut
            ));
        }
        return Duration.between(timeOut, timeIn).toMinutes();
    }
}
