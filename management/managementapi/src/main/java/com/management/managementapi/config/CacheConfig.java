package com.management.managementapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKey;
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
public class CacheConfig implements CachingConfigurer {

    public static final String SIGNED_URLS = "signedUrls";
    public static final String ENTERPRISES = "enterprises";
    public static final String BUDGET_TREE = "budgetTree";

    @Bean
    @Override
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(SIGNED_URLS, ENTERPRISES, BUDGET_TREE);
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(45))
                .maximumSize(5_000));
        return manager;
    }

    /**
     * A chave inclui o <b>método</b>, e não só os argumentos.
     *
     * O {@code SimpleKeyGenerator} do Spring constrói a chave apenas a partir dos
     * argumentos: o nome do cache é o único namespace, o método não entra. Com
     * vários métodos a partilhar um cache, dois que recebam o mesmo argumento
     * escrevem na mesma entrada e o segundo lê o valor do primeiro — do tipo
     * errado. Havia três colisões, todas descobertas a 2026-10-01 a partir de um
     * export que estourava:
     * <ul>
     *   <li>{@code budgetTree}: {@code listLots(enterpriseId)} (uma lista) e
     *       {@code getTree(enterpriseId)} (a árvore) — o
     *       {@code ClassCastException: ArrayList cannot be cast to BudgetTreeDTO}
     *       em {@code /export/summary};</li>
     *   <li>{@code enterprises}: {@code findBasicById(id)} e {@code findById(id)} —
     *       DTOs diferentes com o mesmo id;</li>
     *   <li>{@code enterprises}: {@code findAllBasic()} e {@code findAllActive()},
     *       ambos sem argumentos, logo ambos na chave {@code SimpleKey.EMPTY}.
     *       <b>Esta não dava erro nenhum</b>: as duas devolvem {@code List}, o cast
     *       passa por causa do erasure, e o que seguia para o cliente era a lista
     *       de DTOs do outro método — campos errados, em silêncio.</li>
     * </ul>
     *
     * Corrigir pelo gerador e não com um {@code key = "…"} em cada método resolve
     * as três de uma vez e, sobretudo, protege o próximo método que alguém
     * acrescente a um cache já existente sem se lembrar disto.
     *
     * Um {@code @Cacheable} com {@code key} explícito (o {@code SIGNED_URLS}, que
     * usa {@code #bucket + '/' + #storageKey}) ignora o gerador e não é afetado.
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> cacheKey(method.getDeclaringClass(), method.getName(), params);
    }

    /**
     * A chave de um método cacheado. Pública ao package para o teste poder dizer
     * que chave espera sem repetir esta construção — se ela mudar, o teste muda com
     * ela em vez de passar por acidente.
     */
    static Object cacheKey(Class<?> declaringClass, String method, Object... params) {
        String scope = declaringClass.getSimpleName() + '#' + method;
        if (params.length == 0) {
            return scope;
        }
        Object[] key = new Object[params.length + 1];
        key[0] = scope;
        System.arraycopy(params, 0, key, 1, params.length);
        return new SimpleKey(key);
    }
}
