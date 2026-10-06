package com.management.managementapi.dto.attendance.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Um justificativo. O `url` é sempre assinado e temporário — nunca a URL bruta do Storage. */
public record AbsenceDocumentResponseDTO(
        UUID id,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        OffsetDateTime uploadedAt,
        String url
) {}
