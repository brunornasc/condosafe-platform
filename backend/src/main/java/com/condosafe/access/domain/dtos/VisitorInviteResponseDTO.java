package com.condosafe.access.domain.dtos;

import com.condosafe.access.domain.models.VisitorInvite;

import java.time.Instant;
import java.util.UUID;

public record VisitorInviteResponseDTO(
        UUID id,
        UUID unitId,
        UUID residentId,
        String visitorName,
        String documentNumber,
        Instant validFrom,
        Instant validUntil,
        int maxUses,
        int usedCount,
        String status,
        String secretKey,
        String qrPayloadRawJson,
        Instant createdAt
) {
    public static VisitorInviteResponseDTO from(VisitorInvite invite, QrCodeDTO qrPayload) {
        return new VisitorInviteResponseDTO(
                invite.id(),
                invite.unitId(),
                invite.residentId(),
                invite.visitorName(),
                invite.documentNumber(),
                invite.validFrom(),
                invite.validUntil(),
                invite.maxUses(),
                invite.usedCount(),
                invite.status(),
                invite.secretKey(),
                buildRawJson(qrPayload),
                invite.createdAt()
        );
    }

    private static String buildRawJson(QrCodeDTO qrPayload) {
        if (qrPayload == null) {
            return null;
        }
        return "{\"sub\":\"%s\",\"unt\":\"%s\",\"tms\":%d,\"jti\":\"%s\",\"sig\":\"%s\"}"
                .formatted(qrPayload.sub(), qrPayload.unt(), qrPayload.tms(), qrPayload.jti(), qrPayload.sig());
    }
}