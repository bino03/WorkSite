package com.management.managementapi.enterprises.dto.supplier.response;

import java.time.LocalDate;

/**
 * Um NIF que aparece nas faturas e ainda não tem empresa associada — a lista de
 * trabalho do ecrã de fornecedores.
 *
 * @param suggestedName  nome que alguém já escreveu à mão nalguma fatura deste
 *                       NIF, ou {@code null} se nenhuma o tem; serve para
 *                       aparecer pré-preenchido na caixa
 * @param lastInvoiceDate data da fatura mais recente deste NIF, para se
 *                        perceber se é um fornecedor atual ou de há dois anos
 */
public record UnknownSupplierNifDTO(
        String nif,
        long invoiceCount,
        String suggestedName,
        LocalDate lastInvoiceDate
) {}
