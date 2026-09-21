package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de uma importação da folha "Despesas" do vault da Vilatro
 * (docs/excel-parity.md §9).
 *
 * @param dryRun          true quando nada foi gravado
 * @param sheetTotal      valor da linha de totais da {@code TabelaDespesas}, quando legível
 * @param parsedTotal     soma das linhas lidas — tem de bater certo com {@code sheetTotal}
 * @param totalDifference {@code parsedTotal - sheetTotal}; acima de 1 cêntimo é erro (passo 9 do §9),
 *                        ao contrário do orçamento, onde só avisa
 * @param errors          o que impede de gravar — cada um aponta a linha do Excel a corrigir
 * @param warnings        o que entrou com aproximações (data de pagamento presumida, método "Outro", …)
 * @param questions       o que só a pessoa sabe responder (NC ↔ fatura de origem, pagamentos agregados);
 *                        a gravação exige-as todas respondidas em {@code answers}
 * @param transferredCount quantas faturas da quarentena seguem logo para a obra (ou empresa) da coluna "Empreendimento"
 * @param invoices        as faturas tal como vão entrar, já agrupadas por nº
 */
public record ExpensesImportResultDTO(
        boolean dryRun,
        String scope,
        String sheetName,
        int rowCount,
        int invoiceCount,
        int creditNoteCount,
        int manualExpenseCount,
        int transferredCount,
        int paidCount,
        int partiallyPaidCount,
        int unpaidCount,
        BigDecimal parsedTotal,
        BigDecimal sheetTotal,
        BigDecimal totalDifference,
        List<ExpensesImportIssueDTO> errors,
        List<String> warnings,
        List<ExpensesImportQuestionDTO> questions,
        List<ExpensesImportInvoiceDTO> invoices
) {}
