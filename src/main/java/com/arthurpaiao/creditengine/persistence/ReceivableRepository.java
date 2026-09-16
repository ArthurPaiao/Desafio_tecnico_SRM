package com.arthurpaiao.creditengine.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface ReceivableRepository extends JpaRepository<Receivable, UUID> {
    Optional<Receivable> findByAssignorIdAndTitleCode(UUID assignorId, String titleCode);
    @Query(value = "SELECT * FROM receivables WHERE id = :id FOR UPDATE NOWAIT", nativeQuery = true)
    Optional<Receivable> findForSettlement(UUID id);

    Page<Receivable> findByStatus(Receivable.Status status, Pageable pageable);
}
