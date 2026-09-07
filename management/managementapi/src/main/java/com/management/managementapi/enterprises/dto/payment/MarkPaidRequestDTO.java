package com.management.managementapi.enterprises.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Marcar <b>uma</b> fatura como paga. É o gesto de um clique do Excel: data,
 * método, referência e prova opcional.
 *
 * {@code amount} é opcional — por omissão paga o que falta liquidar na fatura
 * (o líquido menos o que já estiver pago). Um valor abaixo disso deixa a fatura
 * em {@code PARTIAL}; acima é recusado.
 */
public record MarkPaidRequestDTO(

        @NotNull(message = "A data do pagamento é obrigatória")
        @PastOrPresent(message = "A data do pagamento não pode ser no futuro")
        LocalDate paidOn,

        @NotBlank(message = "Indique o método de pagamento")
        String method,

        @Positive(message = "O valor do pagamento tem de ser positivo")
        BigDecimal amount,

        @Size(max = 255, message = "Referência demasiado longa")
        String reference,

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {}
