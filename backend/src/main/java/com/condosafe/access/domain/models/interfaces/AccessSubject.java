package com.condosafe.access.domain.models.interfaces;

import java.util.UUID;

public interface AccessSubject {
    UUID getId();
    UUID getUnitId();
    boolean isAccessAllowed();
}