package com.condosafe.access.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record QrCodeDTO (
        @NotNull UUID sub,
        @NotNull UUID unt,
        @NotNull Long tms,
        @NotBlank String jti,
        @NotBlank String sig
) { }
