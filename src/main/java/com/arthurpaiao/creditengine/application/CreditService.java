package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.Contracts.*;
import com.arthurpaiao.creditengine.domain.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import static com.arthurpaiao.creditengine.application.BusinessException.Code.*;
import static com.arthurpaiao.creditengine.application.PageRequests.page;

@Service
@Transactional(readOnly = true)
public class CreditService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<PricingStrategy> STRATEGIES = List.of(new DuplicataStrategy(), new ChequeStrategy());
    private final AssignorRepository assignors;
    private final PricingConfigRepository configs;
    private final ExchangeRateRepository rates;
    private final ReceivableRepository receivables;
    private final Clock clock;

    public CreditService(AssignorRepository assignors, PricingConfigRepository configs,
                         ExchangeRateRepository rates, ReceivableRepository receivables, Clock clock) {
        this.assignors = assignors;
        this.configs = configs;
        this.rates = rates;
        this.receivables = receivables;
        this.clock = clock;
    }

    @Transactional
    public ReceivableView createReceivable(CreateReceivable request) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var face = validateRegistration(request);
        var value = new Receivable(UUID.randomUUID(), request.assignorId(), request.titleCode().strip(),
                request.type(), face, request.dueDate(), request.paymentCurrency(), now);
        // Flush within the transaction: the database arbitrates concurrent duplicate registrations.
        return ReceivableView.from(receivables.saveAndFlush(value));
    }

    public BigDecimal validateRegistration(CreateReceivable request) {
        var face = DecimalRules.require(new BigDecimal(request.faceValue()), 19, 2, true, "Valor de face");
        term(request.dueDate(), clock.instant(), config());
        if (!assignors.existsById(request.assignorId())) {
            throw new BusinessException(NOT_FOUND, "Cedente não encontrado");
        }
        return face;
    }

    @Transactional
    public ReceivableView updateReceivable(UUID id, UpdateReceivable request) {
        // Same row lock as settlement: never edit data used by an in-flight settlement.
        var title = receivables.findForSettlement(id)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Recebível não encontrado"));
        if (title.getStatus() != Receivable.Status.PENDING) {
            throw new BusinessException(ALREADY_SETTLED, "Recebível liquidado não pode ser alterado");
        }
        var face = validateRegistration(new CreateReceivable(title.getAssignorId(), title.getTitleCode(),
                request.type(), request.faceValue(), request.dueDate(), request.paymentCurrency()));
        title.edit(face, request.type(), request.dueDate(), request.paymentCurrency(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        receivables.flush();
        return ReceivableView.from(title);
    }

    @Transactional
    public ExchangeRateView createExchangeRate(CreateExchangeRate request) {
        var rate = DecimalRules.require(new BigDecimal(request.rate()), 24, 10, true, "Cotação");
        var validFrom = request.validFrom().toInstant();
        if (validFrom.getNano() % 1000 != 0 || request.validFrom().getYear() < 1
                || request.validFrom().getYear() > 9999) {
            throw new IllegalArgumentException("Vigência deve estar entre os anos 0001 e 9999 e ter no máximo 6 casas fracionárias");
        }
        return ExchangeRateView.from(rates.saveAndFlush(new ExchangeRate(UUID.randomUUID(), request.pair(),
                rate, validFrom, clock.instant().truncatedTo(ChronoUnit.MICROS))));
    }

    public SimulationView simulate(Simulate request) {
        // PostgreSQL timestamps have microsecond precision; do not round the query cutoff into the future.
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        var config = config();
        var term = term(request.dueDate(), now, config);
        var face = DecimalRules.require(new BigDecimal(request.faceValue()), 19, 2, true, "Valor de face");
        ExchangeRate exchange = null;
        if (request.paymentCurrency() == PaymentCurrency.USD) {
            exchange = rates.findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc("USD/BRL", now)
                    .orElseThrow(() -> new BusinessException(EXCHANGE_RATE_UNAVAILABLE, "Não há cotação USD/BRL vigente"));
            if (!now.isBefore(exchange.getValidFrom().plus(config.getExchangeValidityHours(), ChronoUnit.HOURS))) {
                throw new BusinessException(EXCHANGE_RATE_EXPIRED, "Cotação USD/BRL expirada; cadastre uma nova cotação");
            }
        }
        var result = new PricingEngine(STRATEGIES, config.getMaxTermMonths()).calculate(face,
                term.months(), request.type(), config.getBaseRate(), request.paymentCurrency(),
                exchange == null ? null : exchange.getRate());
        return SimulationView.from(request.type(), term.referenceDate(), request.dueDate(), now, result, exchange);
    }

    public ReceivableView receivable(UUID id) {
        return ReceivableView.from(receivables.findById(id)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Recebível não encontrado")));
    }

    public ExchangeRateView exchangeRate(UUID id) {
        return ExchangeRateView.from(rates.findById(id)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Cotação não encontrada")));
    }

    public PageView<AssignorView> assignors(int page, int size) {
        return PageView.from(assignors.findAll(page(page, size, Sort.by("code"))).map(AssignorView::from));
    }

    public PageView<ReceivableView> receivables(Receivable.Status status, int page, int size) {
        var pageable = page(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = status == null ? receivables.findAll(pageable) : receivables.findByStatus(status, pageable);
        return PageView.from(result.map(ReceivableView::from));
    }

    public PageView<ExchangeRateView> exchangeRates(String pair, int page, int size) {
        if (!"USD/BRL".equals(pair)) throw new IllegalArgumentException("Par suportado: USD/BRL");
        return PageView.from(rates.findByCurrencyPair(pair,
                page(page, size, Sort.by(Sort.Direction.DESC, "validFrom", "id"))).map(ExchangeRateView::from));
    }

    private PricingConfig config() {
        return configs.findById(1).orElseThrow(() -> new IllegalStateException("Configuração de precificação ausente"));
    }

    private TermCalculator.Term term(java.time.LocalDate dueDate, Instant now, PricingConfig config) {
        return new TermCalculator(clock, BUSINESS_ZONE, config.getMaxTermMonths())
                .calculate(now.atZone(BUSINESS_ZONE).toLocalDate(), dueDate);
    }

}
