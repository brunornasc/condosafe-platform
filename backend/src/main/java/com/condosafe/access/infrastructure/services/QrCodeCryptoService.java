package com.condosafe.access.infrastructure.services;

import com.condosafe.access.domain.dtos.QrCodeDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class QrCodeCryptoService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TIME_STEP_SECONDS = 30L;

    private final SecretKeySpec keySpec;

    public QrCodeCryptoService(@Value("${condosafe.security.qr-code.secret-key}") String secretKey) {
        this.keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public boolean isExpired(long tokenTimestamp) {
        long currentSlot = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        long tokenSlot = tokenTimestamp / TIME_STEP_SECONDS;

        return Math.abs(currentSlot - tokenSlot) > 1;
    }

    public boolean isValidSignature(QrCodeDTO dto) {
        try {
            String rawData = String.format("%s:%s:%d:%s", dto.sub(), dto.unt(), dto.tms(), dto.jti());

            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(this.keySpec);

            byte[] computedHash = hmac.doFinal(rawData.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    dto.sig().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;

        }
    }
}