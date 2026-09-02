package com.condosafe.access.infrastructure.consumers;

import com.condosafe.access.domain.dtos.AccessEventDTO;
import com.condosafe.access.domain.models.AccessLog;
import com.condosafe.access.infrastructure.repositories.AccessLogRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class AccessEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AccessEventConsumer.class);

    private final AccessLogRepository accessLogRepository;

    public AccessEventConsumer(AccessLogRepository accessLogRepository) {
        this.accessLogRepository = accessLogRepository;
    }

    @KafkaListener(
            topics = "${condosafe.kafka.topics.access-events:access-events}",
            groupId = "${spring.kafka.consumer.group-id:condosafe-core-group}"
    )
    public void consumeAccessEvent(AccessEventDTO event) {
        LOG.info("Processando evento de acesso recebido via Kafka para a unidade: {}", event.unitId());

        AccessLog accessLog = AccessLog.fromEvent(event);

        accessLogRepository.save(accessLog)
                .doOnSuccess(saved -> {
                    if (saved != null) {
                        LOG.debug("Log de acesso persistido no Postgres via R2DBC. ID: {}", saved.id());

                    } else {
                        LOG.debug("Log de acesso persistido no Postgres via R2DBC. ID: NULL ID");

                    }
                })
                .doOnError(error -> LOG.error("Falha ao persistir log de acesso via R2DBC: {}", error.getMessage(), error))
                .subscribe();
    }
}