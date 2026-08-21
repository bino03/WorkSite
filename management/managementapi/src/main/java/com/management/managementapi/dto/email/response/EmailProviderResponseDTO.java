package com.management.managementapi.dto.email.response;

import java.util.UUID;

/**
 * Um provedor SMTP tal como o Backoffice o vê.
 *
 * <b>Sem a password.</b> Ela está em texto simples na base de dados e não há razão
 * nenhuma para a fazer chegar ao browser — o formulário de edição só precisa de
 * saber se já existe alguma, que é o que o {@code hasPassword} diz.
 */
public record EmailProviderResponseDTO(
        UUID id,
        String providerName,
        String host,
        int port,
        String username,
        String fromEmail,
        String fromName,
        String encryption,
        boolean isDefault,
        boolean isActive,
        boolean hasPassword
) {}
