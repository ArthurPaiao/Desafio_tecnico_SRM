package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;

public interface PricingStrategy {
    ReceivableType type();
    BigDecimal spread();
}
