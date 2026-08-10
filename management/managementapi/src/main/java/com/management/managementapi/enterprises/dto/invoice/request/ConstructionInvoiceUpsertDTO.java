package com.management.managementapi.enterprises.dto.invoice.request;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Correção manual dos dados de uma fatura já carregada.
 *
 * <b>Todos os campos são opcionais, de propósito.</b> A fatura entra no sistema
 * a partir do ficheiro, não deste formulário: o QR da AT preenche o que
 * consegue e isto serve para corrigir ou completar o resto. Exigir campos aqui
 * reintroduziria a fricção que a caixa de entrada existe para eliminar — a
 * obrigatoriedade só aparece no momento de associar a fatura a uma rubrica.
 *
 * A base tributável e o total de impostos <b>não estão aqui</b>: são o que o QR
 * da AT declarou, não campos de edição. Um {@code PUT} que os omitisse — como o
 * do próprio Backoffice, que nunca os mostrou como editáveis — apagava-os da
 * fatura à primeira correção de qualquer outro campo.
 */
public record ConstructionInvoiceUpsertDTO(

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

        @Size(max = 2000, message = "Notas demasiado longas")
        String notes
) {}
