package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

class PricingEngineTest {
    private final PricingEngine engine = new PricingEngine(List.of(new DuplicataStrategy(), new ChequeStrategy()), 120);

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "C1,DUPLICATA_MERCANTIL,100000.00,3,BRL,,92859.94,92859.94,7140.06",
        "C2,CHEQUE_PRE_DATADO,25000.00,2,BRL,,23337.77,23337.77,1662.23",
        "C3,DUPLICATA_MERCANTIL,100000.00,3,USD,5.4321,92859.94,17094.67,7140.06"
    })
    void goldenCases(String name, ReceivableType type, String face, int months, PaymentCurrency currency,
                     String rate, String vp, String amount, String discount) {
        var result = engine.calculate(new BigDecimal(face), months, type, new BigDecimal("0.01"), currency,
                rate == null ? null : new BigDecimal(rate));
        assertEquals(new BigDecimal(vp), result.presentValueBrl());
        assertEquals(new BigDecimal(amount), result.finalAmount());
        assertEquals(new BigDecimal(discount), result.discountBrl());
    }

    @Test void zeroTermKeepsFaceValueAndBrlNeedsNoRate() {
        var result = price("10.0000", 0, PaymentCurrency.BRL, null);
        assertEquals(new BigDecimal("10.00"), result.finalAmount());
        assertEquals(new BigDecimal("0.00"), result.discountBrl());
        assertNull(result.exchangeRateUsed());
    }

    @ParameterizedTest @CsvSource({"2.01,1.00", "2.03,1.02"})
    void conversionRoundsTiesToEven(String face, String expected) {
        assertEquals(new BigDecimal(expected), price(face, 0, PaymentCurrency.USD, "2").finalAmount());
    }

    @Test void convertsRoundedBrlRatherThanUnroundedQuotient() {
        // 1/1.025 = 0.975609... -> BRL 0.98 -> USD 0.49; direct conversion would yield 0.48.
        assertEquals(new BigDecimal("0.49"), price("1.00", 1, PaymentCurrency.USD, "2.01").finalAmount());
    }

    @ParameterizedTest @CsvSource({"0,0", "-1,0", "1.001,0", "100000000000000000,0", "10,-1", "10,121"})
    void rejectsInvalidMoneyOrTerm(String face, int months) {
        assertThrows(IllegalArgumentException.class, () -> price(face, months, PaymentCurrency.BRL, null));
    }

    @Test void validatesLimitsRatesAndStrategies() {
        assertDoesNotThrow(() -> price("100", 120, PaymentCurrency.BRL, null));
        assertThrows(IllegalArgumentException.class, () -> price("10", 0, PaymentCurrency.USD, null));
        assertThrows(IllegalArgumentException.class, () -> price("10", 0, PaymentCurrency.USD, "0"));
        assertThrows(IllegalArgumentException.class, () -> price("10", 0, PaymentCurrency.USD, "-1"));
        assertThrows(IllegalArgumentException.class, () -> price("10", 0, PaymentCurrency.USD, "1.00000000001"));
        assertThrows(IllegalArgumentException.class, () -> price("99999999999999999.99", 0, PaymentCurrency.USD, "0.0000000001"));
        assertThrows(IllegalArgumentException.class, () -> engine.calculate(new BigDecimal("1"), 0,
                ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("-0.01"), PaymentCurrency.BRL, null));
        assertThrows(IllegalArgumentException.class, () -> engine.calculate(new BigDecimal("1"), 0,
                null, new BigDecimal("0.01"), PaymentCurrency.BRL, null));
        var missing = new PricingEngine(List.of(new DuplicataStrategy()), 120);
        assertThrows(IllegalArgumentException.class, () -> missing.calculate(new BigDecimal("1"), 0,
                ReceivableType.CHEQUE_PRE_DATADO, BigDecimal.ZERO, PaymentCurrency.BRL, null));
        assertThrows(IllegalArgumentException.class, () -> new PricingEngine(
                List.of(new DuplicataStrategy(), new DuplicataStrategy()), 120));
    }

    private PricingResult price(String face, int months, PaymentCurrency currency, String rate) {
        return engine.calculate(new BigDecimal(face), months, ReceivableType.DUPLICATA_MERCANTIL,
                new BigDecimal("0.01"), currency, rate == null ? null : new BigDecimal(rate));
    }
}
