package com.management.managementapi.enterprises.dto.substage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConstructionSubStageDTO(
    @NotBlank(message = "Nome da sub-etapa é obrigatório") String name,
    String description,
    @NotNull(message = "Etapa é obrigatória") UUID stageId
) {}
