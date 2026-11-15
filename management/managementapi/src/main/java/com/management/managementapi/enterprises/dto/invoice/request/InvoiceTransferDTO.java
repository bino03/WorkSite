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
 *       {@code enterprise_id} é limpa — check {@code ck_invoice_scope_enterprise});</li>
 *   <li>{@code targetBudgetId} é o lote de destino, só com {@code PROJECT}. Com
 *       a mesma obra da fatura é o que torna a transferência válida: mudar de
 *       lote dentro da obra (2026-11-15);</li>
 *   <li>{@code keepRubrics}, só na mudança de lote: as despesas passam para as
 *       rubricas com o mesmo código no lote de destino em vez de serem
 *       apagadas. Tudo ou nada — se alguma não existir lá, recusa
 *       ({@code INVOICE_053}); o modal pergunta depois de ver o
 *       {@code lot-move-preview}.</li>
 * </ul>
 */
public record InvoiceTransferDTO(
        @NotBlank String targetScope,
        UUID targetEnterpriseId,
        @NotBlank String reason,
        UUID targetBudgetId,
        Boolean keepRubrics
) {
    public InvoiceTransferDTO(String targetScope, UUID targetEnterpriseId, String reason) {
        this(targetScope, targetEnterpriseId, reason, null, null);
    }
}
