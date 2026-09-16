package com.arthurpaiao.creditengine.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {
    Optional<ExchangeRate> findFirstByCurrencyPairAndValidFromLessThanEqualOrderByValidFromDesc(
            String currencyPair, Instant now);
    Page<ExchangeRate> findByCurrencyPair(String currencyPair, Pageable pageable);
}
