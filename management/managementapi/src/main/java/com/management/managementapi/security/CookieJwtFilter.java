package com.management.managementapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que lê o JWT do cookie "access_token" e o adiciona ao header Authorization.
 * Isto permite que o Spring Security's OAuth2 ResourceServer decifre e valide o token.
 *
 * O fluxo é:
 * 1. Request chega com cookie "access_token=eyJhbG..."
 * 2. Este filtro lê o cookie e adiciona "Authorization: Bearer eyJhbG..." ao request
 * 3. BearerTokenAuthenticationFilter consegue processar como normal
 */
@Slf4j
@RequiredArgsConstructor
public class CookieJwtFilter extends OncePerRequestFilter {

    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Se já tiver Authorization header, não faz nada (token no header tem prioridade)
        if (request.getHeader(AUTHORIZATION_HEADER) == null) {
            // Tenta ler o token do cookie "access_token"
            String token = readAccessTokenFromCookie(request);

            if (token != null) {
                // Valida o JWT antes de injetar no header — tokens inválidos/expirados são rejeitados aqui
                try {
                    jwtDecoder.decode(token);
                    // Token válido: injeta no Authorization header para o Spring Security processar
                    request = new CookieJwtRequestWrapper(request, token);
                    log.debug("JWT válido, Authorization header injetado para: {}", request.getRequestURI());
                } catch (Exception e) {
                    // Token inválido ou expirado: não injeta no header
                    // Spring Security tratará o request como anónimo e retornará 401 se necessário
                    log.warn("JWT inválido no cookie '{}', ignorado: {}", ACCESS_TOKEN_COOKIE_NAME, e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Lê o valor do cookie "access_token"
     */
    private String readAccessTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Wrapper simples que sobre-escreve getHeader() para injetar o Authorization header
     */
    private static class CookieJwtRequestWrapper extends HttpServletRequestWrapper {
        private final String token;

        public CookieJwtRequestWrapper(HttpServletRequest request, String token) {
            super(request);
            this.token = token;
        }

        @Override
        public String getHeader(String name) {
            // Se pedir Authorization header, retorna o token como Bearer
            if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                return BEARER_PREFIX + token;
            }
            // Caso contrário, comportamento normal
            return super.getHeader(name);
        }

        @Override
        public java.util.Enumeration<String> getHeaders(String name) {
            // Se pedir Authorization headers, retorna o nosso
            if (AUTHORIZATION_HEADER.equalsIgnoreCase(name)) {
                return java.util.Collections.enumeration(
                    java.util.Collections.singletonList(BEARER_PREFIX + token)
                );
            }
            // Caso contrário, comportamento normal
            return super.getHeaders(name);
        }
    }
}
