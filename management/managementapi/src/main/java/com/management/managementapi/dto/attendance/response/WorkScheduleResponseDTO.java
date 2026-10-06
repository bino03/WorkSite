package com.management.managementapi.dto.attendance.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkScheduleResponseDTO(
        UUID id,
        String name,
        String notes,
        List<WorkScheduleDayResponseDTO> days,
        /** Minutos de trabalho previstos na semana, somados dos dias. */
        long weeklyMinutes,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime deletedAt
) {}
