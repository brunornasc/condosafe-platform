package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.AccessEventDTO;
import com.condosafe.access.domain.dtos.AccessValidationRequestDTO;
import com.condosafe.access.domain.models.ValidationReason;
import com.condosafe.access.domain.dtos.ValidationResponseDTO;
import com.condosafe.access.infrastructure.services.AntiReplayService;
import com.condosafe.access.infrastructure.services.QrCodeCryptoService;
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

import module java.base;

@RestController
@RequestMapping("/api/v1/access")
public class AccessValidationController {

    private final Logger LOG = LoggerFactory.getLogger(AccessValidationController.class);

    private final QrCodeCryptoService cryptoService;
    private final AntiReplayService antiReplayService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AccessValidationController(
            QrCodeCryptoService cryptoService,
            AntiReplayService antiReplayService,
            KafkaTemplate<String, Object> kafkaTemplate
    ) {
        this.cryptoService = cryptoService;
        this.antiReplayService = antiReplayService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<ValidationResponseDTO>> validate(@Valid @RequestBody AccessValidationRequestDTO request) {
        var qr = request.qrCode();

        // 1. Validação temporal (Clock Drift)
        if (cryptoService.isExpired(qr.tms())) {
            publishAuditLog(request, false, ValidationReason.EXPIRED);
            return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.EXPIRED)));
        }

        // 2. Validação da assinatura criptográfica HMAC-SHA256
        if (!cryptoService.isValidSignature(qr)) {
            publishAuditLog(request, false, ValidationReason.INVALID_SIGNATURE);
            return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.INVALID_SIGNATURE)));
        }

        // 3. Validação atômica anti-replay no Redis (SET NX EX 35)
        return antiReplayService.acquireLock(qr.jti())
                .flatMap(acquired -> {
                    if (!acquired) {
                        publishAuditLog(request, false, ValidationReason.REPLAY_ATTACK);
                        return Mono.just(ResponseEntity.ok(ValidationResponseDTO.reject(ValidationReason.REPLAY_ATTACK)));
                    }

                    // Acesso concedido: publica evento de auditoria
                    publishAuditLog(request, true, ValidationReason.OK);
                    return Mono.just(ResponseEntity.ok(ValidationResponseDTO.ok()));
                });
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

        // Envio assíncrono com callback não-bloqueante
        kafkaTemplate.send("access-events", qr.unt().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Log estruturado para alerta em observability (Prometheus/Grafana)
                        LOG.error("Falha ao publicar evento de auditoria no Kafka. UnitId: {}, Erro: {}",
                                qr.unt(), ex.getMessage());
                    } else {
                        LOG.debug("Evento de auditoria enfileirado com sucesso no Kafka. Offset: {}",
                                result.getRecordMetadata().offset());
                    }
                });
    }
}