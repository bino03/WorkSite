package com.management.managementapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura de regressão para o rate limiting de /auth/login e /auth/forgot-password
 * (notes/roadmap/pre-deploy-security.md) — sem isto, alguém a "simplificar" o filtro no
 * futuro (ex.: só por IP, ou só por conta) não teria como saber que partiu a garantia.
 */
class RateLimitFilterTest {

    private static final String LOGIN_PATH = "/auth/login";

    private RateLimitFilter filterWith(int accountMax, int accountWindowMin, int ipMax, int ipWindowMin) {
        // Os parâmetros de /auth/forgot-password não entram nestes testes — usa-se sempre /auth/login.
        return new RateLimitFilter(accountMax, accountWindowMin, ipMax, ipWindowMin, 100, 15, 100, 15);
    }

    private MockHttpServletRequest loginRequest(String email, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", LOGIN_PATH);
        request.setRemoteAddr(ip);
        String body = "{\"email\":\"" + email + "\",\"password\":\"whatever123\"}";
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private FilterChain countingChain(AtomicInteger counter) {
        return (req, res) -> {
            // Lê o corpo tal como o @RequestBody do controller o faria — prova que o
            // CachedBodyRequestWrapper deixa o pedido legível a jusante.
            req.getInputStream().readAllBytes();
            counter.incrementAndGet();
        };
    }

    @Test
    void deixaPassarPedidosDentroDoLimite() throws ServletException, IOException {
        RateLimitFilter filter = filterWith(5, 15, 20, 1);
        AtomicInteger passed = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(loginRequest("a@example.com", "10.0.0.1"), response, countingChain(passed));

        assertThat(passed.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200); // default do MockHttpServletResponse, nada escrito
    }

    @Test
    void bloqueiaPorContaAoExceederOLimite_masContasDiferentesNaoSePartilham() throws ServletException, IOException {
        RateLimitFilter filter = filterWith(2, 15, 100, 1); // limite baixo por conta, alto por IP
        AtomicInteger passed = new AtomicInteger();

        // Mesma conta, IPs diferentes (para isolar o limite por conta do limite por IP)
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(loginRequest("vitima@example.com", "10.0.0." + i), ok, countingChain(passed));
            assertThat(ok.getStatus()).isEqualTo(200);
        }
        assertThat(passed.get()).isEqualTo(2);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("vitima@example.com", "10.0.0.99"), blocked, countingChain(passed));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"errorCode\":\"ERR_011\"");
        assertThat(passed.get()).isEqualTo(2); // não chegou ao chain

        // Outra conta, mesmo padrão de IPs — não deve estar afetada pelo bloqueio acima
        MockHttpServletResponse otherAccount = new MockHttpServletResponse();
        filter.doFilter(loginRequest("outra@example.com", "10.0.0.99"), otherAccount, countingChain(passed));
        assertThat(otherAccount.getStatus()).isEqualTo(200);
        assertThat(passed.get()).isEqualTo(3);
    }

    @Test
    void bloqueiaPorIpAoExceederOLimite_mesmoComContasDiferentes() throws ServletException, IOException {
        RateLimitFilter filter = filterWith(100, 15, 2, 1); // limite alto por conta, baixo por IP
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(loginRequest("conta" + i + "@example.com", "203.0.113.5"), ok, countingChain(passed));
            assertThat(ok.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest("conta-nova@example.com", "203.0.113.5"), blocked, countingChain(passed));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(passed.get()).isEqualTo(2);
    }

    @Test
    void ignoraPedidosForaDeLoginEForgotPassword() throws ServletException, IOException {
        RateLimitFilter filter = filterWith(1, 15, 1, 1); // limites baixos: só provam algo se o filtro chegar a correr
        AtomicInteger passed = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, countingChain(passed));

        assertThat(passed.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
