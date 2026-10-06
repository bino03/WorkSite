package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * o fuso em que "o dia" da assiduidade é contado.
 *
 * <p>O resto do projeto é todo UTC ({@code jdbc.time_zone}) e nunca precisou de
 * outra coisa: uma fatura tem data, não hora. A assiduidade tem, e aqui o fuso
 * deixa de ser detalhe — uma entrada às 08:00 em Lisboa é 07:00 UTC no verão e
 * 08:00 UTC no inverno, logo contar dias em UTC poria picagens no dia errado
 * durante metade do ano, e a hora de entrada "08:00" do horário deixaria de
 * corresponder à realidade.
 *
 * <p>Esta classe é o único sítio que sabe qual é o fuso. Configurável por
 * {@code app.attendance.zone} para o dia em que houver uma obra fora do
 * continente, mas com Lisboa como omissão.
 */
@Component
public class AttendanceZone {

    private final ZoneId zoneId;

    public AttendanceZone(@Value("${app.attendance.zone:Europe/Lisbon}") String zone) {
        this.zoneId = ZoneId.of(zone);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    /** O dia de calendário local a que um instante pertence. */
    public LocalDate dateOf(OffsetDateTime instant) {
        return instant.atZoneSameInstant(zoneId).toLocalDate();
    }

    /** O instante em que um dia local começa — 00:00 nesse fuso, não em UTC. */
    public OffsetDateTime startOfDay(LocalDate date) {
        return date.atStartOfDay(zoneId).toOffsetDateTime();
    }

    /**
     * O instante em que o dia local seguinte começa. Os intervalos do módulo são
     * meio-abertos ({@code >= início}, {@code < fim}) para que um instante não
     * possa cair em dois dias.
     */
    public OffsetDateTime startOfNextDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }
}
