package com.management.managementapi.enterprises.dto.supplier.response;

import java.util.UUID;

/**
 * Um fornecedor do catálogo.
 *
 * Sem {@code createdAt}/{@code updatedAt}: os timestamps do {@code BaseEntity}
 * são escritos pela base de dados (trigger) e não voltam preenchidos no objeto
 * acabado de gravar — devolvê-los daria null na resposta de uma criação, e a
 * lista não os mostra.
 *
 * @param invoiceCount quantas faturas (de todos os projetos) têm este NIF — é o
 *                     que dá a noção do peso do fornecedor sem sair da lista
 */
public record SupplierResponseDTO(
        UUID id,
        String nif,
        String name,
        String notes,
        long invoiceCount
) {}
