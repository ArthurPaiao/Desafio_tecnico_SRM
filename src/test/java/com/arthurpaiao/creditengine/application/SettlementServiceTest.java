package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.*;
import com.arthurpaiao.creditengine.api.Contracts.*;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementServiceTest {
    private final SettlementRepository settlements = mock(SettlementRepository.class);
    private final ReceivableRepository titles = mock(ReceivableRepository.class);
    private final AssignorRepository assignors = mock(AssignorRepository.class);
    private final CreditService credit = mock(CreditService.class);
    private final UUID key = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-14T15:00:00Z");
    private final Receivable title = new Receivable(UUID.randomUUID(), UUID.randomUUID(), "T-001",
            ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000.00"), LocalDate.of(2026, 12, 14), PaymentCurrency.BRL, now);
    private final SimulationView simulation = new SimulationView(title.getType(), LocalDate.of(2026, 9, 14),
            title.getDueDate(), now, "100000.00", 3, "0.0100000000", "0.0150000000", "0.0250000000",
            "92859.94", "7140.06", PaymentCurrency.BRL, null, "92859.94", null, null);
    private SettlementService service;
    private SettlementRequest request;

    @BeforeEach
    void setup() {
        service = new SettlementService(settlements, titles, assignors, credit);
        request = new SettlementRequest(title.getId(), ExpectedConditions.from(title, simulation));
        when(settlements.tryLockIntent(anyLong())).thenReturn(true);
        when(settlements.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(titles.findForSettlement(title.getId())).thenReturn(Optional.of(title));
        when(credit.simulate(any())).thenReturn(simulation);
        var assignor = mock(Assignor.class);
        when(assignor.getName()).thenReturn("Cedente original");
        when(assignors.findById(title.getAssignorId())).thenReturn(Optional.of(assignor));
        when(settlements.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void firstSettlementUsesServerCalculationAndChangesState() {
        var result = service.settle(key, request);
        assertFalse(result.replayed());
        assertEquals("92859.94", result.settlement().calculation().finalAmount());
        assertEquals("Cedente original", result.settlement().assignorName());
        assertEquals(Receivable.Status.SETTLED, title.getStatus());
        assertEquals(now, title.getUpdatedAt());
    }

    @Test
    void replayReturnsOriginalBeforeAnyRecalculation() {
        var original = service.settle(key, request);
        var saved = org.mockito.ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).saveAndFlush(saved.capture());
        when(settlements.findByIdempotencyKey(key)).thenReturn(Optional.of(saved.getValue()));
        clearInvocations(credit, titles, assignors);
        var replay = service.settle(key, request);
        assertTrue(replay.replayed());
        assertEquals(original.settlement(), replay.settlement());
        verifyNoInteractions(credit, titles, assignors);
    }

    @Test
    void sameKeyWithDifferentTitleConflictsBeforeRecalculation() {
        service.settle(key, request);
        var saved = org.mockito.ArgumentCaptor.forClass(Settlement.class);
        verify(settlements).saveAndFlush(saved.capture());
        when(settlements.findByIdempotencyKey(key)).thenReturn(Optional.of(saved.getValue()));
        clearInvocations(credit, titles);
        var error = assertThrows(BusinessException.class, () -> service.settle(key,
                new SettlementRequest(UUID.randomUUID(), request.expectedConditions())));
        assertEquals(BusinessException.Code.IDEMPOTENCY_CONFLICT, error.getCode());
        verifyNoInteractions(credit, titles);
    }

    @Test
    void changedConditionsDoNotSettleAndReturnCurrentSimulation() {
        var changed = new ExpectedConditions(title.getAssignorId(), "OTHER", title.getType(), title.getDueDate(), 3,
                PaymentCurrency.BRL, "100000", "0.025", "92859.94", "7140.06", "92859.94", null);
        var error = assertThrows(ConditionsChangedException.class,
                () -> service.settle(key, new SettlementRequest(title.getId(), changed)));
        assertEquals(simulation, error.getCurrent().simulation());
        assertEquals(request.expectedConditions(), error.getCurrent().expectedConditions());
        assertEquals(Receivable.Status.PENDING, title.getStatus());
        verify(settlements, never()).saveAndFlush(any());
    }

    @Test
    void busyIntentDoesNotProceed() {
        when(settlements.tryLockIntent(anyLong())).thenReturn(false);
        var error = assertThrows(BusinessException.class, () -> service.settle(key, request));
        assertEquals(BusinessException.Code.OPERATION_IN_PROGRESS, error.getCode());
        verifyNoInteractions(credit);
    }

    @Test
    void alreadySettledTitleRejectsAnotherIntent() {
        service.settle(key, request);
        var error = assertThrows(BusinessException.class, () -> service.settle(UUID.randomUUID(), request));
        assertEquals(BusinessException.Code.ALREADY_SETTLED, error.getCode());
    }
}
