package backend.passslip;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PassSlipValidator")
class PassSlipValidatorTest {

    private PassSlipValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PassSlipValidator();
    }

    @Test
    @DisplayName("Valid inputs → result is valid")
    void validInputs() {
        var result = validator.validate("EMP-001", "City Hall", "Permit renewal");
        assertTrue(result.isValid());
        assertTrue(result.getViolations().isEmpty());
    }

    @Test
    @DisplayName("Blank employeeId → violation collected")
    void blankEmployeeId() {
        var result = validator.validate("  ", "City Hall", "Reason");
        assertFalse(result.isValid());
        assertEquals(1, result.getViolations().size());
        assertTrue(result.getViolations().get(0).contains("Employee ID"));
    }

    @Test
    @DisplayName("Null destination → violation collected")
    void nullDestination() {
        var result = validator.validate("EMP-001", null, "Reason");
        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("Destination")));
    }

    @Test
    @DisplayName("Empty reason → violation collected")
    void emptyReason() {
        var result = validator.validate("EMP-001", "Somewhere", "");
        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("Reason")));
    }

    @Test
    @DisplayName("Multiple blank fields → all violations collected in one pass")
    void multipleViolations() {
        var result = validator.validate("", "", "");
        assertFalse(result.isValid());
        assertEquals(3, result.getViolations().size());
    }

    @Test
    @DisplayName("Destination exceeding max length → violation collected")
    void destinationTooLong() {
        String tooLong = "A".repeat(PassSlipValidator.MAX_DESTINATION_LENGTH + 1);
        var result = validator.validate("EMP-001", tooLong, "Reason");
        assertFalse(result.isValid());
        assertTrue(result.getViolations().stream().anyMatch(v -> v.contains("Destination")));
    }

    @Test
    @DisplayName("Reason at exact max length → valid")
    void reasonAtMaxLength() {
        String exactly = "R".repeat(PassSlipValidator.MAX_REASON_LENGTH);
        var result = validator.validate("EMP-001", "City Hall", exactly);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("getViolationsAsString joins with line separator")
    void violationsAsString() {
        var result = validator.validate("", "", "");
        String combined = result.getViolationsAsString();
        assertFalse(combined.isBlank());
        assertEquals(3, combined.split(System.lineSeparator()).length);
    }
}
