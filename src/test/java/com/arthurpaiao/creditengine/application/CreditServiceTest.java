package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.Contracts.*;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditServiceTest {
    private final Instant now = Instant.parse("2026-09-14T15:00:00Z");
    private final AssignorRepository assignors = mock(AssignorRepository.class);
    private final PricingConfigRepository configs = mock(PricingConfigRepository.class);
    private final ExchangeRateRepository rates = mock(ExchangeRateRepository.class);
    private final ReceivableRepository receivables = mock(ReceivableRepository.class);
    private final PricingConfig config = mock(PricingConfig.class);
    private CreditService service;

    @BeforeEach
    void setup() {
        service = new CreditService(assignors, configs, rates, receivables, Clock.fixed(now, ZoneOffset.UTC));
        when(configs.findById(1)).thenReturn(Optional.of(config));
        when(config.getBaseRate()).thenReturn(new BigDecimal("0.01"));
        when(config.getMaxTermMonths()).thenReturn(120);
        when(config.getExchangeValidityHours()).thenReturn(24);
    }

    @Test
    void brlUsesPersistedConfigurationWithoutQueryingOrWritingExchange() {
        var result = service.simulate(simulate(PaymentCurrency.BRL));
        assertEquals("92859.94", result.finalAmount());
        assertEquals("7140.06", result.discountBrl());
        assertEquals(3, result.termMonths());
        assertEquals(LocalDate.of(2026, 9, 14), result.referenceDate());
        assertEquals(now, result.calculatedAt());
        assertNull(result.exchangeRateId());
        assertNull(result.exchangeRateUsed());
        verifyNoInteractions(rates, receivables, assignors);
    }

    @Test
    void editPreservesIdentityAndUsesNoQuote() {
        UUID id = UUID.randomUUID(), assignor = UUID.randomUUID();
        var title = new Receivable(id, assignor, "FIXED", ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100.00"), LocalDate.of(2026, 12, 14), PaymentCurrency.BRL, now.minusSeconds(60));
        when(receivables.findForSettlement(id)).thenReturn(Optional.of(title));
        when(assignors.existsById(assignor)).thenReturn(true);
        var edited = service.updateReceivable(id, new UpdateReceivable("200.50", ReceivableType.CHEQUE_PRE_DATADO,
                LocalDate.of(2027, 1, 14), PaymentCurrency.USD));
        assertEquals(id, edited.id()); assertEquals(assignor, edited.assignorId()); assertEquals("FIXED", edited.titleCode());
        assertEquals("200.50", edited.faceValue()); assertEquals(ReceivableType.CHEQUE_PRE_DATADO, edited.type());
        assertEquals(LocalDate.of(2027, 1, 14), edited.dueDate()); assertEquals(PaymentCurrency.USD, edited.paymentCurrency());
        assertEquals(Receivable.Status.PENDING, edited.status()); assertEquals(now, edited.updatedAt());
        assertEquals(now.minusSeconds(60), edited.createdAt()); verify(receivables).flush(); verifyNoInteractions(rates);
    }

    @Test
    void editRejectsMissingAndSettledTitles() {
        UUID id = UUID.randomUUID();
        var update = new UpdateReceivable("200.00", ReceivableType.CHEQUE_PRE_DATADO, LocalDate.of(2027, 1, 14), PaymentCurrency.BRL);
        assertEquals(BusinessException.Code.NOT_FOUND, assertThrows(BusinessException.class, () -> service.updateReceivable(id, update)).getCode());
        var title = new Receivable(id, UUID.randomUUID(), "FIXED", ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("100.00"), LocalDate.of(2026, 12, 14), PaymentCurrency.BRL, now);
        title.markSettled(now); when(receivables.findForSettlement(id)).thenReturn(Optional.of(title));
        assertEquals(BusinessException.Code.ALREADY_SETTLED, assertThrows(BusinessException.class, () -> service.updateReceivable(id, update)).getCode());
        assertEquals(new BigDecimal("100.00"), title.getFaceValue()); verify(receivables, never()).flush();
    }

    @Test
    void updatedBaseRateIsReadForEverySimulation() {
        service.simulate(simulate(PaymentCurrency.BRL));
        when(config.getBaseRate()).thenReturn(new BigDecimal("0.02"));
        var next = service.simulate(simulate(PaymentCurrency.BRL));
        assertEquals("0.0350000000", next.effectiveRate());
        assertNotEquals("92859.94", next.finalAmount());
        verify(configs, times(2)).findById(1);
    }

    @Test
    void usdUsesCurrentRateAndKeepsItsAuditIdentity() {
        var exchange = rate(now);
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now))
                .thenReturn(Optional.of(exchange));
        var result = service.simulate(simulate(PaymentCurrency.USD));
        assertEquals("18571.99", result.finalAmount());
        assertEquals(exchange.getId(), result.exchangeRateId());
        assertEquals(now, result.exchangeRateValidFrom());
        verifyNoInteractions(receivables);
        verify(rates, never()).saveAndFlush(any());
    }

    @Test
    void missingCurrentRateBlocksUsd() {
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now))
                .thenReturn(Optional.empty());
        var error = assertThrows(BusinessException.class, () -> service.simulate(simulate(PaymentCurrency.USD)));
        assertEquals(BusinessException.Code.EXCHANGE_RATE_UNAVAILABLE, error.getCode());
    }

    @Test
    void exactExpirationBlocksWithoutFallback() {
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now))
                .thenReturn(Optional.of(rate(now.minus(Duration.ofHours(24)))));
        var error = assertThrows(BusinessException.class, () -> service.simulate(simulate(PaymentCurrency.USD)));
        assertEquals(BusinessException.Code.EXCHANGE_RATE_EXPIRED, error.getCode());
        verify(rates).findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now);
        verifyNoMoreInteractions(rates);
    }

    @Test
    void oneMicrosecondBeforeExpirationIsValid() {
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now))
                .thenReturn(Optional.of(rate(now.minus(Duration.ofHours(24)).plusNanos(1000))));
        assertEquals("18571.99", service.simulate(simulate(PaymentCurrency.USD)).finalAmount());
    }

    @Test
    void queryCutoffNeverRoundsIntoAFutureMicrosecond() {
        var clock = Clock.fixed(now.plusNanos(999), ZoneOffset.UTC);
        service = new CreditService(assignors, configs, rates, receivables, clock);
        when(rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now))
                .thenReturn(Optional.of(rate(now)));
        assertEquals(now, service.simulate(simulate(PaymentCurrency.USD)).calculatedAt());
        verify(rates).findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now);
    }

    @Test
    void referenceDateUsesSaoPauloAndOneClockRead() {
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-09-15T02:59:59Z"), Instant.parse("2026-09-15T03:00:00Z"));
        service = new CreditService(assignors, configs, rates, receivables, clock);
        var result = service.simulate(new Simulate("1", ReceivableType.CHEQUE_PRE_DATADO,
                LocalDate.of(2026, 9, 14), PaymentCurrency.BRL));
        assertEquals(0, result.termMonths());
        assertEquals(LocalDate.of(2026, 9, 14), result.referenceDate());
        verify(clock).instant();
    }

    @Test
    void usdRegistrationIsPendingAndDoesNotRequireExchange() {
        var assignor = UUID.randomUUID();
        when(assignors.existsById(assignor)).thenReturn(true);
        when(receivables.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.createReceivable(new CreateReceivable(assignor, " T-001 ",
                ReceivableType.DUPLICATA_MERCANTIL, "100.00", LocalDate.of(2026, 9, 14), PaymentCurrency.USD));
        assertEquals(Receivable.Status.PENDING, result.status());
        assertEquals("T-001", result.titleCode());
        assertEquals("100.00", result.faceValue());
        verifyNoInteractions(rates);
    }

    @Test
    void unknownAssignorDoesNotPersist() {
        var error = assertThrows(BusinessException.class, () -> service.createReceivable(new CreateReceivable(
                UUID.randomUUID(), "T-001", ReceivableType.DUPLICATA_MERCANTIL, "100",
                LocalDate.of(2026, 9, 14), PaymentCurrency.BRL)));
        assertEquals(BusinessException.Code.NOT_FOUND, error.getCode());
        verifyNoInteractions(receivables);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1.001", "100000000000000000.00"})
    void invalidMoneyDoesNotPersist(String face) {
        assertThrows(IllegalArgumentException.class, () -> service.createReceivable(new CreateReceivable(
                UUID.randomUUID(), "T-001", ReceivableType.DUPLICATA_MERCANTIL, face,
                LocalDate.of(2026, 9, 14), PaymentCurrency.BRL)));
        verifyNoInteractions(receivables);
    }

    @Test
    void overdueAndBeyondLimitDoNotPersist() {
        when(config.getMaxTermMonths()).thenReturn(1);
        for (var due : List.of(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 10, 15))) {
            assertThrows(IllegalArgumentException.class, () -> service.createReceivable(new CreateReceivable(
                    UUID.randomUUID(), "T-001", ReceivableType.DUPLICATA_MERCANTIL, "100", due, PaymentCurrency.BRL)));
        }
        verifyNoInteractions(receivables);
    }

    @Test
    void futureExchangeRegistrationNormalizesOffsetWithoutRoundingValue() {
        when(rates.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var result = service.createExchangeRate(new CreateExchangeRate("USD/BRL", "5.1234567890",
                OffsetDateTime.parse("2026-09-15T12:00:00-03:00")));
        assertEquals(Instant.parse("2026-09-15T15:00:00Z"), result.validFrom());
        assertEquals("5.1234567890", result.rate());
    }

    @Test
    void exchangeDoesNotSilentlyRoundSubMicrosecondValidity() {
        assertThrows(IllegalArgumentException.class, () -> service.createExchangeRate(new CreateExchangeRate(
                "USD/BRL", "5", OffsetDateTime.parse("2026-09-15T12:00:00.0000001Z"))));
        verifyNoInteractions(rates);
    }

    @Test
    void paginationIsBoundedAndStable() {
        when(receivables.findByStatus(eq(Receivable.Status.PENDING), any())).thenAnswer(call ->
                new PageImpl<Receivable>(List.of(), call.getArgument(1), 0));
        assertEquals(20, service.receivables(Receivable.Status.PENDING, 0, 20).size());
        verify(receivables).findByStatus(eq(Receivable.Status.PENDING), argThat((Pageable page) ->
                page.getSort().getOrderFor("createdAt").isDescending() && page.getSort().getOrderFor("id").isDescending()));
        assertThrows(IllegalArgumentException.class, () -> service.receivables(null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.receivables(null, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> service.receivables(null, Integer.MAX_VALUE, 100));
    }

    private Simulate simulate(PaymentCurrency currency) {
        return new Simulate("100000.00", ReceivableType.DUPLICATA_MERCANTIL, LocalDate.of(2026, 12, 14), currency);
    }
    private ExchangeRate rate(Instant validFrom) {
        return new ExchangeRate(UUID.randomUUID(), "USD/BRL", new BigDecimal("5.0000000000"), validFrom, now);
    }
}
