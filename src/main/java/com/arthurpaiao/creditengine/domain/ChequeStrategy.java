package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;

public final class ChequeStrategy implements PricingStrategy {
    public ReceivableType type() { return ReceivableType.CHEQUE_PRE_DATADO; }
    public BigDecimal spread() { return new BigDecimal("0.025"); }
}
