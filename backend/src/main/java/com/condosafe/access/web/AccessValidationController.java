package com.condosafe.access.web;

import com.condosafe.access.domain.dtos.AccessValidationRequestDTO;
import com.condosafe.access.domain.dtos.ValidationResponseDTO;
import com.condosafe.access.infrastructure.services.AccessValidationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/access")
public class AccessValidationController {

    private final AccessValidationService accessValidationService;

    public AccessValidationController(AccessValidationService accessValidationService) {
        this.accessValidationService = accessValidationService;
    }

    @PostMapping("/validate")
    public Mono<ResponseEntity<ValidationResponseDTO>> validate(@Valid @RequestBody AccessValidationRequestDTO request) {
        return accessValidationService.validate(request)
                .map(ResponseEntity::ok);
    }
}