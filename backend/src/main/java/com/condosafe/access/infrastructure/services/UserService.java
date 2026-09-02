package com.condosafe.access.infrastructure.services;

import com.condosafe.access.domain.models.interfaces.AccessSubject;
import com.condosafe.access.domain.models.VisitorInvite;
import com.condosafe.access.infrastructure.repositories.UserRepository;
import com.condosafe.access.infrastructure.repositories.VisitorInviteRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final VisitorInviteRepository visitorInviteRepository;

    public UserService(UserRepository userRepository, VisitorInviteRepository visitorInviteRepository) {
        this.userRepository = userRepository;
        this.visitorInviteRepository = visitorInviteRepository;
    }

    public Mono<AccessSubject> findAccessSubject(UUID subjectId, UUID unitId) {
        return userRepository.findByIdAndUnitId(subjectId, unitId)
                .cast(AccessSubject.class)
                .switchIfEmpty(Mono.defer(() ->
                        visitorInviteRepository.findByIdAndUnitId(subjectId, unitId)
                                .cast(AccessSubject.class)
                ));
    }

    public Mono<Void> postAccessHook(AccessSubject subject) {
        if (subject instanceof VisitorInvite invite) {
            return visitorInviteRepository.incrementUsage(invite.id());
        }
        return Mono.empty();
    }
}