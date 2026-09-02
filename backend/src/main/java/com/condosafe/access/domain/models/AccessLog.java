package com.condosafe.access.domain.models;

import com.condosafe.access.domain.dtos.AccessEventDTO;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("access_logs")
public record AccessLog(
        @Id
        UUID id,

        @Column("user_id")
        UUID userId,

        @Column("unit_id")
        UUID unitId,

        @Column("gate_id")
        String gateId,

        @Column("access_type")
        String accessType,

        @Column("direction")
        String direction,

        @Column("granted")
        boolean granted,

        @Column("reason")
        String reason,

        @Column("accessed_at")
        Instant accessedAt
) {
    public static AccessLog fromEvent(AccessEventDTO event) {
        return new AccessLog(
                null,
                event.userId(),
                event.unitId(),
                event.gateId(),
                event.accessType(),
                event.direction(),
                event.granted(),
                event.reason(),
                event.accessedAt() != null ? Instant.ofEpochMilli(event.accessedAt()) : Instant.now()
        );
    }
}