package com.management.managementapi.enterprises.dto.budget.response;

import java.util.List;

/**
 * O que a pasta {@code Faturas/Lançadas/} do zip vai levar — a parte
 * "documentos" do {@link BudgetExportSummaryDTO}.
 *
 * @param documentCount           ficheiros que entram no zip
 * @param invoicesWithoutDocument faturas da obra sem nenhum ficheiro — não há nada a exportar delas
 * @param renamedCount            quantos recebem um nome gerado pelo §7 (os outros mantêm o
 *                                {@code original_filename}, que já é o nome do vault)
 * @param warnings                colisões ({@code _2}), documentos sem ficheiro, faturas sem data
 */
public record DocumentsExportSummaryDTO(
        int documentCount,
        int invoicesWithoutDocument,
        int renamedCount,
        List<String> warnings
) {}
