package com.arthurpaiao.creditengine.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "pricing_config")
public class PricingConfig {
    @Id private Integer id;
    @Column(nullable = false, precision = 19, scale = 10) private BigDecimal baseRate;
    @Column(nullable = false) private int maxTermMonths;
    @Column(nullable = false) private int exchangeValidityHours;
    @Column(nullable = false) private Instant updatedAt;

    protected PricingConfig() {}
    public BigDecimal getBaseRate() { return baseRate; }
    public int getMaxTermMonths() { return maxTermMonths; }
    public int getExchangeValidityHours() { return exchangeValidityHours; }
}
