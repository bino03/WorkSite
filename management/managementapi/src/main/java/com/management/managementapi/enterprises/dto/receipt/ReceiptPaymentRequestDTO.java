package com.management.managementapi.enterprises.dto.receipt;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * "Registar também o pagamento" no recibo: paga as faturas ligadas que ainda
 * têm valor em falta — um pagamento por obra. {@code paidOn} vazio = data do recibo.
 */
public record ReceiptPaymentRequestDTO(
        @NotBlank(message = "Indique o método de pagamento")
        String method,

        @PastOrPresent(message = "A data do pagamento não pode ser no futuro")
        LocalDate paidOn,

        @Size(max = 255, message = "Referência demasiado longa")
        String reference
) {}
