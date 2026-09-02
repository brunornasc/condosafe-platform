package com.condosafe.access.infrastructure.repositories;

import com.condosafe.access.domain.models.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface UserRepository extends R2dbcRepository<User, UUID> {

    @Query("SELECT * FROM users WHERE id = :userId AND unit_id = :unitId")
    Mono<User> findByIdAndUnitId(UUID userId, UUID unitId);
}