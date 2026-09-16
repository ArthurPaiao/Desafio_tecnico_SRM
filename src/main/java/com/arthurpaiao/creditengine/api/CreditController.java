package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.api.Contracts.*;
import com.arthurpaiao.creditengine.application.CreditService;
import com.arthurpaiao.creditengine.persistence.Receivable;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
public class CreditController {
    private final CreditService service;
    public CreditController(CreditService service) { this.service = service; }

    @GetMapping("/assignors")
    public PageView<AssignorView> assignors(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.assignors(page, size);
    }

    @PostMapping("/receivables")
    public ResponseEntity<ReceivableView> createReceivable(@Valid @RequestBody CreateReceivable request) {
        var value = service.createReceivable(request);
        return ResponseEntity.created(URI.create("/receivables/" + value.id())).body(value);
    }

    @GetMapping("/receivables")
    public PageView<ReceivableView> receivables(@RequestParam(required = false) Receivable.Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.receivables(status, page, size);
    }

    @GetMapping("/receivables/{id}")
    public ReceivableView receivable(@PathVariable UUID id) { return service.receivable(id); }

    @PutMapping("/receivables/{id}")
    public ReceivableView updateReceivable(@PathVariable UUID id, @Valid @RequestBody UpdateReceivable request) {
        return service.updateReceivable(id, request);
    }

    @PostMapping("/exchange-rates")
    public ResponseEntity<ExchangeRateView> createExchangeRate(@Valid @RequestBody CreateExchangeRate request) {
        var value = service.createExchangeRate(request);
        return ResponseEntity.created(URI.create("/exchange-rates/" + value.id())).body(value);
    }

    @GetMapping("/exchange-rates")
    public PageView<ExchangeRateView> exchangeRates(@RequestParam(defaultValue = "USD/BRL") String pair,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.exchangeRates(pair, page, size);
    }

    @GetMapping("/exchange-rates/{id}")
    public ExchangeRateView exchangeRate(@PathVariable UUID id) { return service.exchangeRate(id); }

    @PostMapping("/simulations")
    public SimulationView simulate(@Valid @RequestBody Simulate request) { return service.simulate(request); }
}
