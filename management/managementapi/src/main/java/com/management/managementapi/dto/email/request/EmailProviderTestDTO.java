package com.management.managementapi.dto.email.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Para onde enviar o email de teste de um provedor. */
public record EmailProviderTestDTO(

        @NotBlank(message = "O email de destino é obrigatório")
        @Email(message = "Email de destino inválido")
        @Size(max = 255, message = "Email de destino demasiado longo")
        String to
) {}
