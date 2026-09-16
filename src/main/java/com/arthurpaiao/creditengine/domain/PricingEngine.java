package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure calculation. Configuration and eligible exchange rates come from the application layer. */
public final class PricingEngine {
    private final Map<ReceivableType, PricingStrategy> strategies;
    private final int maxTermMonths;

    public PricingEngine(List<PricingStrategy> strategies, int maxTermMonths) {
        if (maxTermMonths < 0 || maxTermMonths > 120) {
            throw new IllegalArgumentException("Limite deve estar entre 0 e 120 meses nesta versão");
        }
        this.maxTermMonths = maxTermMonths;
        var configured = new EnumMap<ReceivableType, PricingStrategy>(ReceivableType.class);
        for (var strategy : Objects.requireNonNull(strategies)) {
            if (configured.put(Objects.requireNonNull(strategy.type()), strategy) != null) {
                throw new IllegalArgumentException("Strategy duplicada: " + strategy.type());
            }
        }
        this.strategies = Map.copyOf(configured);
    }

    public PricingResult calculate(BigDecimal faceValue, int termMonths, ReceivableType type,
                                   BigDecimal baseRate, PaymentCurrency currency, BigDecimal exchangeRate) {
        if (termMonths < 0 || termMonths > maxTermMonths) {
            throw new IllegalArgumentException("Prazo fora do limite configurado");
        }
        if (type == null || !strategies.containsKey(type)) {
            throw new IllegalArgumentException("Tipo de recebível não suportado");
        }
        if (currency == null) throw new IllegalArgumentException("Moeda obrigatória");
        var face = DecimalRules.require(faceValue, 19, 2, true, "Valor de face");
        var base = DecimalRules.require(baseRate, 19, 10, false, "Taxa base");
        var spread = DecimalRules.require(strategies.get(type).spread(), 19, 10, false, "Spread");
        var effective = DecimalRules.require(base.add(spread), 19, 10, false, "Taxa efetiva");
        // No MathContext: preserve the exact denominator and round only the final quotient.
        var denominator = BigDecimal.ONE.add(effective).pow(termMonths);
        var vp = face.divide(denominator, 2, RoundingMode.HALF_EVEN);
        var discount = face.subtract(vp);
        BigDecimal usedRate = null;
        BigDecimal finalAmount = vp;
        if (currency == PaymentCurrency.USD) {
            usedRate = DecimalRules.require(exchangeRate, 24, 10, true, "Cotação");
            finalAmount = vp.divide(usedRate, 2, RoundingMode.HALF_EVEN);
        }
        DecimalRules.require(finalAmount, 19, 2, false, "Valor convertido");
        return new PricingResult(face, termMonths, base, spread, effective, vp, discount,
                currency, usedRate, finalAmount);
    }
}
