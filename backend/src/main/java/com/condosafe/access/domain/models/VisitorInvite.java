package com.condosafe.access.domain.models;

import com.condosafe.access.domain.models.interfaces.AccessSubject;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("visitor_invites")
public record VisitorInvite(
        @Id
        UUID id,

        @Column("unit_id")
        UUID unitId,

        @Column("resident_id")
        UUID residentId,

        @Column("visitor_name")
        String visitorName,

        @Column("document_number")
        String documentNumber,

        @Column("valid_from")
        Instant validFrom,

        @Column("valid_until")
        Instant validUntil,

        @Column("max_uses")
        int maxUses,

        @Column("used_count")
        int usedCount,

        @Column("status")
        String status,

        @Column("created_at")
        Instant createdAt
) implements AccessSubject {
    @Override public UUID getId() { return id(); }
    @Override public UUID getUnitId() { return unitId(); }
    @Override public boolean isAccessAllowed() { return isValidForNow(Instant.now()); }

    public boolean isValidForNow(Instant now) {
        return "ACTIVE".equalsIgnoreCase(status)
                && (now.isAfter(validFrom) || now.equals(validFrom))
                && now.isBefore(validUntil)
                && usedCount < maxUses;
    }
}