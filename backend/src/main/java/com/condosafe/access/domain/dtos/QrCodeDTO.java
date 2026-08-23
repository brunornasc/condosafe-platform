package com.condosafe.access.domain.dtos;

import module java.base;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record QrCodeDTO (
        @NotNull UUID sub,
        @NotNull UUID unt,
        @NotNull Long tms,
        @NotBlank String jti,
        @NotBlank String sig
) { }
