package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma fatura tal como foi lida do Excel, já com as linhas do mesmo nº juntas
 * — serve a pré-visualização do {@code dryRun}.
 *
 * @param key              identifica o grupo dentro do ficheiro (é o valor das
 *                         respostas de {@code CREDIT_NOTE_ORIGIN})
 * @param creditNoteOrigin {@code key} da fatura de origem, quando já se sabe
 * @param manualExpense    linha "Despesa registada à mão na app, sem fatura." —
 *                         entra como despesa solta, não como fatura
 * @param transferTo       só na quarentena: para onde a fatura segue depois de entrar (nome da obra
 *                         ou "Despesas da empresa"), quando a coluna "Empreendimento" está preenchida
 * @param lines            uma por linha do Excel: rubrica + valor
 */
public record ExpensesImportInvoiceDTO(
        String key,
        List<Integer> excelRows,
        String invoiceNumber,
        String documentStatus,
        LocalDate invoiceDate,
        String description,
        BigDecimal totalAmount,
        boolean creditNote,
        String creditNoteOrigin,
        boolean manualExpense,
        String paymentStatus,
        String paymentMethod,
        LocalDate paidOn,
        BigDecimal paidAmount,
        String paymentReference,
        boolean sentToAccountant,
        String notes,
        String supplierName,
        String supplierNif,
        boolean duplicate,
        String possibleEnterprises,
        String askWhom,
        String transferTo,
        List<Line> lines
) {
    public record Line(int excelRow, String rubricCode, String rubricLabel, BigDecimal amount) {}
}
