package com.management.managementapi.enterprises.config;

import java.time.LocalDate;

import com.management.managementapi.notifications.service.InvoiceDueNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Quarto job agendado do projeto: avisa das faturas por liquidar que estão a
 * chegar ao vencimento.
 *
 * Corre também no arranque, pelo mesmo motivo do
 * {@link BudgetItemDeadlineNotifierConfig}: a app não está ligada 24h por dia,
 * e um cron às 7h numa app que só arranca às 9h nunca dispararia. O dedupe do
 * {@link InvoiceDueNotifier} torna as duas passagens inofensivas.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class InvoiceDueNotifierConfig {

    private final InvoiceDueNotifier notifier;

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
            int created = notifier.notifyUpcomingDueDates(LocalDate.now());
            if (created > 0) {
                log.info("InvoiceDue: {} avisos de vencimento de fatura criados", created);
            }
        } catch (RuntimeException e) {
            log.error("InvoiceDue: falhou a verificação de vencimentos de fatura", e);
        }
    }
}
