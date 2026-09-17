package com.management.managementapi.enterprises.dto.invoice.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * As respostas às perguntas devolvidas pelo {@code dryRun} da importação de
 * despesas. Vão no mesmo multipart que o ficheiro, na parte {@code answers}.
 */
public record ExpensesImportAnswersDTO(
        List<Answer> answers
) {
    public record Answer(
            @NotBlank String questionId,
            @NotBlank String value
    ) {}
}
