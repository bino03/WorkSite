package com.management.managementapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache em memória (Caffeine).
 *
 * Existe por causa das signed URLs do Supabase: cada uma é um POST à API de
 * storage, e uma lista de 20 faturas pediria 20 assinaturas de miniatura por
 * cada render da página. Com o cache, a mesma chave é assinada uma vez a cada
 * 45 minutos.
 *
 * O TTL é deliberadamente mais curto que a validade da própria URL (1 h): assim
 * a entrada expira antes da assinatura e nunca se devolve um link já morto.
 *
 * `ENTERPRISES` e `BUDGET_TREE` existem por lentidão sentida na app real: as
 * duas leituras recalculam rollups do orçamento do zero a cada pedido. Evict é
 * sempre `allEntries = true` (nunca por chave) — produção corre numa só
 * instância com pouco tráfego simultâneo, por isso limpar a família inteira a
 * cada escrita relevante é mais simples e mais seguro do que arriscar esquecer
 * uma chave composta espalhada por vários serviços.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SIGNED_URLS = "signedUrls";
    public static final String ENTERPRISES = "enterprises";
    public static final String BUDGET_TREE = "budgetTree";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SIGNED_URLS, ENTERPRISES, BUDGET_TREE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(45))
                .maximumSize(5_000));
        return manager;
    }
}
