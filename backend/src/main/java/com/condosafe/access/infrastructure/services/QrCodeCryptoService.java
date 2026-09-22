package com.condosafe.access.infrastructure.services;

import com.condosafe.access.domain.dtos.QrCodeDTO;
import com.condosafe.access.domain.models.VisitorInvite;
import com.condosafe.access.domain.models.interfaces.AccessSubject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class QrCodeCryptoService {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TIME_STEP_SECONDS = 30L;
    private static final String RESIDENT_KEY_LABEL = "resident:";

    private final String masterSecret;
    private final SecretKeySpec keySpec;

    public QrCodeCryptoService(@Value("${condosafe.security.qr-code.secret-key}") String secretKey) {
        this.masterSecret = secretKey;
        this.keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public boolean isExpired(long tokenTimestamp) {
        long currentSlot = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        long tokenSlot = tokenTimestamp / TIME_STEP_SECONDS;

        return Math.abs(currentSlot - tokenSlot) > 1;
    }

    public boolean isValidSignature(QrCodeDTO dto) {
        return isValidSignature(dto, this.masterSecret);
    }

    /**
     * Valida a assinatura HMAC do QR Code usando o segredo do sujeito.
     * - Visitante (VisitorInvite): segredo exclusivo gravado em visitor_invites.
     * - Morador (User): segredo compatilhado derivado do mestre (nunca o segredo
     *   mestre em si), via deriveResidentSecret(subject.getId()).
     */
    public boolean isValidSignature(QrCodeDTO dto, AccessSubject subject) {
        if (subject instanceof VisitorInvite invite && invite.secretKey() != null) {
            return isValidSignature(dto, invite.secretKey());
        }
        return isValidSignature(dto, deriveResidentSecret(subject.getId()));
    }

    /**
     * Deriva um segredo por-morador a partir do segredo mestre:
     * HMAC-SHA256(master, "resident:" + userId) -> hex.
     *
     * O mestre nunca trafega nem é gravado no cliente: cada morador recebe a
     * própria chave derivada (KDF de um passo, estilo HKDF sem HKDF-Extract),
     * o que permite revogação e rotação granular sem expor chaves de terceiros.
     */
    public String deriveResidentSecret(UUID residentId) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(this.keySpec);
            byte[] derived = hmac.doFinal((RESIDENT_KEY_LABEL + residentId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(derived);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao derivar segredo do morador", e);
        }
    }

    public boolean isValidSignature(QrCodeDTO dto, String secret) {
        try {
            String rawData = String.format("%s:%s:%d:%s", dto.sub(), dto.unt(), dto.tms(), dto.jti());

            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

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

    /**
     * Gera um payload de QR Code assinado (espelho do crypto-bridge nativo):
     * HMAC-SHA256 hex sobre "sub:unt:tms:jti" com o segredo informado.
     */
    public QrCodeDTO generatePayload(String subjectId, String unitId, String secret) {
        long tms = Instant.now().getEpochSecond();
        String jti = UUID.randomUUID().toString();

        try {
            String rawData = String.format("%s:%s:%d:%s", subjectId, unitId, tms, jti);

            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));

            byte[] computedHash = hmac.doFinal(rawData.getBytes(StandardCharsets.UTF_8));
            String sig = HexFormat.of().formatHex(computedHash);

            return new QrCodeDTO(
                    UUID.fromString(subjectId),
                    UUID.fromString(unitId),
                    tms,
                    jti,
                    sig
            );
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar payload de QR Code", e);
        }
    }
}