package com.management.managementapi.notifications.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notificação in-app")
public record NotificationResponseDTO(
        UUID id,
        @Schema(description = "Tipo — o frontend usa-o só para escolher o ícone", example = "task_assigned")
        String type,
        String title,
        String body,
        @Schema(description = "Rota do frontend para onde a notificação leva", example = "/backoffice/tasks")
        String link,
        @Schema(description = "Id da entidade que originou a notificação (tarefa, fatura…)")
        UUID entityId,
        @Schema(description = "Nulo enquanto não for lida")
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {}
