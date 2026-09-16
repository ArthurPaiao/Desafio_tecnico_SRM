package com.arthurpaiao.creditengine.application;

import com.arthurpaiao.creditengine.api.*;
import com.arthurpaiao.creditengine.api.Contracts.*;
import com.arthurpaiao.creditengine.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.ArrayList;
import java.time.Instant;
import java.time.OffsetDateTime;
import com.arthurpaiao.creditengine.domain.PaymentCurrency;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import static com.arthurpaiao.creditengine.application.PageRequests.page;
import static com.arthurpaiao.creditengine.application.BusinessException.Code.*;

@Service
public class SettlementService {
    private static final JsonMapper REQUEST_MAPPER = JsonMapper.builder().build();
    private final SettlementRepository settlements;
    private final ReceivableRepository titles;
    private final AssignorRepository assignors;
    private final CreditService credit;

    public SettlementService(SettlementRepository settlements, ReceivableRepository titles,
                             AssignorRepository assignors, CreditService credit) {
        this.settlements = settlements;
        this.titles = titles;
        this.assignors = assignors;
        this.credit = credit;
    }

    public record Outcome(SettlementView settlement, boolean replayed) {}

    @Transactional
    public Outcome settle(UUID key, SettlementRequest request) {
        // Transaction-scoped, cross-process lock. Rare 64-bit collisions only cause a safe busy response.
        if (!settlements.tryLockIntent(key.getMostSignificantBits() ^ key.getLeastSignificantBits())) {
            throw new BusinessException(OPERATION_IN_PROGRESS, "Tentativa em andamento; repita a mesma chave e pedido");
        }
        var fingerprint = fingerprint(request);
        var existing = settlements.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestFingerprint().equals(fingerprint)) {
                throw new BusinessException(IDEMPOTENCY_CONFLICT, "Chave já utilizada com outro pedido");
            }
            return new Outcome(existing.get().toView(), true);
        }
        var title = titles.findForSettlement(request.receivableId())
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Recebível não encontrado"));
        requirePending(title);
        var current = calculate(title);
        if (!request.expectedConditions().equals(current.expectedConditions())) {
            throw new ConditionsChangedException(current);
        }
        var assignor = assignors.findById(title.getAssignorId())
                .orElseThrow(() -> new IllegalStateException("Cedente do título ausente"));
        var saved = settlements.saveAndFlush(new Settlement(title, assignor.getName(), key, fingerprint, current.simulation()));
        title.markSettled(current.simulation().calculatedAt());
        titles.flush();
        return new Outcome(saved.toView(), false);
    }

    @Transactional(readOnly = true)
    public ReceivableSimulation simulate(UUID receivableId) {
        var title = titles.findById(receivableId)
                .orElseThrow(() -> new BusinessException(NOT_FOUND, "Recebível não encontrado"));
        requirePending(title);
        return calculate(title);
    }

    @Transactional(readOnly = true)
    public SettlementView find(UUID id) {
        return settlements.findById(id).orElseThrow(() -> new BusinessException(NOT_FOUND, "Liquidação não encontrada")).toView();
    }

    private ReceivableSimulation calculate(Receivable title) {
        var simulation = credit.simulate(new Simulate(title.getFaceValue().toPlainString(), title.getType(),
                title.getDueDate(), title.getPaymentCurrency()));
        return new ReceivableSimulation(ReceivableView.from(title), simulation, ExpectedConditions.from(title, simulation));
    }

    @Transactional(readOnly = true)
    public PageView<SettlementView> list(OffsetDateTime from, OffsetDateTime to, UUID assignorId,
                                         PaymentCurrency currency, int page, int size) {
        var start = boundary(from);
        var end = boundary(to);
        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("Início deve ser anterior ao fim do período");
        }
        var pageable = page(page, size, Sort.by(Sort.Direction.DESC, "settledAt", "id"));
        Specification<Settlement> filter = (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (start != null) predicates.add(builder.greaterThanOrEqualTo(root.<Instant>get("settledAt"), start));
            if (end != null) predicates.add(builder.lessThan(root.<Instant>get("settledAt"), end));
            if (assignorId != null) predicates.add(builder.equal(root.get("assignorId"), assignorId));
            if (currency != null) predicates.add(builder.equal(root.get("currency"), currency));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return PageView.from(settlements.findAll(filter, pageable).map(Settlement::toView));
    }

    private Instant boundary(OffsetDateTime value) {
        if (value == null) return null;
        if (value.getYear() < 1 || value.getYear() > 9999 || value.getNano() % 1000 != 0) {
            throw new IllegalArgumentException("Período deve usar anos 0001 a 9999 e precisão máxima de microssegundos");
        }
        return value.toInstant();
    }

    private void requirePending(Receivable title) {
        if (title.getStatus() != Receivable.Status.PENDING) {
            throw new BusinessException(ALREADY_SETTLED, "Recebível já liquidado; não crie outra intenção de pagamento");
        }
    }

    private String fingerprint(SettlementRequest request) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(REQUEST_MAPPER.writeValueAsBytes(request)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponível", impossible);
        }
    }
}
