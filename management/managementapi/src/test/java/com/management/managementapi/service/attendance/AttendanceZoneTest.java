package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O resto do projeto é todo UTC e nunca precisou de outra coisa — uma fatura tem
 * data, não hora. A assiduidade tem, e aqui o fuso deixa de ser detalhe: uma
 * entrada às 08:00 em Lisboa é 07:00 UTC no verão e 08:00 UTC no inverno. Contar
 * dias em UTC poria picagens no dia errado durante metade do ano.
 *
 * <p>Estes testes existem porque `ZoneId`/`Europe/Lisbon` não aparecia em nenhum
 * ficheiro deste projeto antes do módulo de assiduidade — não havia precedente
 * nenhum em que confiar.
 */
class AttendanceZoneTest {

    private final AttendanceZone zone = new AttendanceZone("Europe/Lisbon");

    @Test
    @DisplayName("08:00 em Lisboa é o mesmo dia local no verão e no inverno, apesar de ser UTC diferente")
    void mesmoDiaLocalApesarDeOffsetDiferente() {
        // Verão: Lisboa está em UTC+1, logo 08:00 local = 07:00Z
        OffsetDateTime verao = OffsetDateTime.of(2026, 7, 15, 7, 0, 0, 0, ZoneOffset.UTC);
        // Inverno: Lisboa está em UTC+0, logo 08:00 local = 08:00Z
        OffsetDateTime inverno = OffsetDateTime.of(2026, 1, 15, 8, 0, 0, 0, ZoneOffset.UTC);

        assertThat(zone.dateOf(verao)).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(zone.dateOf(inverno)).isEqualTo(LocalDate.of(2026, 1, 15));

        assertThat(verao.atZoneSameInstant(zone.zoneId()).getHour()).isEqualTo(8);
        assertThat(inverno.atZoneSameInstant(zone.zoneId()).getHour()).isEqualTo(8);
    }

    @Test
    @DisplayName("uma picagem às 00:30 de verão pertence ao dia certo — em UTC seria o dia anterior")
    void picagemDeMadrugadaNaoEscorregaParaODiaAnterior() {
        // 00:30 de 16 de julho em Lisboa (UTC+1) é 23:30Z de 15 de julho.
        OffsetDateTime instante = OffsetDateTime.of(2026, 7, 15, 23, 30, 0, 0, ZoneOffset.UTC);

        assertThat(instante.toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(zone.dateOf(instante)).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    @DisplayName("o dia começa à meia-noite local, não às 00:00Z")
    void inicioDoDiaEhLocal() {
        OffsetDateTime inicio = zone.startOfDay(LocalDate.of(2026, 7, 15));

        assertThat(inicio.toInstant()).isEqualTo(
                OffsetDateTime.of(2026, 7, 14, 23, 0, 0, 0, ZoneOffset.UTC).toInstant());
    }

    @Test
    @DisplayName("o intervalo de um dia é meio-aberto: o fim é o início do dia seguinte")
    void intervaloMeioAberto() {
        LocalDate dia = LocalDate.of(2026, 3, 10);

        assertThat(zone.startOfNextDay(dia)).isEqualTo(zone.startOfDay(dia.plusDays(1)));
    }

    @Test
    @DisplayName("no dia em que os relógios mudam, o dia local continua a fechar no início do seguinte")
    void diaDaMudancaDeHora() {
        // Em Portugal continental os relógios adiantam no último domingo de março:
        // 2026-03-29. Esse dia tem 23 horas, e é onde um cálculo ingénuo falha.
        LocalDate mudanca = LocalDate.of(2026, 3, 29);

        OffsetDateTime inicio = zone.startOfDay(mudanca);
        OffsetDateTime fim = zone.startOfNextDay(mudanca);

        assertThat(java.time.Duration.between(inicio, fim).toHours()).isEqualTo(23);
        assertThat(zone.dateOf(inicio)).isEqualTo(mudanca);
        assertThat(zone.dateOf(fim)).isEqualTo(mudanca.plusDays(1));
    }
}
