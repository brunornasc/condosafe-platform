package com.condosafe.access.domain.models;

import com.condosafe.access.domain.models.interfaces.AccessSubject;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
public record User(
        @Id
        UUID id,

        @Column("keycloak_id")
        String keycloakId,

        @Column("unit_id")
        UUID unitId,

        @Column("full_name")
        String fullName,

        @Column("email")
        String email,

        @Column("role")
        String role,

        @Column("created_at")
        Instant createdAt,

        @Column("status")
        String status
) implements AccessSubject {
    @Override
    public UUID getId() {
        return id();
    }

    @Override
    public UUID getUnitId() {
        return unitId();
    }

    @Override
    public boolean isAccessAllowed() {
        return "ACTIVE".equalsIgnoreCase(status());
    }
}