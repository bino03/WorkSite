package com.management.managementapi.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.RevokedTokenRepository;
import com.management.managementapi.integrations.supabase.SupabaseProperties;

@Slf4j
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // --- Decoder via JWKS do Supabase ---
    // Projetos Supabase novos assinam com chaves assimétricas (ES256), não com
    // o segredo HS256 partilhado legado — por isso validamos contra o endpoint
    // JWKS, que suporta ES256/RS256 e rotação de chaves automaticamente.
    @Bean
    JwtDecoder jwtDecoder(SupabaseProperties props) {
        String jwkSetUri = props.getUrl() + "/auth/v1/.well-known/jwks.json";
        // NimbusJwtDecoder.withJwkSetUri(...) assume RS256 por omissão — o
        // Supabase assina com ES256, por isso tem de ser declarado explicitamente.
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }

    // --- Converte claims -> authorities (ROLE_*) ---
    @Bean
    JwtAuthenticationConverter jwtAuthConverter(ProfileRepository profileRepo) {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter((Jwt jwt) -> {
            var out = new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>();

            // 0) Todo token válido recebe ROLE_AUTHENTICATED
            out.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));

            // 1) Claim "role" direto (se existir)
            var topRole = jwt.getClaimAsString("role");
            if (topRole != null && !topRole.isBlank()) {
                out.add(new SimpleGrantedAuthority("ROLE_" + topRole.toUpperCase()));
            }

            // 2) app_metadata.role (Supabase metadata)
            Object appMeta = jwt.getClaims().get("app_metadata");
            if (appMeta instanceof Map<?, ?> meta) {
                Object r2 = meta.get("role");
                if (r2 instanceof String s && !s.isBlank()) {
                    out.add(new SimpleGrantedAuthority("ROLE_" + s.toUpperCase()));
                }
            }

            // 3) Fallback: consulta DB Profile.role
            var sub = jwt.getClaimAsString("sub"); // UUID do user no Supabase
            try {
                var uid = java.util.UUID.fromString(sub);
                profileRepo.findByAuthUserId(uid).ifPresent(p -> {
                    var dbRole = p.getRole().name(); // ADMIN / EMPLOYEE
                    out.add(new SimpleGrantedAuthority("ROLE_" + dbRole));
                });
            } catch (Exception ignore) {
                // se o sub não for UUID válido, ignora
            }

            return out;
        });
        return conv;
    }

    // --- Rate limiting em /auth/login e /auth/forgot-password ---
    @Bean
    RateLimitFilter rateLimitFilter(
            @Value("${app.security.rate-limit.login.max-attempts-per-account}") int loginAccountMax,
            @Value("${app.security.rate-limit.login.account-window-minutes}") int loginAccountWindowMinutes,
            @Value("${app.security.rate-limit.login.max-requests-per-ip}") int loginIpMax,
            @Value("${app.security.rate-limit.login.ip-window-minutes}") int loginIpWindowMinutes,
            @Value("${app.security.rate-limit.forgot-password.max-attempts-per-account}") int forgotAccountMax,
            @Value("${app.security.rate-limit.forgot-password.account-window-minutes}") int forgotAccountWindowMinutes,
            @Value("${app.security.rate-limit.forgot-password.max-requests-per-ip}") int forgotIpMax,
            @Value("${app.security.rate-limit.forgot-password.ip-window-minutes}") int forgotIpWindowMinutes) {
        return new RateLimitFilter(
                loginAccountMax, loginAccountWindowMinutes, loginIpMax, loginIpWindowMinutes,
                forgotAccountMax, forgotAccountWindowMinutes, forgotIpMax, forgotIpWindowMinutes);
    }

    // --- Configuração de segurança principal ---
    @Bean
    SecurityFilterChain security(HttpSecurity http,
                                 JwtDecoder decoder,
                                 JwtAuthenticationConverter authConv,
                                 ProfileRepository profileRepo,
                                 RevokedTokenRepository revokedRepo,
                                 RateLimitFilter rateLimitFilter) throws Exception {
        http
            // ✅ habilita CORS — vai usar o bean corsConfigurationSource() definido abaixo
            .cors(cors -> {})
            // ❌ desliga CSRF (usamos JWT stateless)
            .csrf(csrf -> csrf.disable())
            // 🔒 sessões desativadas (JWT = stateless)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 🔑 regras de autorização
            .authorizeHttpRequests(auth -> auth
                // ✅ Endpoints PÚBLICOS (sem autenticação)
                .requestMatchers("/actuator/health", "/auth/login", "/auth/refresh", "/auth/logout", "/auth/accept-invite", "/auth/forgot-password", "/auth/reset-password").permitAll()

                // 🔒 Endpoints protegidos
                .requestMatchers(HttpMethod.POST, "/auth/admin/**").hasRole("ADMIN")
                // Credenciais SMTP: quem as controla controla os emails que saem em nome da plataforma.
                .requestMatchers("/settings/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/employees/**").hasAnyRole("ADMIN", "EMPLOYEE")
                .requestMatchers("/auth/me").authenticated()
                .anyRequest().authenticated()
            )
            // 🔑 validação JWT
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(authConv))
            );

        // DEBUG FILTER: Log de todas as requests
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain) throws ServletException, IOException {
                log.info("===== INCOMING REQUEST =====");
                log.info("URI: {}", request.getRequestURI());
                log.info("Method: {}", request.getMethod());
                log.info("Origin: {}", request.getHeader("Origin"));
                log.info("Cookie header presente: {}",
                        request.getHeader("Cookie") != null ? "SIM" : "NÃO");
                if (request.getHeader("Cookie") != null) {
                    String cookieHeader = request.getHeader("Cookie");
                    int length = Math.min(100, cookieHeader.length());
                    log.info("Cookie header: {}", cookieHeader.substring(0, length) + "...");
                }
                filterChain.doFilter(request, response);
            }
        }, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        // -1) Rate limiting em /auth/login e /auth/forgot-password (por IP e por conta)
        http.addFilterBefore(rateLimitFilter,
            org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        // 0) Lê JWT do cookie "access_token" e adiciona ao Authorization header
        http.addFilterBefore(new CookieJwtFilter(decoder),
            org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        // 1) Bloqueio por estado (ex.: conta bloqueada)
        http.addFilterAfter(new AccountLockFilter(profileRepo, true),
            org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        // 2) Revogação de token
        http.addFilterBefore(new TokenRevocationFilter(revokedRepo),
            org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    // --- ✅ Configuração CORS global ---
    // Origens configuráveis por env var (CORS_ALLOWED_ORIGINS, lista separada por vírgulas) —
    // o domínio de produção do Backoffice ainda não está decidido, por isso não há valor de
    // produção hardcoded aqui. Ver docs/environment.md.
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(allowedOrigins);
        // Métodos permitidos
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Headers permitidos (precisas de Authorization para JWT!)
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
        // Headers expostos (se precisares de exibir headers personalizados no browser)
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        // Se vais usar cookies (mesmo que não uses, não faz mal deixar true)
        cfg.setAllowCredentials(true);
        // Cache do preflight (OPTIONS) em segundos
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

     @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Usa BCryptPasswordEncoder como exemplo
    }
}