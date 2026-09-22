package com.condosafe.access.domain.dtos;

import java.util.UUID;

/**
 * Payload de provisionamento devolvido ao app do morador ("access/qr-token").
 * O SecretKey é armazenado no SecureStorage/Vault do dispositivo e usado para
 * assinar o QR Code offline via @condosafe/crypto-bridge.
 */
public record QrProvisioningResponseDTO(
        UUID userId,
        UUID unitId,
        String secretKey
) { }