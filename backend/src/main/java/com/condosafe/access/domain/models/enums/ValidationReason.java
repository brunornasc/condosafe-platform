package com.condosafe.access.domain.models.enums;

public enum ValidationReason {
    OK,
    REPLAY_ATTACK,
    INVALID_SIGNATURE,
    EXPIRED;
}
