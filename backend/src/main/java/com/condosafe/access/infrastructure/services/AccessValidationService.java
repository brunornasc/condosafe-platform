package com.condosafe.access.infrastructure.services;

import com.condosafe.access.domain.dtos.AccessEventDTO;
import com.condosafe.access.domain.dtos.AccessValidationRequestDTO;
import com.condosafe.access.domain.dtos.ValidationResponseDTO;
import com.condosafe.access.domain.models.enums.ValidationReason;
import com.condosafe.access.domain.models.interfaces.AccessSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
public class AccessValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(AccessValidationService.class);

    private final QrCodeCryptoService cryptoService;
    private final AntiReplayService antiReplayService;
    private final UserService userService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String accessEventsTopic;

    public AccessValidationService(
            QrCodeCryptoService cryptoService,
            AntiReplayService antiReplayService,
            UserService userService,
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${condosafe.kafka.topics.access-events:access-events}") String accessEventsTopic
    ) {
        this.cryptoService = cryptoService;
        this.antiReplayService = antiReplayService;
        this.userService = userService;
        this.kafkaTemplate = kafkaTemplate;
        this.accessEventsTopic = accessEventsTopic;
    }

    public Mono<ValidationResponseDTO> validate(AccessValidationRequestDTO request) {
        var qr = request.qrCode();

        // 1. Validação temporal (clock drift de até 60s)
        if (cryptoService.isExpired(qr.tms())) {
            publishAuditLog(request, false, ValidationReason.EXPIRED);
            return Mono.just(ValidationResponseDTO.reject(ValidationReason.EXPIRED));
        }

        // 2. Validação cadastral e criptográfica por sujeito (Morador → Visitante)
        return userService.findAccessSubject(qr.sub(), qr.unt())
                .flatMap(subject -> {
                    if (!cryptoService.isValidSignature(qr, subject)) {
                        publishAuditLog(request, false, ValidationReason.INVALID_SIGNATURE);
                        return Mono.just(ValidationResponseDTO.reject(ValidationReason.INVALID_SIGNATURE));
                    }

                    if (!subject.isAccessAllowed()) {
                        publishAuditLog(request, false, ValidationReason.SUBJECT_NOT_ALLOWED);
                        return Mono.just(ValidationResponseDTO.reject(ValidationReason.SUBJECT_NOT_ALLOWED));
                    }

                    // 3. Trava Anti-Replay no Redis (JTI de uso único)
                    return antiReplayService.acquireLock(qr.jti())
                            .flatMap(acquired -> {
                                if (!acquired) {
                                    publishAuditLog(request, false, ValidationReason.REPLAY_ATTACK);
                                    return Mono.just(ValidationResponseDTO.reject(ValidationReason.REPLAY_ATTACK));
                                }

                                // 4. Sucesso: hook pós-acesso (contador de convite) + auditoria
                                return userService.postAccessHook(subject)
                                        .then(Mono.fromCallable(() -> {
                                            publishAuditLog(request, true, ValidationReason.OK);
                                            return ValidationResponseDTO.ok();
                                        }));
                            });
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    publishAuditLog(request, false, ValidationReason.SUBJECT_NOT_FOUND);
                    return ValidationResponseDTO.reject(ValidationReason.SUBJECT_NOT_FOUND);
                }));
    }

    private void publishAuditLog(AccessValidationRequestDTO request, boolean granted, ValidationReason reason) {
        var qr = request.qrCode();
        var event = new AccessEventDTO(
                qr.sub(),
                qr.unt(),
                request.gateId(),
                request.accessType(),
                request.direction(),
                granted,
                reason.name(),
                Instant.now().toEpochMilli()
        );

        kafkaTemplate.send(accessEventsTopic, qr.unt().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Falha ao publicar log de acesso no Kafka para a unidade: {}", qr.unt(), ex);
                    }
                });
    }
}