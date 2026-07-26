// RevokedTokenCleanupConfig.java
package com.management.managementapi.security;

import com.management.managementapi.repository.RevokedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class RevokedTokenCleanupConfig {

  private final RevokedTokenRepository repo;

  // 30 dias para tokens sem expires_at explícito
  private static final int FALLBACK_TTL_SECONDS = 30 * 24 * 60 * 60;

  // corre ao minuto 15 de cada hora
  @Scheduled(cron = "0 15 * * * *")
  public void cleanup() {
    int deleted = repo.deleteExpired(FALLBACK_TTL_SECONDS);
    if (deleted > 0) {
      log.info("RevokedToken cleanup: {} linhas removidas", deleted);
    }
  }
}
