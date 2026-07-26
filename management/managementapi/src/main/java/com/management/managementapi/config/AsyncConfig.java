package com.management.managementapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ativa suporte a métodos @Async no contexto Spring.
 * Necessário para o ActivityLogger executar de forma assíncrona.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
