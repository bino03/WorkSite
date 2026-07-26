package com.management.managementapi.dto.activity;

import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityLogListDTO(
    UUID id,
    String userName,
    ActivityType activityType,
    EntityType entityType,
    String entityName,
    OffsetDateTime createdAt
) {}
