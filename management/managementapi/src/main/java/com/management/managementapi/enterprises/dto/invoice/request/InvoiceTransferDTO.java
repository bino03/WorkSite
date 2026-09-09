package com.management.managementapi.enterprises.dto.invoice.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

/**
 * Transferir uma fatura de âmbito/obra. Nunca é edição direta — a razão é
 * obrigatória e fica no {@code activity_log} com a repartição antiga.
 *
 * <ul>
 *   <li>{@code targetScope} = {@code PROJECT} exige {@code targetEnterpriseId};</li>
 *   <li>{@code COMPANY}/{@code UNIDENTIFIED} não o aceitam (a coluna
 *       {@code enterprise_id} é limpa — check {@code ck_invoice_scope_enterprise}).</li>
 * </ul>
 */
public record InvoiceTransferDTO(
        @NotBlank String targetScope,
        UUID targetEnterpriseId,
        @NotBlank String reason
) {}
