package com.management.managementapi.enterprises.dto.stage;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ConstructionStageResponseDTO(
    UUID id,
    String name,
    String description,
    UUID enterpriseId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
