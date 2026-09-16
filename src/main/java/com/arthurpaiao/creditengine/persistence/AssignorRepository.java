package com.arthurpaiao.creditengine.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AssignorRepository extends JpaRepository<Assignor, UUID> {
    java.util.Optional<Assignor> findByCode(String code);
}
