package com.management.managementapi.enterprises.dto.invoice.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Uma linha do histórico de transferências de uma fatura. Projeção leve das
 * entradas {@code activity_type = transfer} do {@code activity_log} — só vem no
 * detalhe da fatura. O registo de auditoria completo (com a repartição antiga)
 * fica no {@code activity_log}, não aqui.
 */
public record InvoiceTransferSummaryDTO(
        OffsetDateTime transferredAt,
        String fromScope,
        UUID fromEnterpriseId,
        String fromEnterpriseName,
        String toScope,
        UUID toEnterpriseId,
        String toEnterpriseName,
        String reason,
        String byName
) {}
