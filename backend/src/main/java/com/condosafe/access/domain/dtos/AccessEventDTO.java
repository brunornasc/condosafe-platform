package com.condosafe.access.domain.dtos;

import java.time.Instant;
import java.util.UUID;

public record AccessEventDTO(
        UUID userId,
        UUID unitId,
        String gateId,
        String accessType,
        String direction,
        boolean granted,
        String reason,
        Long accessedAt
) { }