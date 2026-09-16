package com.management.managementapi.enterprises.config;

import java.time.LocalDate;

import com.management.managementapi.notifications.service.BudgetItemDeadlineNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Terceiro job agendado do projeto (depois de {@code RevokedTokenCleanupConfig}
 * e {@code ConstructionBudgetItemPurgeConfig}): avisa dos prazos de rubrica
 * que se aproximam.
 *
 * Corre também no arranque: a app não está ligada 24h por dia, e um cron às 7h
 * numa app que só arranca às 9h nunca dispararia. O dedupe do
 * {@link BudgetItemDeadlineNotifier} torna as duas passagens inofensivas.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class BudgetItemDeadlineNotifierConfig {

    private final BudgetItemDeadlineNotifier notifier;

    @Scheduled(cron = "0 0 7 * * *")
    public void daily() {
        run();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    // Um aviso falhado não pode impedir a app de arrancar — no listener do
    // ApplicationReadyEvent uma exceção sobe até ao SpringApplication.run().
    private void run() {
        try {
            int created = notifier.notifyUpcomingDeadlines(LocalDate.now());
            if (created > 0) {
                log.info("BudgetItemDeadline: {} avisos de prazo de rubrica criados", created);
            }
        } catch (RuntimeException e) {
            log.error("BudgetItemDeadline: falhou a verificação de prazos de rubrica", e);
        }
    }
}
