package com.management.managementapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobertura de regressão para o rate limiting das exportações do orçamento
 * (notes/roadmap/pre-deploy-security.md) — por utilizador, não por IP, e só nas rotas
 * `/export`/`/export/zip`.
 */
class ExportRateLimitFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(userId, null));
    }

    private FilterChain countingChain(AtomicInteger counter) {
        return (req, res) -> counter.incrementAndGet();
    }

    @Test
    void deixaPassarPedidosDentroDoLimite() throws ServletException, IOException {
        ExportRateLimitFilter filter = new ExportRateLimitFilter(10, 10);
        authenticateAs("user-1");
        AtomicInteger passed = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/construction-budget/enterprise/8d3e7a2f-a24f-4faa-8429-dba7ca3ab82e/export/zip");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, countingChain(passed));

        assertThat(passed.get()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void bloqueiaPorUtilizadorAoExceederOLimite_masOutroUtilizadorNaoEAfetado() throws ServletException, IOException {
        ExportRateLimitFilter filter = new ExportRateLimitFilter(2, 10);
        String path = "/construction-budget/enterprise/8d3e7a2f-a24f-4faa-8429-dba7ca3ab82e/export/zip";
        AtomicInteger passed = new AtomicInteger();

        authenticateAs("user-vitima");
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse ok = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("GET", path), ok, countingChain(passed));
            assertThat(ok.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), blocked, countingChain(passed));
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("\"errorCode\":\"ERR_011\"");
        assertThat(passed.get()).isEqualTo(2);

        authenticateAs("user-outro");
        MockHttpServletResponse otherUser = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", path), otherUser, countingChain(passed));
        assertThat(otherUser.getStatus()).isEqualTo(200);
        assertThat(passed.get()).isEqualTo(3);
    }

    @Test
    void ignoraRotasQueNaoSaoExportacao() throws ServletException, IOException {
        ExportRateLimitFilter filter = new ExportRateLimitFilter(1, 10);
        authenticateAs("user-1");
        AtomicInteger passed = new AtomicInteger();

        // GET /export/summary não faz streaming pesado nenhum — fica fora do rate limit
        MockHttpServletRequest summary = new MockHttpServletRequest("GET",
                "/construction-budget/enterprise/8d3e7a2f-a24f-4faa-8429-dba7ca3ab82e/export/summary");
        MockHttpServletResponse r1 = new MockHttpServletResponse();
        filter.doFilter(summary, r1, countingChain(passed));

        // GET normal da árvore, também fora
        MockHttpServletRequest tree = new MockHttpServletRequest("GET",
                "/construction-budget/enterprise/8d3e7a2f-a24f-4faa-8429-dba7ca3ab82e");
        MockHttpServletResponse r2 = new MockHttpServletResponse();
        filter.doFilter(tree, r2, countingChain(passed));

        assertThat(passed.get()).isEqualTo(2);
        assertThat(r1.getStatus()).isEqualTo(200);
        assertThat(r2.getStatus()).isEqualTo(200);
    }

    @Test
    void semUtilizadorAutenticadoDeixaPassar() throws ServletException, IOException {
        // Não deve acontecer na prática (a rota exige autenticação antes deste filtro correr),
        // mas o filtro não deve rebentar nem bloquear silenciosamente se isso falhar.
        ExportRateLimitFilter filter = new ExportRateLimitFilter(1, 10);
        AtomicInteger passed = new AtomicInteger();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/construction-budget/enterprise/8d3e7a2f-a24f-4faa-8429-dba7ca3ab82e/export/zip");

        filter.doFilter(request, new MockHttpServletResponse(), countingChain(passed));
        filter.doFilter(request, new MockHttpServletResponse(), countingChain(passed));

        assertThat(passed.get()).isEqualTo(2);
    }
}
