package com.management.managementapi.enterprises.dto.expense.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dados lidos do QR code da AT numa fatura, para preencher o formulário de
 * despesa sem transcrição manual.
 *
 * Nada é gravado por este endpoint: o utilizador confirma antes de criar a
 * despesa. Um QR ilegível não é erro — devolve {@code read = false} e o
 * preenchimento segue manual.
 *
 * @param taxableAmount     {@code totalAmount - taxAmount}; o QR traz as bases
 *                          repartidas por taxa, esta é a soma útil
 * @param documentType      FT (fatura), FS (fatura simplificada), FR
 *                          (fatura-recibo), NC (nota de crédito), ND (nota de débito)…
 * @param alreadyRegistered despesas deste projeto com o mesmo ATCUD — é normal
 *                          repartir uma fatura por várias rubricas, por isso é
 *                          aviso e não bloqueio
 */
public record InvoiceScanResultDTO(
        boolean read,
        String issuerNif,
        String buyerNif,
        String documentType,
        String documentStatus,
        String documentNumber,
        String atcud,
        LocalDate invoiceDate,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        List<RegisteredInvoiceRefDTO> alreadyRegistered,
        List<String> warnings
) {
    /** Resultado quando não há QR legível — o formulário fica manual. */
    public static InvoiceScanResultDTO notRead(List<String> warnings) {
        return new InvoiceScanResultDTO(false, null, null, null, null, null, null,
                null, null, null, null, List.of(), warnings);
    }
}
