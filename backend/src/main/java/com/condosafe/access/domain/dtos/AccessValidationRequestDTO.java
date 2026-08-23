package com.condosafe.access.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccessValidationRequestDTO(
        @NotNull(message = "O payload do QR Code é obrigatório")
        @Valid
        QrCodeDTO qrCode,

        @NotBlank(message = "O identificador da catraca/leitora é obrigatório")
        String gateId,

        @NotBlank(message = "O tipo de leitora/mídia é obrigatório")
        String accessType, // ex: "QR_CODE_TOTP", "DEEP_LINK_PASS", "BLUETOOTH"

        @NotBlank(message = "O sentido do acesso é obrigatório (IN/OUT)")
        String direction   // "IN" ou "OUT"
) { }