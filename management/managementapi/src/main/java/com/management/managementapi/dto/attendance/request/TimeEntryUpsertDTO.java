package com.management.managementapi.dto.attendance.request;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.management.managementapi.model.enums.TimeDirection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * registar ou corrigir uma picagem. O {@code source} não vem do cliente: na fase 1
 * é sempre MANUAL, e quando houver QR é o endpoint do QR que o define — o método
 * de registo é um facto de como o registo entrou, não uma escolha de quem o grava.
 */
public record TimeEntryUpsertDTO(

        @NotNull(message = "O funcionário é obrigatório")
        UUID profileId,

        /** Opcional: a obra onde a picagem aconteceu. Quando o QR entrar, vem dele. */
        UUID enterpriseId,

        @NotNull(message = "O instante da picagem é obrigatório")
        OffsetDateTime happenedAt,

        @NotNull(message = "O sentido (entrada ou saída) é obrigatório")
        TimeDirection direction,

        @Size(max = 500, message = "A nota não pode ter mais de 500 caracteres")
        String note,

        /** Porque é que foi corrigida. Guardado na revisão, para a auditoria. */
        @Size(max = 500, message = "O motivo não pode ter mais de 500 caracteres")
        String reason
) {}
