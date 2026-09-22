package com.condosafe.access.infrastructure.repositories;

import com.condosafe.access.domain.models.VisitorInvite;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface VisitorInviteRepository extends R2dbcRepository<VisitorInvite, UUID> {

    @Query("SELECT * FROM visitor_invites WHERE id = :inviteId AND unit_id = :unitId")
    Mono<VisitorInvite> findByIdAndUnitId(UUID inviteId, UUID unitId);

    @Query("SELECT * FROM visitor_invites WHERE resident_id = :residentId ORDER BY created_at DESC")
    Flux<VisitorInvite> findByResidentIdOrderByCreatedAtDesc(UUID residentId);

    @Query("UPDATE visitor_invites SET used_count = used_count + 1 WHERE id = :inviteId AND used_count < max_uses")
    Mono<Void> incrementUsage(UUID inviteId);

    @Query("UPDATE visitor_invites SET status = 'REVOKED' WHERE id = :inviteId AND unit_id = :unitId")
    Mono<Void> revokeByIdAndUnitId(UUID inviteId, UUID unitId);
}