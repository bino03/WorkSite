package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Uma fatura que <b>não coube</b> num pagamento agregado porque o valor do
 * movimento não chegou para todas as selecionadas. A app devolve-as para o
 * utilizador as tirar da seleção — nunca decide sozinha o que fica de fora.
 */
public record LeftOutInvoiceDTO(
        UUID invoiceId,
        String invoiceNumber,
        String supplierName,
        BigDecimal remaining
) {}
