package com.management.managementapi.enterprises.dto.expense.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Despesa já existente com o mesmo ATCUD — serve o aviso de duplicado. */
public record RegisteredInvoiceRefDTO(
        UUID expenseId,
        String expenseName,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,
        LocalDate expenseDate,
        BigDecimal totalPrice
) {}
