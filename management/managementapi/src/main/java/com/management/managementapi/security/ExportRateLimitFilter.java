package com.management.managementapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.error.ErrorResponseDTO;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting nas exportações do orçamento (`/export`, `/export/zip`), por utilizador
 * autenticado — não por IP: a app é interna (só ADMIN/EMPLOYEE, sem portal público), o risco é
 * abuso ou uma conta comprometida a repetir um pedido pesado (o zip faz streaming de todos os
 * documentos de uma obra), não bots externos. Ver notes/roadmap/pre-deploy-security.md.
 *
 * Corre DEPOIS da autenticação (registado com {@code addFilterAfter(AccountLockFilter.class)} em
 * {@link SecurityConfig}) — precisa do {@code Authentication} já resolvido para saber quem é o
 * utilizador; ao contrário do {@link RateLimitFilter}, que corre antes (as rotas de auth não têm
 * sessão ainda).
 */
public class ExportRateLimitFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final List<PathPattern> PATTERNS = List.of(
            new PathPatternParser().parse("/construction-budget/enterprise/{enterpriseId}/export"),
            new PathPatternParser().parse("/construction-budget/enterprise/{enterpriseId}/export/zip")
    );

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    private final int maxRequestsPerUser;
    private final int windowMinutes;

    public ExportRateLimitFilter(int maxRequestsPerUser, int windowMinutes) {
        this.maxRequestsPerUser = maxRequestsPerUser;
        this.windowMinutes = windowMinutes;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) return true;
        var path = org.springframework.http.server.PathContainer.parsePath(request.getRequestURI());
        return PATTERNS.stream().noneMatch(p -> p.matches(path));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userKey = auth != null ? auth.getName() : null;

        if (userKey != null) {
            Bucket bucket = buckets.get(userKey, k -> Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(maxRequestsPerUser)
                            .refillGreedy(maxRequestsPerUser, Duration.ofMinutes(windowMinutes)).build())
                    .build());
            if (!bucket.tryConsume(1)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                var body = new ErrorResponseDTO(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage(),
                        request.getRequestURI(),
                        ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
                response.getWriter().write(MAPPER.writeValueAsString(body));
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
