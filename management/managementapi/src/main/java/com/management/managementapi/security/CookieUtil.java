package com.management.managementapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Utilitário para gerenciar cookies JWT de forma segura.
 * Define flags de segurança: HttpOnly, Secure, SameSite
 */
@Component
public class CookieUtil {

    @Value("${app.security.cookie.secure:false}")
    private boolean isSecure;

    @Value("${app.security.cookie.domain:localhost}")
    private String cookieDomain;

    /**
     * Cria um cookie de access token (15 minutos)
     * @param token Valor do JWT
     * @return ResponseCookie com flags de segurança
     */
    public ResponseCookie createAccessTokenCookie(String token) {
        var cookie = ResponseCookie
            .from("access_token", token)
            .httpOnly(true)                    // JavaScript não acessa
            .secure(isSecure)                   // HTTPS em produção
            .path("/")
            .sameSite("Lax")                   // Permite cookies em cross-site (mais seguro que Strict para dev)
            .maxAge(15 * 60);                   // 15 minutos

        // Em localhost (dev): domain vazio para permitir localhost:5173, localhost:8080, etc
        // Em produção: domain definido na config (ex: seu-app.com)
        if (cookieDomain != null && !cookieDomain.equals("localhost") && !cookieDomain.isEmpty()) {
            cookie.domain(cookieDomain);
        } else {
            // Explicitamente deixa domain vazio em localhost
            cookie.domain("");
        }

        return cookie.build();
    }

    /**
     * Cria um cookie de refresh token (7 dias)
     * @param token Valor do refresh token
     * @return ResponseCookie com flags de segurança
     */
    public ResponseCookie createRefreshTokenCookie(String token) {
        var cookie = ResponseCookie
            .from("refresh_token", token)
            .httpOnly(true)                    // JavaScript não acessa
            .secure(isSecure)                   // HTTPS em produção
            .path("/")
            .sameSite("Lax")                   // Permite cookies em cross-site (mais seguro que Strict para dev)
            .maxAge(7 * 24 * 60 * 60);          // 7 dias

        // Em localhost (dev): domain vazio para permitir localhost:5173, localhost:8080, etc
        // Em produção: domain definido na config (ex: seu-app.com)
        if (cookieDomain != null && !cookieDomain.equals("localhost") && !cookieDomain.isEmpty()) {
            cookie.domain(cookieDomain);
        } else {
            // Explicitamente deixa domain vazio em localhost
            cookie.domain("");
        }

        return cookie.build();
    }

    /**
     * Cria um cookie vazio para limpar/remover cookie
     * @param cookieName Nome do cookie a remover
     * @return ResponseCookie com maxAge=0 (remove do browser)
     */
    public ResponseCookie createCleanCookie(String cookieName) {
        var cookie = ResponseCookie
            .from(cookieName, "")
            .httpOnly(true)
            .secure(isSecure)
            .path("/")
            .sameSite("Lax")
            .maxAge(0);                        // Remove imediatamente

        // Em localhost (dev): domain vazio para permitir localhost:5173, localhost:8080, etc
        // Em produção: domain definido na config (ex: seu-app.com)
        if (cookieDomain != null && !cookieDomain.equals("localhost") && !cookieDomain.isEmpty()) {
            cookie.domain(cookieDomain);
        } else {
            // Explicitamente deixa domain vazio em localhost
            cookie.domain("");
        }

        return cookie.build();
    }

    /**
     * Cria um cookie de logout (alias para createCleanCookie)
     * @param cookieName Nome do cookie a remover
     * @return ResponseCookie com maxAge=0
     */
    public static ResponseCookie createLogoutCookie(String cookieName) {
        return ResponseCookie
            .from(cookieName, "")
            .httpOnly(true)
            .secure(false) // false em dev, true em prod (vem da config em produção)
            .path("/")
            .sameSite("Lax")
            .maxAge(0)                         // Remove imediatamente
            .domain("")                        // Domain vazio em localhost
            .build();
    }
}
