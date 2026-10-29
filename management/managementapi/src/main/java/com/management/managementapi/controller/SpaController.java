package com.management.managementapi.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Fallback das rotas do React (BrowserRouter) para o index.html do Backoffice, que em
 * produção é servido pelo próprio backend a partir de classpath:/static — ver
 * docs/operations.md → "Deploy". Sem isto, um refresh em /backoffice/... dava 404.
 *
 * As rotas têm de acompanhar o {@code <Routes>} de main.tsx e o
 * {@code SecurityConfig.SPA_PATHS}. Nenhuma colide com a API (que vive em /auth,
 * /enterprises, /settings, ...). Localmente, sem build copiado, o index.html não existe
 * e estas rotas dão 404 — o Vite em :5173 continua a ser o caminho em dev.
 */
@Controller
public class SpaController {

    @GetMapping({
        "/login", "/loading", "/forgot-password", "/reset-password", "/accept-invite",
        "/backoffice", "/backoffice/**", "/team", "/team/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
