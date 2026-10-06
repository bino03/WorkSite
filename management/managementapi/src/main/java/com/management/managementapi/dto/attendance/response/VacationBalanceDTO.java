package com.management.managementapi.dto.attendance.response;

import java.util.UUID;

/**
 * o saldo de férias de um ano civil, em **dias úteis**.
 *
 * @param entitled  dias a que tem direito nesse ano (22 por omissão, editável por funcionário)
 * @param taken     dias de ausências de férias já aprovadas
 * @param pending   dias de pedidos ainda por decidir — descontam do disponível, senão
 *                  marcava-se o dobro dos dias que se tem e só se descobria ao aprovar
 * @param available `entitled - taken - pending`. Pode ser negativo se o saldo foi
 *                  excedido à mão; mostra-se como é, não se esconde
 */
public record VacationBalanceDTO(
        UUID profileId,
        String profileName,
        int year,
        int entitled,
        double taken,
        double pending,
        double available
) {}
