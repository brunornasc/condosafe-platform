package com.condosafe.access.infrastructure.repositories;

import com.condosafe.access.domain.models.VisitorInvite;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VisitorInviteRepository extends R2dbcRepository<VisitorInvite, UUID> {

    @Query("SELECT * FROM visitor_invites WHERE id = :inviteId AND unit_id = :unitId")
    Mono<VisitorInvite> findByIdAndUnitId(UUID inviteId, UUID unitId);

    @Query("UPDATE visitor_invites SET used_count = used_count + 1 WHERE id = :inviteId")
    Mono<Void> incrementUsage(UUID inviteId);
}