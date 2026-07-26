package com.management.managementapi.dto.employee;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Utilizador para seleção em pickers (ex.: atribuir tarefa) — só os campos necessários")
public record EmployeeAssignableDTO(
        @Schema(description = "ID do profile") UUID id,
        @Schema(description = "Nome") String name,
        @Schema(description = "Email (de auth.users, read-only)") String email,
        @Schema(description = "Role no sistema", example = "EMPLOYEE") String role
) {}
