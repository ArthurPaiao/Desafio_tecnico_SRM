package com.arthurpaiao.creditengine.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class TermCalculator {
    private final Clock clock;
    private final ZoneId businessZone;
    private final int maxMonths;

    public TermCalculator(Clock clock, ZoneId businessZone, int maxMonths) {
        this.clock = Objects.requireNonNull(clock);
        this.businessZone = Objects.requireNonNull(businessZone);
        if (maxMonths < 0 || maxMonths > 120) throw new IllegalArgumentException("Limite inválido");
        this.maxMonths = maxMonths;
    }

    public Term calculate(LocalDate dueDate) {
        return calculate(LocalDate.now(clock.withZone(businessZone)), dueDate);
    }

    public Term calculate(LocalDate referenceDate, LocalDate dueDate) {
        if (referenceDate == null || dueDate == null || dueDate.isBefore(referenceDate)) {
            throw new IllegalArgumentException("Vencimento ausente ou anterior à data de referência");
        }
        long months = ChronoUnit.MONTHS.between(YearMonth.from(referenceDate), YearMonth.from(dueDate));
        if (months > maxMonths) throw new IllegalArgumentException("Prazo excede o limite");
        // Always anchor to the original date, never to a previously adjusted anniversary.
        if (referenceDate.plusMonths(months).isBefore(dueDate)) months++;
        if (months > maxMonths) throw new IllegalArgumentException("Prazo excede o limite");
        return new Term(referenceDate, dueDate, (int) months);
    }

    public record Term(LocalDate referenceDate, LocalDate dueDate, int months) {}
}
