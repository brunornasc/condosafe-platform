package com.condosafe.access.infrastructure.services;

import com.condosafe.access.domain.dtos.CreateVisitorInviteRequestDTO;
import com.condosafe.access.domain.dtos.VisitorInviteResponseDTO;
import com.condosafe.access.domain.models.VisitorInvite;
import com.condosafe.access.infrastructure.repositories.VisitorInviteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class VisitorInviteService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final VisitorInviteRepository visitorInviteRepository;
    private final QrCodeCryptoService cryptoService;

    public VisitorInviteService(VisitorInviteRepository visitorInviteRepository, QrCodeCryptoService cryptoService) {
        this.visitorInviteRepository = visitorInviteRepository;
        this.cryptoService = cryptoService;
    }

    public Mono<VisitorInviteResponseDTO> create(UUID residentId, UUID unitId, CreateVisitorInviteRequestDTO request) {
        if (!request.validUntil().isAfter(request.validFrom())) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "O fim da validade deve ser posterior ao início"));
        }

        String secretKey = generateSecretKey();
        Instant now = Instant.now();

        VisitorInvite invite = new VisitorInvite(
                null,
                unitId,
                residentId,
                request.visitorName(),
                request.documentNumber(),
                request.validFrom(),
                request.validUntil(),
                request.maxUses(),
                0,
                "ACTIVE",
                secretKey,
                now
        );

        return visitorInviteRepository.save(invite)
                .map(saved -> {
                    var qrPayload = cryptoService.generatePayload(
                            saved.id().toString(), saved.unitId().toString(), saved.secretKey());
                    return VisitorInviteResponseDTO.from(saved, qrPayload);
                });
    }

    public Flux<VisitorInviteResponseDTO> listByResident(UUID residentId) {
        return visitorInviteRepository.findByResidentIdOrderByCreatedAtDesc(residentId)
                .map(invite -> VisitorInviteResponseDTO.from(invite, null));
    }

    public Mono<Void> revoke(UUID inviteId, UUID unitId) {
        return visitorInviteRepository.findByIdAndUnitId(inviteId, unitId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Convite de visitante não encontrado")))
                .flatMap(invite -> {
                    if ("REVOKED".equalsIgnoreCase(invite.status())) {
                        return Mono.empty();
                    }
                    return visitorInviteRepository.revokeByIdAndUnitId(inviteId, unitId);
                });
    }

    private static String generateSecretKey() {
        byte[] secret = new byte[32];
        SECURE_RANDOM.nextBytes(secret);
        return HexFormat.of().formatHex(secret);
    }
}