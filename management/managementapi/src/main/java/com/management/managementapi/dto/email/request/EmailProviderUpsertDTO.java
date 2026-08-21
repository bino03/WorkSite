package com.management.managementapi.dto.email.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Criar ou editar um provedor SMTP.
 *
 * A {@code password} é o único campo opcional na edição: como nunca é devolvida
 * ao cliente (ver {@code EmailProviderResponseDTO}), o formulário não a consegue
 * reenviar sem obrigar quem edita o nome do remetente a saber a password de cor.
 * Vir a null ou vazia significa "mantém a que está" — não "apaga".
 */
public record EmailProviderUpsertDTO(

        @NotBlank(message = "O nome do provedor é obrigatório")
        @Size(max = 100, message = "Nome do provedor demasiado longo")
        String providerName,

        @NotBlank(message = "O host é obrigatório")
        @Size(max = 255, message = "Host demasiado longo")
        String host,

        @NotNull(message = "A porta é obrigatória")
        @Min(value = 1, message = "Porta inválida")
        @Max(value = 65535, message = "Porta inválida")
        Integer port,

        @NotBlank(message = "O utilizador é obrigatório")
        @Size(max = 255, message = "Utilizador demasiado longo")
        String username,

        @Size(max = 255, message = "Password demasiado longa")
        String password,

        @NotBlank(message = "O email de remetente é obrigatório")
        @Email(message = "Email de remetente inválido")
        @Size(max = 255, message = "Email de remetente demasiado longo")
        String fromEmail,

        @Size(max = 255, message = "Nome de remetente demasiado longo")
        String fromName,

        // Os três valores que o `createMailSender` sabe interpretar. Qualquer outro
        // passaria em silêncio e a ligação sairia sem cifra nenhuma.
        @Pattern(regexp = "tls|ssl|none", message = "A encriptação tem de ser tls, ssl ou none")
        String encryption,

        Boolean isDefault,

        Boolean isActive
) {}
