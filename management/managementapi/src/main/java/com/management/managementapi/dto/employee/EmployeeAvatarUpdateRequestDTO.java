package com.management.managementapi.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Pedido para atualizar o avatar (apenas metadados)")
public record EmployeeAvatarUpdateRequestDTO(
        @NotBlank(message = "avatarUrl é obrigatório")
        @Schema(example = "https://cdn.example.com/avatars/u123.png")
        String avatarUrl
) {}