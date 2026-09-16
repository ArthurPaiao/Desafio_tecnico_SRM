package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.api.Contracts.SimulationView;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.Receivable;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Only economic conditions participate; quotation identity and clock timestamps do not. */
public record ExpectedConditions(
        @NotNull UUID assignorId, @NotBlank @Size(max = 100) String titleCode,
        @NotNull ReceivableType type, @NotNull LocalDate dueDate,
        @NotNull @Min(0) @Max(120) Integer termMonths, @NotNull PaymentCurrency paymentCurrency,
        @JsonDeserialize(using = DecimalText.class) String faceValueBrl,
        @JsonDeserialize(using = DecimalText.class) String effectiveRate,
        @JsonDeserialize(using = DecimalText.class) String presentValueBrl,
        @JsonDeserialize(using = DecimalText.class) String discountBrl,
        @JsonDeserialize(using = DecimalText.class) String finalAmount,
        @JsonDeserialize(using = DecimalText.class) String exchangeRateUsed) {

    public ExpectedConditions {
        faceValueBrl = decimal(faceValueBrl, 19, 2, true);
        effectiveRate = decimal(effectiveRate, 19, 10, false);
        presentValueBrl = decimal(presentValueBrl, 19, 2, false);
        discountBrl = decimal(discountBrl, 19, 2, false);
        finalAmount = decimal(finalAmount, 19, 2, false);
        if (paymentCurrency == PaymentCurrency.USD) {
            exchangeRateUsed = decimal(exchangeRateUsed, 24, 10, true);
        } else if (exchangeRateUsed != null) {
            throw new IllegalArgumentException("Condições BRL não devem conter cotação");
        }
    }

    public static ExpectedConditions from(Receivable title, SimulationView simulation) {
        return new ExpectedConditions(title.getAssignorId(), title.getTitleCode(), simulation.type(),
                simulation.dueDate(), simulation.termMonths(), simulation.paymentCurrency(), simulation.faceValueBrl(),
                simulation.effectiveRate(), simulation.presentValueBrl(), simulation.discountBrl(),
                simulation.finalAmount(), simulation.exchangeRateUsed());
    }

    public boolean matches(Receivable title, SimulationView simulation) {
        return equals(from(title, simulation));
    }

    private static String decimal(String value, int precision, int scale, boolean positive) {
        if (value == null || value.length() > 64 || !value.matches("[0-9]+(\\.[0-9]+)?")) {
            throw new IllegalArgumentException("Condição decimal obrigatória: informe texto com ponto decimal");
        }
        return DecimalRules.require(new BigDecimal(value), precision, scale, positive, "Condição esperada").toPlainString();
    }
}
