package com.condosafe.access.domain.dtos;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateVisitorInviteRequestDTO(
        @NotBlank(message = "O nome do visitante é obrigatório")
        String visitorName,

        String documentNumber,

        @NotNull(message = "O início da validade é obrigatório")
        Instant validFrom,

        @NotNull(message = "O fim da validade é obrigatório")
        @Future(message = "O fim da validade deve ser no futuro")
        Instant validUntil,

        @NotNull(message = "O número máximo de usos é obrigatório")
        @Min(value = 1, message = "O convite deve permitir pelo menos 1 uso")
        @Max(value = 100, message = "O convite não pode exceder 100 usos")
        Integer maxUses
) { }