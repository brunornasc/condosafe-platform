package com.condosafe.shared.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/me")
public class UserProfileController {

    @GetMapping
    public Mono<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return Mono.just(Map.of(
                "subject", Objects.requireNonNull(jwt.getSubject()),
                "preferred_username", Objects.requireNonNull(jwt.getClaimAsString("preferred_username")),
                "email", Objects.requireNonNull(jwt.getClaimAsString("email")),
                "roles", jwt.getClaimAsMap("realm_access") != null ? Objects.requireNonNull(jwt.getClaimAsMap("realm_access")).get("roles") : "NONE"
        ));
    }
}