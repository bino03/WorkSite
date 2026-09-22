package com.management.managementapi.security;

import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiting em /auth/login e /auth/forgot-password, por IP e por conta (email do
 * corpo do pedido) — os dois juntos porque um só apanha metade dos casos: um atacante
 * distribuído contorna o limite por conta, e alguém a testar várias contas do mesmo IP
 * contorna o limite por IP. Ver notes/roadmap/pre-deploy-security.md.
 *
 * Em memória (Caffeine guarda os buckets do bucket4j) — só faz sentido enquanto o
 * backend correr numa única instância; múltiplas instâncias atrás de um load balancer
 * exigiriam estado partilhado (Redis) ou rate limiting no proxy.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    // Instância própria, não a injetada pelo Spring — este filtro corre antes da
    // autenticação e não deve depender do contexto MVC para responder 429. Precisa do
    // JavaTimeModule à mesma para o LocalDateTime de ErrorResponseDTO.timestamp.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final String LOGIN_PATH = "/auth/login";
    private static final String FORGOT_PASSWORD_PATH = "/auth/forgot-password";

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(100_000)
            .build();

    private final Rule loginRule;
    private final Rule forgotPasswordRule;

    public RateLimitFilter(int loginAccountMax, int loginAccountWindowMinutes,
                            int loginIpMax, int loginIpWindowMinutes,
                            int forgotAccountMax, int forgotAccountWindowMinutes,
                            int forgotIpMax, int forgotIpWindowMinutes) {
        this.loginRule = new Rule(loginAccountMax, loginAccountWindowMinutes, loginIpMax, loginIpWindowMinutes);
        this.forgotPasswordRule = new Rule(forgotAccountMax, forgotAccountWindowMinutes, forgotIpMax, forgotIpWindowMinutes);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !(LOGIN_PATH.equals(uri) || FORGOT_PASSWORD_PATH.equals(uri));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        String uri = request.getRequestURI();
        Rule rule = LOGIN_PATH.equals(uri) ? loginRule : forgotPasswordRule;

        String ipKey = "ip:" + uri + ":" + clientIp(request);
        Bucket ipBucket = buckets.get(ipKey, k -> Bucket.builder().addLimit(rule.ipBandwidth()).build());
        if (!ipBucket.tryConsume(1)) {
            respondTooManyRequests(response, uri);
            return;
        }

        String email = extractEmail(body);
        if (email != null && !email.isBlank()) {
            String acctKey = "acct:" + uri + ":" + email.toLowerCase();
            Bucket acctBucket = buckets.get(acctKey, k -> Bucket.builder().addLimit(rule.accountBandwidth()).build());
            if (!acctBucket.tryConsume(1)) {
                respondTooManyRequests(response, uri);
                return;
            }
        }

        chain.doFilter(new CachedBodyRequestWrapper(request, body), response);
    }

    /** X-Forwarded-For só é de confiar atrás de um proxy próprio que o defina — sem
     *  isso, é só uma aproximação melhor do que nada para o único-instância de hoje. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractEmail(byte[] body) {
        try {
            JsonNode node = MAPPER.readTree(body);
            JsonNode email = node.get("email");
            return email != null && email.isTextual() ? email.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void respondTooManyRequests(HttpServletResponse response, String path) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        var body = new ErrorResponseDTO(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ErrorCode.RATE_LIMIT_EXCEEDED.getDefaultMessage(),
                path,
                ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
        response.getWriter().write(MAPPER.writeValueAsString(body));
    }

    private record Rule(int accountMax, int accountWindowMinutes, int ipMax, int ipWindowMinutes) {
        Bandwidth accountBandwidth() {
            return Bandwidth.builder().capacity(accountMax)
                    .refillGreedy(accountMax, Duration.ofMinutes(accountWindowMinutes)).build();
        }

        Bandwidth ipBandwidth() {
            return Bandwidth.builder().capacity(ipMax)
                    .refillGreedy(ipMax, Duration.ofMinutes(ipWindowMinutes)).build();
        }
    }

    /** Permite ler o corpo aqui (para o email) e outra vez no @RequestBody do controller. */
    private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                }

                @Override
                public int read() {
                    return bais.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
