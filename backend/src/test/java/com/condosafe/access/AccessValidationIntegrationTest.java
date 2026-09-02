package com.condosafe.access;

import com.condosafe.access.domain.dtos.AccessValidationRequestDTO;
import com.condosafe.access.domain.dtos.QrCodeDTO;
import com.condosafe.access.domain.dtos.ValidationResponseDTO;
import com.condosafe.access.domain.models.enums.ValidationReason;
import com.redis.testcontainers.RedisContainer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Testcontainers
class AccessValidationIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:16-3.4")
                    .asCompatibleSubstituteFor("postgres")
            )
            .withDatabaseName("condosafe")
            .withUsername("condosafe")
            .withPassword("condosafe");

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"));

    @Autowired
    private org.springframework.r2dbc.core.DatabaseClient databaseClient;

    @DynamicPropertySource
    static void configureDynamicProperties(DynamicPropertyRegistry registry) {
        // R2DBC
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.r2dbc.pool.initial-size", () -> 2);
        registry.add("spring.r2dbc.pool.max-size", () -> 10);
        registry.add("spring.r2dbc.pool.validation-query", () -> "SELECT 1");

        // Flyway JDBC
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private WebTestClient webTestClient;

    @Value("${condosafe.security.qr-code.secret-key}")
    private String secretKey;

    private UUID residentId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        this.residentId = UUID.randomUUID();
        this.unitId = UUID.randomUUID();
        UUID condoId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        String uniqueEmail = "user-" + residentId + "@condosafe.com";

        databaseClient.sql("""
        INSERT INTO condominiums (id, name, address) 
        VALUES (:condoId, 'Condo Test', 'Rua Teste, 100')
    """).bind("condoId", condoId).then()
                .then(databaseClient.sql("""
        INSERT INTO blocks (id, condominium_id, name) 
        VALUES (:blockId, :condoId, 'Bloco A')
    """).bind("blockId", blockId).bind("condoId", condoId).then())
                .then(databaseClient.sql("""
        INSERT INTO units (id, block_id, unit_number, status) 
        VALUES (:unitId, :blockId, '101', 'ACTIVE')
    """).bind("unitId", unitId).bind("blockId", blockId).then())
                .then(databaseClient.sql("""
        INSERT INTO users (id, keycloak_id, unit_id, full_name, email, role, status) 
        VALUES (:userId, :keycloakId, :unitId, 'Bruno Rodrigues', :email, 'RESIDENT', 'ACTIVE')
    """).bind("userId", residentId)
                        .bind("keycloakId", UUID.randomUUID().toString())
                        .bind("unitId", unitId)
                        .bind("email", uniqueEmail).then())
                .block();
    }
    @Test
    @DisplayName("Deve conceder acesso para um QR Code válido na primeira leitura")
    void shouldGrantAccessForValidQrCode() {
        AccessValidationRequestDTO request = createValidRequest(residentId, unitId, UUID.randomUUID().toString(), Instant.now().getEpochSecond());

        webTestClient.post()
                .uri("/api/v1/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidationResponseDTO.class)
                .value(response -> {
                    assertThat(response)
                            .as("O corpo da resposta de validação não pode ser nulo")
                            .isNotNull();
                    assertThat(response.granted()).isTrue();
                    assertThat(response.reason()).isEqualTo(ValidationReason.OK);
                });
    }

    @Test
    @DisplayName("Deve bloquear segunda leitura do mesmo token como REPLAY_ATTACK (Anti-Replay no Redis)")
    void shouldRejectSecondAttemptAsReplayAttack() {
        String sameJti = UUID.randomUUID().toString();
        long nowTimestamp = Instant.now().getEpochSecond();
        AccessValidationRequestDTO request = createValidRequest(residentId, unitId, sameJti, nowTimestamp);

        // 1ª Tentativa: Sucesso
        webTestClient.post()
                .uri("/api/v1/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidationResponseDTO.class)
                .value(response -> {
                    assertThat(response)
                            .as("O corpo da resposta de validação não pode ser nulo")
                            .isNotNull();
                    assertThat(response.granted()).isTrue();
                });

        // 2ª Tentativa com o mesmo JTI: Bloqueio imediato no Redis
        webTestClient.post()
                .uri("/api/v1/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidationResponseDTO.class)
                .value(response -> {
                    assertThat(response)
                            .as("O corpo da resposta de validação não pode ser nulo")
                            .isNotNull();
                    assertThat(response.granted()).isFalse();
                    assertThat(response.reason()).isEqualTo(ValidationReason.REPLAY_ATTACK);
                });
    }

    @Test
    @DisplayName("Deve rejeitar QR Code adulterado com INVALID_SIGNATURE")
    void shouldRejectTamperedQrCodeSignature() {
        AccessValidationRequestDTO request = createValidRequest(residentId, unitId, UUID.randomUUID().toString(), Instant.now().getEpochSecond());

        // Adulterando a assinatura HMAC
        AccessValidationRequestDTO tamperedRequest = getTamperedRequest(request);

        webTestClient.post()
                .uri("/api/v1/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(tamperedRequest)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidationResponseDTO.class)
                .value(response -> {
                    assertThat(response)
                            .as("O corpo da resposta de validação não pode ser nulo")
                            .isNotNull();
                    assertThat(response.granted()).isFalse();
                    assertThat(response.reason()).isEqualTo(ValidationReason.INVALID_SIGNATURE);
                });
    }

    private static @NonNull AccessValidationRequestDTO getTamperedRequest(AccessValidationRequestDTO request) {
        QrCodeDTO tamperedQr = new QrCodeDTO(
                request.qrCode().sub(),
                request.qrCode().unt(),
                request.qrCode().tms(),
                request.qrCode().jti(),
                "badf00d" + request.qrCode().sig().substring(7)
        );

        return new AccessValidationRequestDTO(
                tamperedQr,
                request.gateId(),
                request.accessType(),
                request.direction()
        );
    }

    @Test
    @DisplayName("Deve rejeitar QR Code expirado por Clock Drift (> 60 segundos no passado)")
    void shouldRejectExpiredQrCode() {
        long expiredTimestamp = Instant.now().minusSeconds(120).getEpochSecond();
        AccessValidationRequestDTO request = createValidRequest(residentId, unitId, UUID.randomUUID().toString(), expiredTimestamp);

        webTestClient.post()
                .uri("/api/v1/access/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(ValidationResponseDTO.class)
                .value(response -> {
                    assertThat(response)
                            .as("O corpo da resposta de validação não pode ser nulo")
                            .isNotNull();
                    assertThat(response.granted()).isFalse();
                    assertThat(response.reason()).isEqualTo(ValidationReason.EXPIRED);
                });
    }

    // Helper para gerar o hash HMAC-SHA256 válido
    private AccessValidationRequestDTO createValidRequest(UUID sub, UUID unt, String jti, long tms) {
        String rawData = String.format("%s:%s:%d:%s", sub, unt, tms, jti);
        String sig = calculateHmacSha256(rawData, secretKey);

        QrCodeDTO qrCode = new QrCodeDTO(sub, unt, tms, jti, sig);
        return new AccessValidationRequestDTO(
                qrCode,
                "GATE_MAIN_TURNSTILE_01",
                "QR_CODE_TOTP",
                "IN"
        );
    }

    private String calculateHmacSha256(String data, String key) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular HMAC de teste", e);
        }
    }
}