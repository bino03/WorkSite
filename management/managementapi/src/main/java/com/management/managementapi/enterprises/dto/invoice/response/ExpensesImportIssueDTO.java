package com.management.managementapi.enterprises.dto.invoice.response;

/** Um erro que impede a importação, preso à linha do Excel (1-based) onde se corrige. */
public record ExpensesImportIssueDTO(
        int excelRow,
        String message
) {}
