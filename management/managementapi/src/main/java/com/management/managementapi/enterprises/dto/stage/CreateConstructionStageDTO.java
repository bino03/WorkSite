package com.management.managementapi.enterprises.dto.stage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateConstructionStageDTO(
    @NotBlank(message = "Nome da etapa é obrigatório") String name,
    String description,
    @NotNull(message = "Empresa é obrigatória") UUID enterpriseId
) {}
