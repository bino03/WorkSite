package com.management.managementapi.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Pedido para atualizar apenas a role (PATCH)")
public record EmployeeRolePatchRequestDTO(
        @NotBlank(message = "role é obrigatório")
        @Pattern(regexp = "^[A-Z_]{3,32}$", message = "role deve estar em MAIÚSCULAS (ex: EMPLOYEE, ADMIN)")
        @Schema(example = "ADMIN")
        String role
) {}