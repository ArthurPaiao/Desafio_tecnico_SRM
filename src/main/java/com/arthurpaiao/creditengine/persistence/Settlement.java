package com.arthurpaiao.creditengine.persistence;

import com.arthurpaiao.creditengine.api.Contracts.SimulationView;
import com.arthurpaiao.creditengine.api.SettlementView;
import com.arthurpaiao.creditengine.domain.*;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "settlements")
public class Settlement {
    @Id private UUID id;
    @Column(nullable = false, unique = true) private UUID receivableId;
    @Column(nullable = false, unique = true) private UUID idempotencyKey;
    @Column(nullable = false, length = 64) private String requestFingerprint;
    @Column(nullable = false) private UUID assignorId;
    @Column(nullable = false, length = 200) private String assignorName;
    @Column(nullable = false, length = 100) private String titleCode;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReceivableType type;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal faceValue;
    @Column(nullable = false) private LocalDate dueDate;
    @Column(nullable = false) private LocalDate referenceDate;
    @Column(nullable = false) private int termMonths;
    @Column(nullable = false, precision = 19, scale = 10) private BigDecimal baseRate;
    @Column(nullable = false, precision = 19, scale = 10) private BigDecimal spread;
    @Column(nullable = false, precision = 19, scale = 10) private BigDecimal effectiveRate;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal presentValueBrl;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal discountBrl;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal finalAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 3) private PaymentCurrency currency;
    @Column private UUID exchangeRateId;
    @Column(precision = 24, scale = 10) private BigDecimal exchangeRateUsed;
    @Column private Instant exchangeRateValidFrom;
    @Column(nullable = false) private Instant settledAt;

    protected Settlement() {}
    public Settlement(Receivable title, String assignorName, UUID key, String fingerprint, SimulationView value) {
        this.id = UUID.randomUUID();
        this.receivableId = title.getId();
        this.idempotencyKey = key;
        this.requestFingerprint = fingerprint;
        this.assignorId = title.getAssignorId();
        this.assignorName = assignorName;
        this.titleCode = title.getTitleCode();
        this.type = value.type();
        this.faceValue = new BigDecimal(value.faceValueBrl());
        this.dueDate = value.dueDate();
        this.referenceDate = value.referenceDate();
        this.termMonths = value.termMonths();
        this.baseRate = new BigDecimal(value.baseRate());
        this.spread = new BigDecimal(value.spread());
        this.effectiveRate = new BigDecimal(value.effectiveRate());
        this.presentValueBrl = new BigDecimal(value.presentValueBrl());
        this.discountBrl = new BigDecimal(value.discountBrl());
        this.finalAmount = new BigDecimal(value.finalAmount());
        this.currency = value.paymentCurrency();
        this.exchangeRateId = value.exchangeRateId();
        this.exchangeRateUsed = value.exchangeRateUsed() == null ? null : new BigDecimal(value.exchangeRateUsed());
        this.exchangeRateValidFrom = value.exchangeRateValidFrom();
        this.settledAt = value.calculatedAt();
    }

    public String getRequestFingerprint() { return requestFingerprint; }
    public SettlementView toView() {
        var calculation = new SimulationView(type, referenceDate, dueDate, settledAt, faceValue.toPlainString(),
                termMonths, baseRate.toPlainString(), spread.toPlainString(), effectiveRate.toPlainString(),
                presentValueBrl.toPlainString(), discountBrl.toPlainString(), currency,
                exchangeRateUsed == null ? null : exchangeRateUsed.toPlainString(), finalAmount.toPlainString(),
                exchangeRateId, exchangeRateValidFrom);
        return new SettlementView(id, receivableId, idempotencyKey, assignorId, assignorName, titleCode, settledAt, calculation);
    }
}
