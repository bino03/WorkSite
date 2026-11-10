package com.management.managementapi.enterprises.dto.invoice.request;

import com.management.managementapi.enterprises.model.ConstructionInvoice;

import java.util.UUID;

/**
 * Os filtros da pesquisa global (o modal Ctrl+K). Todos opcionais; com pelo
 * menos um, o texto deixa de ser obrigatório.
 *
 * @param enterpriseId só as faturas desta obra
 * @param scope só este âmbito — é o "Despesas da empresa" / "Por identificar" do select de obra
 * @param supplierNif só deste fornecedor (NIF exato)
 * @param paymentStatus {@code PAID}, {@code PARTIAL}, {@code UNPAID} ou {@code OUTSTANDING}
 *                      (por pagar + parcial); com ele ativo as notas de crédito saem, como na lista da obra
 */
public record GlobalInvoiceSearchFilter(
        String q,
        UUID enterpriseId,
        ConstructionInvoice.Scope scope,
        String supplierNif,
        String paymentStatus
) {

    public static GlobalInvoiceSearchFilter text(String q) {
        return new GlobalInvoiceSearchFilter(q, null, null, null, null);
    }

    public boolean hasFilter() {
        return enterpriseId != null || scope != null || !isBlank(supplierNif) || !isBlank(paymentStatus);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
