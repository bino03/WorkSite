package com.management.managementapi.service.attendance;

import java.util.UUID;

/**
 * os minutos de um dia passados numa obra. {@code enterpriseId} null quer dizer
 * picagem sem obra — não se esconde: é tempo trabalhado que não se sabe onde foi.
 *
 * @param workedMinutes já com a parte da pausa do dia que cabe a esta obra
 */
public record WorkedAtEnterprise(
        UUID enterpriseId,
        String enterpriseName,
        long workedMinutes
) {}
