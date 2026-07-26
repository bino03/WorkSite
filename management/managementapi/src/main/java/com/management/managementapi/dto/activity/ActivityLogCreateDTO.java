package com.management.managementapi.dto.activity;

import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;

import java.util.UUID;

public record ActivityLogCreateDTO(
    UUID userId,
    String userName,
    ActivityType activityType,
    EntityType entityType,
    UUID entityId,
    String entityName,
    String description,
    String metadata,
    String ipAddress,
    String userAgent
) {}
