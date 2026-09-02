package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.AccessEventDTO;
import com.condosafe.access.domain.dtos.AccessValidationRequestDTO;
import com.condosafe.access.domain.dtos.ValidationResponseDTO;
import com.condosafe.access.domain.models.interfaces.AccessSubject;
import com.condosafe.access.domain.models.enums.ValidationReason;
import com.condosafe.access.infrastructure.services.AntiReplayService;
import com.condosafe.access.infrastructure.services.QrCodeCryptoService;
import com.condosafe.access.infrastructure.services.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/access")
public class AccessValidationController {

    private static final Logger LOG = LoggerFactory.getLogger(AccessValidationController.class);

    private final QrCodeCryptoService cryptoService;
    private final AntiReplayService antiReplayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserService userService;

    public AccessValidationController(
            QrCodeCryptoService cryptoService,
            AntiReplayService antiReplayService,
            KafkaTemplate<String, Object> kafkaTemplate,
            UserService userService
    ) {
        this.cryptoService = cryptoService;
        this.antiReplayService = antiReplayService;
        this.kafkaTemplate = kafkaTemplate;
        this.userService = userService;
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<ValidationResponseDTO>> validate(@Valid @RequestBody AccessValidationRequestDTO request) {
        var qr = request.qrCode();

        // 1. Validação temporal
        if (cryptoService.isExpired(qr.tms())) {
            publishAuditLog(request, false, ValidationReason.EXPIRED);
            return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.EXPIRED)));
        }

        // 2. Validação criptográfica HMAC
        if (!cryptoService.isValidSignature(qr)) {
            publishAuditLog(request, false, ValidationReason.INVALID_SIGNATURE);
            return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.INVALID_SIGNATURE)));
        }

        // 3. Validação Cadastral (Morador ou Visitante)
        return userService.findAccessSubject(qr.sub(), qr.unt())
                .filter(AccessSubject::isAccessAllowed)
                .flatMap(subject ->
                        // 4. Trava Anti-Replay no Redis
                        antiReplayService.acquireLock(qr.jti())
                                .flatMap(acquired -> {
                                    if (!acquired) {
                                        publishAuditLog(request, false, ValidationReason.REPLAY_ATTACK);
                                        return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.REPLAY_ATTACK)));
                                    }

                                    // 5. Sucesso: Executa pós-processamento (ex: contador de visitante) e audita
                                    return userService.postAccessHook(subject)
                                            .then(Mono.fromCallable(() -> {
                                                publishAuditLog(request, true, ValidationReason.OK);
                                                return ResponseEntity.ok(ValidationResponseDTO.ok());
                                            }));
                                })
                )
                .defaultIfEmpty(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.INVALID_SIGNATURE)));
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

        kafkaTemplate.send("access-events", qr.unt().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOG.error("Falha ao publicar log de acesso no Kafka para a unidade: {}", qr.unt(), ex);
                    }
                });
    }
}