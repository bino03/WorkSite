package com.management.managementapi.enterprises.dto.substage;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConstructionSubStageResponseDTO(
    UUID id,
    String name,
    String description,
    UUID stageId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
