package com.arthurpaiao.creditengine.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalRules {
    private DecimalRules() {}

    public static BigDecimal require(BigDecimal value, int precision, int scale, boolean positive, String name) {
        if (value == null || (positive ? value.signum() <= 0 : value.signum() < 0)) {
            throw new IllegalArgumentException(name + " inválido");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > scale || normalized.precision() - normalized.scale() > precision - scale) {
            throw new IllegalArgumentException(name + " excede a precisão permitida");
        }
        return value.setScale(scale, RoundingMode.UNNECESSARY);
    }
}
