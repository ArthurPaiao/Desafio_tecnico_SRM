package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;

public final class DuplicataStrategy implements PricingStrategy {
    public ReceivableType type() { return ReceivableType.DUPLICATA_MERCANTIL; }
    public BigDecimal spread() { return new BigDecimal("0.015"); }
}
