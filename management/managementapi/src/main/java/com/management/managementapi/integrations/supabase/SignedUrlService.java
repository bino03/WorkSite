package com.management.managementapi.integrations.supabase;

import com.management.managementapi.config.CacheConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Signed URLs do Supabase com cache e sem exceções verificadas.
 *
 * Assinar é um POST à API de storage. Numa lista de faturas isso multiplica-se
 * por linha, por isso o resultado fica em cache
 * ({@link CacheConfig#SIGNED_URLS}, 50 min) enquanto a assinatura continua
 * válida por 1 h.
 *
 * Devolve {@code null} em vez de lançar: uma miniatura que não assina é uma
 * imagem em falta na lista, não um pedido falhado. É o mesmo contrato que os
 * {@code resolvePhotoUrl} espalhados pelos serviços já praticavam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignedUrlService {

    /** Um pouco acima do TTL do cache, para a URL nunca morrer antes da entrada. */
    private static final int EXPIRES_SECONDS = 3600;

    private final SupabaseStorageService storage;

    @Cacheable(cacheNames = CacheConfig.SIGNED_URLS, key = "#bucket + '/' + #storageKey",
               unless = "#result == null")
    public String resolve(String bucket, String storageKey) {
        if (bucket == null || storageKey == null || storageKey.isBlank()) {
            return null;
        }
        try {
            // O Supabase rejeita chaves com barra inicial.
            String key = storageKey.startsWith("/") ? storageKey.substring(1) : storageKey;
            return storage.createSignedUrl(bucket, key, EXPIRES_SECONDS);
        } catch (Exception e) {
            log.warn("Não foi possível gerar signed URL para {}/{}: {}", bucket, storageKey, e.getMessage());
            return null;
        }
    }
}
