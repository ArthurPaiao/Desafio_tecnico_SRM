package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;

public record PricingResult(
        BigDecimal faceValueBrl, int termMonths, BigDecimal baseRate, BigDecimal spread,
        BigDecimal effectiveRate, BigDecimal presentValueBrl, BigDecimal discountBrl,
        PaymentCurrency paymentCurrency, BigDecimal exchangeRateUsed, BigDecimal finalAmount) {}
