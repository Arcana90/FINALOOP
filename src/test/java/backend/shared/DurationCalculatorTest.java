package backend.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DurationCalculator")
class DurationCalculatorTest {

    @Test
    @DisplayName("90 minutes → '1h 30m'")
    void ninetyMinutes() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1, 10, 30, 0);
        assertEquals("1h 30m", DurationCalculator.calculate(out, in));
    }

    @Test
    @DisplayName("30 minutes → '0h 30m'")
    void thirtyMinutes() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1, 9, 30, 0);
        assertEquals("0h 30m", DurationCalculator.calculate(out, in));
    }

    @Test
    @DisplayName("Exactly 3 hours → '3h 0m'")
    void exactlyThreeHours() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 8, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1, 11, 0, 0);
        assertEquals("3h 0m", DurationCalculator.calculate(out, in));
    }

    @Test
    @DisplayName("Same timestamp → '0h 0m'")
    void zeroDuration() {
        LocalDateTime time = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        assertEquals("0h 0m", DurationCalculator.calculate(time, time));
    }

    @Test
    @DisplayName("Seconds are truncated, not rounded")
    void secondsTruncated() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1, 9, 44, 59); // 44m 59s → still 44m
        assertEquals("0h 44m", DurationCalculator.calculate(out, in));
    }

    @Test
    @DisplayName("totalMinutes returns correct count")
    void totalMinutesCalculation() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 9, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1, 10, 45, 0);
        assertEquals(105L, DurationCalculator.totalMinutes(out, in));
    }

    @Test
    @DisplayName("Null timeOut throws IllegalArgumentException")
    void nullTimeOutThrows() {
        LocalDateTime in = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> DurationCalculator.calculate(null, in));
    }

    @Test
    @DisplayName("Null timeIn throws IllegalArgumentException")
    void nullTimeInThrows() {
        LocalDateTime out = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
                () -> DurationCalculator.calculate(out, null));
    }

    @Test
    @DisplayName("timeIn before timeOut throws IllegalArgumentException")
    void timeInBeforeTimeOutThrows() {
        LocalDateTime out = LocalDateTime.of(2024, 6, 1, 10, 0, 0);
        LocalDateTime in  = LocalDateTime.of(2024, 6, 1,  9, 0, 0);
        assertThrows(IllegalArgumentException.class,
                () -> DurationCalculator.calculate(out, in));
    }
}
