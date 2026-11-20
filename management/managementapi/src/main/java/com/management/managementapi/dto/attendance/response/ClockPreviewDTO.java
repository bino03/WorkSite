package com.management.managementapi.dto.attendance.response;

import java.util.List;
import java.util.UUID;

import com.management.managementapi.model.enums.TimeDirection;

/**
 * o que a página de picar mostra depois de ler o QR, antes de o funcionário confirmar:
 * a obra, o que vai ser picado, e as picagens de hoje.
 */
public record ClockPreviewDTO(
        UUID enterpriseId,
        String enterpriseName,
        TimeDirection nextDirection,
        List<TimeEntryResponseDTO> today
) {}
