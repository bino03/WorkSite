package com.management.managementapi.dto.activity;

import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityLogResponseDTO(
    UUID id,
    UUID userId,
    String userName,
    ActivityType activityType,
    EntityType entityType,
    UUID entityId,
    String entityName,
    String description,
    OffsetDateTime createdAt
) {}
