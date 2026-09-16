package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.persistence.*;
import com.arthurpaiao.creditengine.domain.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsvImportServiceTest {
    private final AssignorRepository assignors = mock(AssignorRepository.class);
    private final ReceivableRepository receivables = mock(ReceivableRepository.class);
    private final CreditService credit = mock(CreditService.class);
    private final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final CsvImportService service = new CsvImportService(assignors, receivables, credit, factory.getValidator(), 100);
    private final UUID id = UUID.randomUUID();
    private static final String HEADER = String.join(";", CsvImportService.HEADERS) + "\n";
    private static final String VALID = "CED-001;T-1;DUPLICATA_MERCANTIL;1000,00;15/12/2026;USD\n";
    @BeforeEach void setup() {
        var assignor = mock(Assignor.class); when(assignor.getId()).thenReturn(id);
        when(assignors.findByCode("CED-001")).thenReturn(Optional.of(assignor));
    }
    @AfterEach void close() { factory.close(); }
    private CsvImportService.Preview parse(String value) { return service.preview(value.getBytes(StandardCharsets.UTF_8)); }

    @Test void acceptsBomQuotedDelimiterAndMultilineWithoutCreatingAnything() {
        var result = parse("\uFEFF" + HEADER + VALID.replace("T-1", "\"T;\n1\"") + VALID.replace("T-1", "T-2"));
        assertEquals(2, result.rows().get(0).line()); assertEquals(4, result.rows().get(1).line());
        assertEquals("T;\n1", result.rows().get(0).request().titleCode());
        assertEquals("1000.00", result.rows().get(0).request().faceValue());
        verify(credit, never()).createReceivable(any()); verify(receivables, never()).saveAndFlush(any());
    }
    @Test void collectsFieldErrorsAndRetainsEveryRow() {
        var result = parse(HEADER + VALID + "UNKNOWN;;INVALID;1.000,00;31/02/2026;EUR\n");
        assertEquals(2, result.rows().size()); assertEquals(6, result.rows().get(1).errors().size());
        assertNull(result.rows().get(1).request());
    }
    @Test void rejectsMalformedFileMissingHeadersAndInvalidUtf8() {
        assertThrows(IllegalArgumentException.class, () -> parse(HEADER + "\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> parse("foo;bar\n" + VALID));
        assertThrows(IllegalArgumentException.class, () -> parse(HEADER + "one;two\n"));
        assertThrows(IllegalArgumentException.class, () -> service.preview(new byte[]{(byte) 0xc3, 0x28}));
        assertThrows(IllegalArgumentException.class, () -> parse(HEADER));
        assertThrows(IllegalArgumentException.class, () -> service.preview(new byte[262145]));
    }
    @Test void accepts100ButRejects101IncludingInvalidRowsWithoutTruncating() {
        assertEquals(100, parse(HEADER + VALID.repeat(100)).rows().size());
        assertThrows(IllegalArgumentException.class, () -> parse(HEADER + VALID.repeat(100) + ";;;;;\n"));
    }
    @Test void marksEveryOccurrenceOfDuplicateAfterTrimming() {
        var result = parse(HEADER + VALID + VALID.replace("T-1", " T-1 "));
        assertTrue(result.rows().stream().allMatch(row -> row.request() == null && row.errors().contains("Cedente/título repetido no arquivo")));
    }
    @Test void distinguishesExistingDuplicateFromConflictingData() {
        var old = new Receivable(UUID.randomUUID(), id, "T-1", ReceivableType.DUPLICATA_MERCANTIL,
                new java.math.BigDecimal("1000.00"), java.time.LocalDate.of(2026, 12, 15), PaymentCurrency.USD, java.time.Instant.now());
        when(receivables.findByAssignorIdAndTitleCode(id, "T-1")).thenReturn(Optional.of(old));
        assertTrue(parse(HEADER + VALID).rows().getFirst().errors().getFirst().startsWith("Duplicado"));
        assertTrue(parse(HEADER + VALID.replace("1000,00", "2000,00")).rows().getFirst().errors().getFirst().startsWith("Conflito"));
    }
    @Test void appliesSharedBusinessValidationAndAllowsOtherRows() {
        doThrow(new IllegalArgumentException("Prazo excede o limite")).when(credit).validateRegistration(any());
        assertEquals(List.of("Prazo excede o limite"), parse(HEADER + VALID).rows().getFirst().errors());
    }
}
