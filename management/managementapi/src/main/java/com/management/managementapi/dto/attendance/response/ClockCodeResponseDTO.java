package com.management.managementapi.dto.attendance.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** O QR de picagem de uma obra, para o admin imprimir. {@code url} é o que o QR codifica. */
public record ClockCodeResponseDTO(
        UUID enterpriseId,
        String enterpriseName,
        String url,
        /** Quando foi gerado o QR atual — muda a cada "Gerar novo QR" (V50). */
        OffsetDateTime generatedAt
) {}
