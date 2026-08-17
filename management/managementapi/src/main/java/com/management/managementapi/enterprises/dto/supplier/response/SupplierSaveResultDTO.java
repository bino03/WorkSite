package com.management.managementapi.enterprises.dto.supplier.response;

/**
 * O fornecedor gravado e o efeito que teve nas faturas.
 *
 * O {@code invoicesUpdated} é o que se mostra a seguir a gravar ("12 faturas
 * ficaram com o nome"): sem ele, a ação parecia não fazer nada — o trabalho
 * acontece em linhas que estão noutro ecrã.
 */
public record SupplierSaveResultDTO(
        SupplierResponseDTO supplier,
        int invoicesUpdated
) {}
