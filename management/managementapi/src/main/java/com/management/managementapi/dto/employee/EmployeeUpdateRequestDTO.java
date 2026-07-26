package com.management.managementapi.dto.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Pedido para atualizar um funcionário (PUT)")
public record EmployeeUpdateRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Schema(example = "Maria Silva")
        String name,

        @Email(message = "Email inválido")
        @Size(max = 320, message = "Email demasiado longo")
        @Schema(example = "maria.silva@empresa.pt")
        String email,   // <-- adicionado

        @Pattern(regexp = "^\\+?[0-9\\s-]{6,20}$", message = "Nº de telefone inválido")
        @Schema(example = "+351 912 345 678")
        String phoneNumber,

        @NotBlank(message = "role é obrigatório")
        @Pattern(regexp = "^[A-Z_]{3,32}$", message = "role deve estar em MAIÚSCULAS (ex: EMPLOYEE, ADMIN, KITCHEN, etc.)")
        @Schema(example = "EMPLOYEE")
        String role
) {}