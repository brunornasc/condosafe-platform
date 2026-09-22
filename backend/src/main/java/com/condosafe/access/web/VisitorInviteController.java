package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.CreateVisitorInviteRequestDTO;
import com.condosafe.access.domain.dtos.VisitorInviteResponseDTO;
import com.condosafe.access.domain.models.User;
import com.condosafe.access.infrastructure.repositories.UserRepository;
import com.condosafe.access.infrastructure.services.VisitorInviteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/visitors")
public class VisitorInviteController {

    private static final Logger LOG = LoggerFactory.getLogger(VisitorInviteController.class);

    private final VisitorInviteService visitorInviteService;
    private final UserRepository userRepository;

    public VisitorInviteController(VisitorInviteService visitorInviteService, UserRepository userRepository) {
        this.visitorInviteService = visitorInviteService;
        this.userRepository = userRepository;
    }

    @PostMapping("/invite")
    public Mono<ResponseEntity<VisitorInviteResponseDTO>> create(
            @Valid @RequestBody CreateVisitorInviteRequestDTO request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return resolveResident(jwt)
                .flatMap(resident -> visitorInviteService.create(resident.id(), resident.unitId(), request)
                        .doOnSuccess(invite -> LOG.info(
                                "Convite de visitante '{}' criado para a unidade {}",
                                invite.visitorName(), invite.unitId())))
                .map(invite -> ResponseEntity.status(HttpStatus.CREATED).body(invite));
    }

    @GetMapping("/invite")
    public Mono<ResponseEntity<List<VisitorInviteResponseDTO>>> list(@AuthenticationPrincipal Jwt jwt) {
        return resolveResident(jwt)
                .flatMapMany(resident -> visitorInviteService.listByResident(resident.id()))
                .collectList()
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/invite/{inviteId}")
    public Mono<ResponseEntity<Void>> revoke(@PathVariable UUID inviteId, @AuthenticationPrincipal Jwt jwt) {
        return resolveResident(jwt)
                .flatMap(resident -> visitorInviteService.revoke(inviteId, resident.unitId()))
                .then(Mono.fromCallable(() -> ResponseEntity.noContent().build()));
    }

    private Mono<User> resolveResident(Jwt jwt) {
        return userRepository.findByKeycloakId(jwt.getSubject())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Morador não cadastrado na plataforma")));
    }
}