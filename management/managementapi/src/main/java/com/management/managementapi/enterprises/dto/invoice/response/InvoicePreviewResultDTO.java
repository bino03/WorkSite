package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado de ler uma fatura sem a gravar — o "Enviar" do carregamento em
 * duas fases: primeiro mostra-se o que o QR trouxe e se colide com alguma
 * fatura já registada, só depois se decide guardar (o "Guardar" que se segue
 * é a mesma {@code POST /construction-invoices} de sempre).
 *
 * Não existe id nem qualquer referência gravada — nada foi tocado no Storage
 * nem na base de dados.
 *
 * @param duplicate        se colide com uma fatura já registada neste projeto
 *                         (checksum, ATCUD, ou o par NIF+número). A false não
 *                         garante que não haja uma cópia idêntica no mesmo
 *                         lote ainda por guardar — só compara com o que já
 *                         está persistido
 * @param duplicateMessage frase pronta a mostrar, identificando a fatura com
 *                         que colide; null quando {@code duplicate} é false
 * @param needsReview      falta a data ou o total; não dá para guardar como
 *                         "pronta" para associar sem os preencher primeiro
 */
public record InvoicePreviewResultDTO(
        boolean qrRead,
        boolean duplicate,
        String duplicateMessage,
        String supplierName,
        String supplierNif,
        String invoiceNumber,
        LocalDate invoiceDate,
        BigDecimal totalAmount,
        boolean needsReview,
        List<String> warnings
) {}
