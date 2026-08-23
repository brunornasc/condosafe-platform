package com.condosafe.access.domain.dtos;

import com.condosafe.access.domain.models.ValidationReason;

public record ValidationResponseDTO(
        Boolean granted,
        ValidationReason reason
) {
    public static ValidationResponseDTO ok() {
        return new ValidationResponseDTO(true, ValidationReason.OK);
    }

    public static ValidationResponseDTO reject(ValidationReason reason) {
        return new ValidationResponseDTO(false, reason);
    }
}
