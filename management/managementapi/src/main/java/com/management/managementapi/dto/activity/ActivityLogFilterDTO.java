package com.management.managementapi.dto.activity;

import com.management.managementapi.model.enums.ActivityType;
import com.management.managementapi.model.enums.EntityType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivityLogFilterDTO(
    UUID userId,
    ActivityType activityType,
    EntityType entityType,
    OffsetDateTime dateFrom,
    OffsetDateTime dateTo
) {}
