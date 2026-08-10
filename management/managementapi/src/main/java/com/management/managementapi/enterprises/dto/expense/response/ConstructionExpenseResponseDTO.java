package com.management.managementapi.enterprises.dto.expense.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Despesa devolvida ao cliente.
 *
 * {@code invoice} é null quando o lançamento foi feito à mão, sem documento —
 * é a forma de o cliente distinguir "não tem fatura" sem ter de adivinhar por
 * campos vazios. As URLs lá dentro são sempre signed URLs geradas na leitura;
 * a chave de storage nunca sai daqui.
 */
public record ConstructionExpenseResponseDTO(
        UUID id,
        UUID budgetItemId,
        String budgetItemCode,
        String budgetItemName,

        String name,
        String description,
        LocalDate expenseDate,
        String unit,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String observations,

        /** A fatura de onde este lançamento saiu; null se foi registado à mão. */
        ExpenseInvoiceRefDTO invoice,

        UUID createdBy,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
