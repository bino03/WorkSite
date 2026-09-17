package com.management.managementapi.enterprises.dto.invoice.response;

import java.util.List;

/**
 * Uma decisão que o importador não toma sozinho (§3.2 e §4 do contrato).
 *
 * O {@code id} é estável entre o {@code dryRun} e a gravação — deriva da
 * linha do Excel — para a resposta se poder enviar no pedido seguinte.
 *
 * @param kind {@code CREDIT_NOTE_ORIGIN} (a que fatura pertence esta nota de
 *             crédito) ou {@code AGGREGATE_PAYMENT} (estas faturas com a mesma
 *             observação foram pagas num só movimento?)
 */
public record ExpensesImportQuestionDTO(
        String id,
        String kind,
        String text,
        List<Integer> excelRows,
        List<Option> options
) {
    public record Option(String value, String label) {}
}
