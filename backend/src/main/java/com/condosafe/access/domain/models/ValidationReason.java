package com.condosafe.access.domain.models;

public enum ValidationReason {
    OK,
    REPLAY_ATTACK,
    INVALID_SIGNATURE,
    EXPIRED;
}
