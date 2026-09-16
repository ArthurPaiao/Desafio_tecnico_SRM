package com.arthurpaiao.creditengine.domain;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class TermCalculatorTest {
    private final TermCalculator calculator = new TermCalculator(
            Clock.fixed(Instant.parse("2026-09-15T01:00:00Z"), ZoneOffset.UTC),
            ZoneId.of("America/Sao_Paulo"), 120);

    @ParameterizedTest
    @CsvSource({
        "2026-09-14,2026-09-14,0", "2026-09-14,2026-09-15,1",
        "2026-09-14,2026-12-14,3", "2026-09-14,2026-12-15,4",
        "2027-01-31,2027-02-28,1", "2028-01-31,2028-02-29,1",
        "2027-01-31,2027-03-30,2", "2027-01-31,2027-03-31,2",
        "2027-01-31,2027-04-01,3", "2026-12-31,2027-01-31,1",
        "2026-09-14,2036-09-14,120"
    })
    void calendarAnniversaries(String reference, String due, int expected) {
        assertEquals(expected, calculator.calculate(LocalDate.parse(reference), LocalDate.parse(due)).months());
    }

    @Test void usesBusinessZoneAndPreservesReferenceDate() {
        var term = calculator.calculate(LocalDate.of(2026, 9, 14));
        assertEquals(LocalDate.of(2026, 9, 14), term.referenceDate());
        assertEquals(0, term.months());
    }

    @Test void rejectsOverdueMissingAndAboveLimit() {
        var reference = LocalDate.of(2026, 9, 14);
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(reference, reference.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(reference, reference.plusYears(10).plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(reference, null));
    }
}
