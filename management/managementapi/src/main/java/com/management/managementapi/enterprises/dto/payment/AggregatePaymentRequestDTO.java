package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Um movimento que liquida <b>várias</b> faturas (raro): o fornecedor recebe N
 * faturas numa transferência só.
 *
 * As faturas têm de ser todas da mesma obra (ou todas da empresa, ou todas da
 * quarentena) — o agregado não junta obras diferentes. O {@code amount} do
 * movimento tem de bater certo com a soma do que falta pagar nas faturas
 * escolhidas; se for menor, a app devolve as que ficam de fora e não grava nada.
 */
public record AggregatePaymentRequestDTO(

        @NotEmpty(message = "Indique pelo menos uma fatura")
        List<UUID> invoiceIds,

        @NotNull(message = "A data do pagamento é obrigatória")
        @PastOrPresent(message = "A data do pagamento não pode ser no futuro")
        LocalDate paidOn,

        @NotBlank(message = "Indique o método de pagamento")
        String method,

        @NotNull(message = "Indique o valor do movimento")
        @Positive(message = "O valor do movimento tem de ser positivo")
        BigDecimal amount,

        @Size(max = 255, message = "Referência demasiado longa")
        String reference,

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {}
