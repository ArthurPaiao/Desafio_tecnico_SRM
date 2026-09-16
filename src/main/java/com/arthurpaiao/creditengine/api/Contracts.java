package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.*;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import org.springframework.data.domain.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class Contracts {
    private Contracts() {}

    public record CreateReceivable(
            @NotNull UUID assignorId,
            @NotBlank @Size(max = 100) String titleCode,
            @NotNull ReceivableType type,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[0-9]+(\\.[0-9]+)?")
            @JsonDeserialize(using = DecimalText.class) String faceValue,
            @NotNull LocalDate dueDate,
            @NotNull PaymentCurrency paymentCurrency) {}

    public record UpdateReceivable(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[0-9]+(\\.[0-9]+)?")
            @JsonDeserialize(using = DecimalText.class) String faceValue,
            @NotNull ReceivableType type,
            @NotNull LocalDate dueDate,
            @NotNull PaymentCurrency paymentCurrency) {}

    public record CreateExchangeRate(
            @NotNull @Pattern(regexp = "USD/BRL") String pair,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[0-9]+(\\.[0-9]+)?")
            @JsonDeserialize(using = DecimalText.class) String rate,
            @NotNull OffsetDateTime validFrom) {}

    public record Simulate(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[0-9]+(\\.[0-9]+)?")
            @JsonDeserialize(using = DecimalText.class) String faceValue,
            @NotNull ReceivableType type,
            @NotNull LocalDate dueDate,
            @NotNull PaymentCurrency paymentCurrency) {}

    public record AssignorView(UUID id, String code, String name) {
        public static AssignorView from(Assignor value) {
            return new AssignorView(value.getId(), value.getCode(), value.getName());
        }
    }

    public record ReceivableView(UUID id, UUID assignorId, String titleCode, ReceivableType type,
            String faceValue, LocalDate dueDate, PaymentCurrency paymentCurrency, Receivable.Status status,
            Instant createdAt, Instant updatedAt) {
        public static ReceivableView from(Receivable value) {
            return new ReceivableView(value.getId(), value.getAssignorId(), value.getTitleCode(), value.getType(),
                    value.getFaceValue().toPlainString(), value.getDueDate(), value.getPaymentCurrency(),
                    value.getStatus(), value.getCreatedAt(), value.getUpdatedAt());
        }
    }

    public record ExchangeRateView(UUID id, String pair, String rate, Instant validFrom, Instant createdAt) {
        public static ExchangeRateView from(ExchangeRate value) {
            return new ExchangeRateView(value.getId(), value.getCurrencyPair(), value.getRate().toPlainString(),
                    value.getValidFrom(), value.getCreatedAt());
        }
    }

    public record SimulationView(ReceivableType type, LocalDate referenceDate, LocalDate dueDate,
            Instant calculatedAt, String faceValueBrl, int termMonths, String baseRate, String spread,
            String effectiveRate, String presentValueBrl, String discountBrl, PaymentCurrency paymentCurrency,
            String exchangeRateUsed, String finalAmount, UUID exchangeRateId, Instant exchangeRateValidFrom) {
        public static SimulationView from(ReceivableType type, LocalDate referenceDate, LocalDate dueDate,
                                          Instant now, PricingResult result, ExchangeRate exchange) {
            return new SimulationView(type, referenceDate, dueDate, now, result.faceValueBrl().toPlainString(),
                    result.termMonths(), result.baseRate().toPlainString(), result.spread().toPlainString(),
                    result.effectiveRate().toPlainString(), result.presentValueBrl().toPlainString(),
                    result.discountBrl().toPlainString(), result.paymentCurrency(),
                    result.exchangeRateUsed() == null ? null : result.exchangeRateUsed().toPlainString(),
                    result.finalAmount().toPlainString(), exchange == null ? null : exchange.getId(),
                    exchange == null ? null : exchange.getValidFrom());
        }
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> from(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
