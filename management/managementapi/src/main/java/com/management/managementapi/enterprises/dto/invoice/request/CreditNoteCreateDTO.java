package com.management.managementapi.enterprises.dto.invoice.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Registar uma nota de crédito a partir de uma fatura já lançada.
 *
 * A NC vive na mesma tabela ({@code construction_invoice},
 * {@code document_type = CREDIT_NOTE}, {@code related_invoice_id} = a fatura de
 * origem, passada no path). Herda o âmbito e a obra da origem; o NIF herda por
 * omissão e um NIF diferente grava com aviso, não bloqueia.
 *
 * {@code totalAmount} é <b>positivo</b> — o valor da NC. O sinal negativo só
 * aparece nas {@code construction_expense} que ela gera.
 *
 * Ver docs/faturas-modelo-alvo.md §6.
 */
public record CreditNoteCreateDTO(

        @NotNull(message = "Indique o valor da nota de crédito")
        @Positive(message = "O valor da nota de crédito tem de ser positivo")
        BigDecimal totalAmount,

        @Size(max = 100, message = "Número demasiado longo")
        String invoiceNumber,

        @Size(max = 100, message = "ATCUD demasiado longo")
        String invoiceAtcud,

        @PastOrPresent(message = "A data não pode ser no futuro")
        LocalDate invoiceDate,

        /** Por omissão herda o da fatura de origem. Diferente → grava com aviso. */
        @Size(max = 20, message = "NIF demasiado longo")
        String supplierNif,

        @Size(max = 500, message = "Descrição demasiado longa")
        String description,

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes,

        /** `MISSING`, `TO_PRINT` ou `TO_REQUEST`. Sem valor fica `MISSING`. */
        String documentStatus,

        /**
         * A repartição negativa confirmada pelo utilizador. 0 ou 1 linha na
         * fase 3 (a origem só tem uma rubrica). Vazio → NC sem despesas.
         */
        @Valid
        List<CreditNoteExpenseLineDTO> expenses
) {}
