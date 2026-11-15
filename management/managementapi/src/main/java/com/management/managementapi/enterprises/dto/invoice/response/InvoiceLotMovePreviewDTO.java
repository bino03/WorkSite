package com.management.managementapi.enterprises.dto.invoice.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O que acontece às rubricas se a fatura mudar para outro lote da mesma obra.
 * Cada despesa (da fatura e das NC ligadas) procura a rubrica com o mesmo
 * código no lote de destino. {@code canKeep} só é {@code true} se <b>todas</b>
 * a encontrarem — tudo ou nada, decidido a 2026-11-15.
 *
 * @param canKeep há despesas e todas têm correspondência no destino
 * @param lines   uma por despesa; {@code to*} a null quando não há correspondência
 */
public record InvoiceLotMovePreviewDTO(boolean canKeep, List<Line> lines) {

    public record Line(
            boolean creditNote,
            String fromCode,
            String fromName,
            BigDecimal amount,
            UUID toBudgetItemId,
            String toCode,
            String toName
    ) {}
}
