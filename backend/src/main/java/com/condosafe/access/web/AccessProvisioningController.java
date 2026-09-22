package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.QrProvisioningResponseDTO;
import com.condosafe.access.infrastructure.repositories.UserRepository;
import com.condosafe.access.infrastructure.services.QrCodeCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
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
    private final QrCodeCryptoService cryptoService;

    public AccessProvisioningController(UserRepository userRepository, QrCodeCryptoService cryptoService) {
        this.userRepository = userRepository;
        this.cryptoService = cryptoService;
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

                    // Provisiona a chave DERIVADA do morador — o segredo mestre
                    // nunca sai do servidor nem é gravado no dispositivo.
                    String derivedSecret = cryptoService.deriveResidentSecret(user.id());
                    var response = new QrProvisioningResponseDTO(user.id(), user.unitId(), derivedSecret);
                    LOG.debug("Provisionando segredo derivado de QR Code para o usuário {}", user.id());
                    return ResponseEntity.ok()
                            .cacheControl(CacheControl.noStore())
                            .body(response);
                });
    }
}