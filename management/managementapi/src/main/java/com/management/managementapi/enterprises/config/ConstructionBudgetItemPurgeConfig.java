package com.management.managementapi.enterprises.config;

import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Purga real (hard delete) das rubricas de orçamento eliminadas há mais de 30
 * dias — mesmo padrão do {@code RevokedTokenCleanupConfig} (só outro tinha um
 * job agendado até agora). A zona de recuperação ("Eliminadas (N)") na
 * `ConstructionBudgetPage` é a janela desses 30 dias.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ConstructionBudgetItemPurgeConfig {

    private final ConstructionBudgetItemRepository repository;

    // corre às 3h da manhã, fora do horário de uso
    @Scheduled(cron = "0 0 3 * * *")
    public void purge() {
        int deleted = repository.purgeDeletedOlderThan30Days();
        if (deleted > 0) {
            log.info("ConstructionBudgetItem purge: {} rubricas removidas (eliminadas há mais de 30 dias)", deleted);
        }
    }
}
