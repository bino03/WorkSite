package com.management.managementapi.enterprises.dto.invoice.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Registar uma fatura <b>sem ficheiro</b>.
 *
 * É o caso que o vault da Vilatro tem todos os dias e que a app não sabia
 * representar: a fatura que ainda está por pedir ao fornecedor, ou que existe
 * em papel e falta digitalizar. Até à V24 não havia forma de a registar, porque
 * o ficheiro era obrigatório — e por isso esse trabalho ficava todo no Excel.
 *
 * O ficheiro junta-se depois, por {@code POST /construction-invoices/{id}/documents}.
 *
 * O {@code scope} manda no resto: {@code PROJECT} exige {@code enterpriseId},
 * {@code COMPANY} e {@code UNIDENTIFIED} exigem que ele venha vazio.
 */
public record InvoiceRegisterDTO(

        @NotBlank(message = "Indique se a fatura é de uma obra, da empresa, ou por identificar")
        String scope,

        /** Obrigatório em {@code PROJECT}, proibido nos outros dois. */
        UUID enterpriseId,

        @Size(max = 255, message = "Nome do fornecedor demasiado longo")
        String supplierName,

        @Size(max = 20, message = "NIF demasiado longo")
        String supplierNif,

        @Size(max = 100, message = "Número da fatura demasiado longo")
        String invoiceNumber,

        @Size(max = 100, message = "ATCUD demasiado longo")
        String invoiceAtcud,

        @PastOrPresent(message = "A data da fatura não pode ser no futuro")
        LocalDate invoiceDate,

        @PositiveOrZero(message = "O total da fatura não pode ser negativo")
        BigDecimal totalAmount,

        /** O "Produto/Serviço" do Excel. */
        @Size(max = 500, message = "Descrição demasiado longa")
        String description,

        /**
         * `MISSING`, `TO_PRINT` ou `TO_REQUEST`. Sem valor fica `MISSING` — não
         * faz sentido registar `ARCHIVED` sem documento nenhum.
         */
        String documentStatus,

        @Size(max = 500, message = "Texto demasiado longo")
        String possibleEnterprises,

        @Size(max = 255, message = "Texto demasiado longo")
        String askWhom,

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {}
