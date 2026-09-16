package com.arthurpaiao.creditengine.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exchange_rates")
public class ExchangeRate {
    @Id private UUID id;
    @Column(nullable = false, length = 7) private String currencyPair;
    @Column(nullable = false, precision = 24, scale = 10) private BigDecimal rate;
    @Column(nullable = false) private Instant validFrom;
    @Column(nullable = false) private Instant createdAt;

    protected ExchangeRate() {}
    public ExchangeRate(UUID id, String currencyPair, BigDecimal rate, Instant validFrom, Instant createdAt) {
        this.id = id;
        this.currencyPair = currencyPair;
        this.rate = rate;
        this.validFrom = validFrom;
        this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getCurrencyPair() { return currencyPair; }
    public BigDecimal getRate() { return rate; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getCreatedAt() { return createdAt; }
}
