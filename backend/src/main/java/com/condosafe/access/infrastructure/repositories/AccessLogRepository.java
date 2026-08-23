package com.condosafe.access.infrastructure.repositories;

import com.condosafe.access.domain.models.AccessLog;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Repository
public interface AccessLogRepository extends R2dbcRepository<AccessLog, UUID> {

    Flux<AccessLog> findByUserIdOrderByAccessedAtDesc(UUID userId);

    Flux<AccessLog> findByUnitIdOrderByAccessedAtDesc(UUID unitId);
}