package com.management.managementapi.enterprises.dto.invoice.response;

/**
 * Um fornecedor com faturas por liquidar — uma opção do filtro de fornecedor
 * de "A pagar".
 *
 * @param name o do catálogo; sem ficha, o que vem na fatura; nulo se não houver nenhum
 */
public record OutstandingSupplierDTO(String nif, String name) {}
