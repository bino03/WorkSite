package com.management.managementapi.enterprises.dto.expense;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ConstructionExpenseResponseDTO(
    UUID id,
    String name,
    BigDecimal price,
    UUID subStageId,
    String invoiceUrl,
    String originalFilename,
    String mimeType,
    Long sizeBytes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
