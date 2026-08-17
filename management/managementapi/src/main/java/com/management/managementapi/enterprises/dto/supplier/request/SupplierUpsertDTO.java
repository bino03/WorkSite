package com.management.managementapi.enterprises.dto.supplier.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Criar ou renomear um fornecedor do catálogo.
 *
 * Ao contrário da fatura, aqui os dois campos são obrigatórios: uma entrada sem
 * NIF ou sem nome não serve para nada — é precisamente o par que a tabela
 * existe para guardar.
 */
public record SupplierUpsertDTO(

        @NotBlank(message = "O NIF é obrigatório")
        @Size(max = 20, message = "NIF demasiado longo")
        // Números e letras: o NIF português são 9 dígitos, mas um fornecedor
        // estrangeiro traz um identificador com letras (ex. "ESB12345678").
        @Pattern(regexp = "[A-Za-z0-9 .-]+", message = "O NIF só pode ter números, letras, espaços, pontos e hífenes")
        String nif,

        @NotBlank(message = "O nome da empresa é obrigatório")
        @Size(max = 255, message = "Nome da empresa demasiado longo")
        String name,

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {}
