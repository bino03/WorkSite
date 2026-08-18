package com.management.managementapi.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

@Schema(description = "Funcionário")
public record EmployeeResponseDTO(
        @Schema(description = "ID do profile (funcionário)", example = "f3a2c0be-3d88-4a6e-9d3a-3d6e53f0a111")
        UUID id,
        @Schema(description = "ID do utilizador em auth.users (se existir)")
        UUID authUserId,
        @Schema(description = "Nome")
        String name,
        @Schema(description = "Email (de auth.users, read-only)")
        String email,
        @Schema(description = "Telefone")
        String phoneNumber,
        @Schema(description = "URL do avatar/foto")
        String photoUrl,
        @Schema(description = "Role no sistema (enum do PostgreSQL, guardado como texto)", example = "EMPLOYEE")
        String role,
        @Schema(description = "Estado da conta", example = "unlocked")
        String status,
        @Schema(description = "Criado em")
        OffsetDateTime createdAt,
        @Schema(description = "Atualizado em")
        OffsetDateTime updatedAt,
        /**
         * Verdadeiro quando esta linha é o próprio utilizador autenticado. Poupa ao
         * frontend guardar o id da sessão só para se comparar a cada linha da lista.
         */
        @Schema(description = "É o próprio utilizador autenticado")
        boolean me
) {}