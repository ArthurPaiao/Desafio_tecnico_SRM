package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import com.arthurpaiao.creditengine.domain.PaymentCurrency;
import com.arthurpaiao.creditengine.api.Contracts.PageView;

@RestController
public class SettlementController {
    private final SettlementService service;
    public SettlementController(SettlementService service) { this.service = service; }

    @PostMapping("/settlements")
    public ResponseEntity<SettlementView> settle(@RequestHeader("Idempotency-Key") UUID key,
                                                @Valid @RequestBody SettlementRequest request) {
        var result = service.settle(key, request);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/settlements/" + result.settlement().id())).body(result.settlement());
    }

    @GetMapping("/settlements/{id}")
    public SettlementView find(@PathVariable UUID id) { return service.find(id); }

    @GetMapping("/settlements")
    public PageView<SettlementView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID assignorId,
            @RequestParam(required = false) PaymentCurrency currency,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(from, to, assignorId, currency, page, size);
    }

    @PostMapping("/receivables/{id}/simulations")
    public ReceivableSimulation simulate(@PathVariable UUID id) { return service.simulate(id); }
}
