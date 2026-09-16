package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.Contracts.SimulationView;
import com.arthurpaiao.creditengine.api.ExpectedConditions;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.Receivable;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ExpectedConditionsTest {
    private final Receivable title = new Receivable(UUID.randomUUID(), UUID.randomUUID(), "T-1",
            ReceivableType.DUPLICATA_MERCANTIL, new BigDecimal("100000.00"), LocalDate.of(2026, 12, 14),
            PaymentCurrency.USD, Instant.parse("2026-09-14T15:00:00Z"));

    @Test
    void sameEconomicConditionsDoNotDependOnExchangeIdentityOrTimestamp() {
        var expected = ExpectedConditions.from(title, simulation("5.0000000000", "0.0250000000", 3));
        assertTrue(expected.matches(title, simulation("5.0", "0.025", 3)));
    }

    @Test
    void changedRateTermOrTitleRequireConfirmation() {
        var expected = ExpectedConditions.from(title, simulation("5", "0.025", 3));
        assertFalse(expected.matches(title, simulation("6", "0.025", 3)));
        assertFalse(expected.matches(title, simulation("5", "0.026", 3)));
        assertFalse(expected.matches(title, simulation("5", "0.025", 2)));
        var other = new Receivable(title.getId(), title.getAssignorId(), "T-2", title.getType(),
                title.getFaceValue(), title.getDueDate(), title.getPaymentCurrency(), title.getCreatedAt());
        assertFalse(expected.matches(other, simulation("5", "0.025", 3)));
    }

    private SimulationView simulation(String exchange, String effective, int term) {
        return new SimulationView(title.getType(), LocalDate.of(2026, 9, 14), title.getDueDate(), Instant.now(),
                "100000.00", term, "0.0100000000", "0.0150000000", effective, "92859.94", "7140.06",
                PaymentCurrency.USD, exchange, "18571.99", UUID.randomUUID(), Instant.now());
    }
}
