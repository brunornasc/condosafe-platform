package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.QrProvisioningResponseDTO;
import com.condosafe.access.infrastructure.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/access")
public class AccessProvisioningController {

    private static final Logger LOG = LoggerFactory.getLogger(AccessProvisioningController.class);

    private final UserRepository userRepository;
    private final String qrCodeSecretKey;

    public AccessProvisioningController(
            UserRepository userRepository,
            @Value("${condosafe.security.qr-code.secret-key}") String qrCodeSecretKey
    ) {
        this.userRepository = userRepository;
        this.qrCodeSecretKey = qrCodeSecretKey;
    }

    @GetMapping("/qr-token")
    public Mono<ResponseEntity<QrProvisioningResponseDTO>> provision(@AuthenticationPrincipal Jwt jwt) {
        return userRepository.findByKeycloakId(jwt.getSubject())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Usuário não cadastrado na plataforma")))
                .map(user -> {
                    if (user.unitId() == null) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Usuário sem unidade vinculada");
                    }
                    var response = new QrProvisioningResponseDTO(user.id(), user.unitId(), qrCodeSecretKey);
                    LOG.debug("Provisionando segredo de QR Code para o usuário {}", user.id());
                    return ResponseEntity.ok(response);
                });
    }
}