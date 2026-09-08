package com.management.managementapi.enterprises.dto.invoice.response;

import java.util.UUID;

/**
 * Onde esta fatura provavelmente vai, e <b>porquê</b>.
 *
 * O porquê não é decoração: uma sugestão sem origem é um palpite que se aceita
 * sem pensar, e classificar mal é o erro mais caro desta app — vai direto ao
 * gasto por rubrica. Com a origem à vista, quem classifica sabe se está a
 * confirmar um hábito desta obra ou a importar um palpite de outra.
 *
 * A sugestão <b>nunca</b> grava sozinha (decisão 8 do Vilatro).
 *
 * @param source              {@code HISTORY_PROJECT} — as faturas deste NIF nesta
 *                            obra costumam ir para aqui; {@code HISTORY_GLOBAL} —
 *                            não há histórico nesta obra, mas há noutra, e o
 *                            código da rubrica existe nas duas
 * @param explanation         frase em português, pronta a mostrar
 * @param referenceEnterprise a obra de onde veio o palpite, só em
 *                            {@code HISTORY_GLOBAL}
 */
public record RubricSuggestionDTO(
        UUID budgetItemId,
        String code,
        String name,
        String source,
        String explanation,
        String referenceEnterprise
) {}
