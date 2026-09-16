package com.arthurpaiao.creditengine.persistence;

import com.arthurpaiao.creditengine.domain.PaymentCurrency;
import com.arthurpaiao.creditengine.domain.ReceivableType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "receivables")
public class Receivable {
    public enum Status { PENDING, SETTLED }

    @Id private UUID id;
    @Column(nullable = false) private UUID assignorId;
    @Column(nullable = false, length = 100) private String titleCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private ReceivableType type;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal faceValue;
    @Column(nullable = false) private LocalDate dueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3) private PaymentCurrency paymentCurrency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected Receivable() {}
    public Receivable(UUID id, UUID assignorId, String titleCode, ReceivableType type,
                      BigDecimal faceValue, LocalDate dueDate, PaymentCurrency paymentCurrency, Instant now) {
        this.id = id;
        this.assignorId = assignorId;
        this.titleCode = titleCode;
        this.type = type;
        this.faceValue = faceValue;
        this.dueDate = dueDate;
        this.paymentCurrency = paymentCurrency;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }
    public UUID getId() { return id; }
    public void markSettled(Instant settledAt) {
        if (status != Status.PENDING) throw new IllegalStateException("Recebível já liquidado");
        status = Status.SETTLED;
        updatedAt = settledAt;
    }
    public UUID getAssignorId() { return assignorId; }
    public void edit(BigDecimal faceValue, ReceivableType type, LocalDate dueDate, PaymentCurrency currency, Instant now) {
        if (status != Status.PENDING) throw new IllegalStateException("Recebível já liquidado");
        this.faceValue = faceValue;
        this.type = type;
        this.dueDate = dueDate;
        this.paymentCurrency = currency;
        this.updatedAt = now;
    }
    public String getTitleCode() { return titleCode; }
    public ReceivableType getType() { return type; }
    public BigDecimal getFaceValue() { return faceValue; }
    public LocalDate getDueDate() { return dueDate; }
    public PaymentCurrency getPaymentCurrency() { return paymentCurrency; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
