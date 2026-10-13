package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As faturas por liquidar de todas as obras e da empresa, pela ordem em que
 * convém pagá-las: vencidas primeiro, depois as que vencem mais cedo, e no fim
 * as que não têm vencimento.
 *
 * Os quatro números do resumo são da lista inteira, não da página — é o que
 * responde a "quanto está em atraso?" sem percorrer as páginas todas.
 *
 * @param invoices            a página pedida, já na ordem de urgência
 * @param enterpriseNames     o nome de cada obra que aparece na página, por id;
 *                            uma despesa da empresa não tem obra e não entra aqui
 * @param totalElements       quantas faturas por liquidar há ao todo
 * @param total               Σ (líquido − pago) das que têm total
 * @param overdueCount        quantas já passaram o vencimento
 * @param overdueTotal        Σ (líquido − pago) das vencidas
 * @param withoutDueDateCount quantas não têm vencimento — ficam no fim da lista
 */
public record OutstandingByUrgencyDTO(
        List<ConstructionInvoiceResponseDTO> invoices,
        Map<UUID, String> enterpriseNames,
        int page,
        int size,
        long totalElements,
        BigDecimal total,
        long overdueCount,
        BigDecimal overdueTotal,
        long withoutDueDateCount
) {}
