package com.management.managementapi.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
                    var dbRole = p.getRole().name(); // ADMIN / EMPLOYEE / CLIENT
                    out.add(new SimpleGrantedAuthority("ROLE_" + dbRole));
                });
            } catch (Exception ignore) {
                // se o sub não for UUID válido, ignora
            }

            return out;
        });
        return conv;
    }

    // --- Configuração de segurança principal ---
    @Bean
    SecurityFilterChain security(HttpSecurity http,
                                 JwtDecoder decoder,
                                 JwtAuthenticationConverter authConv,
                                 ProfileRepository profileRepo,
                                 RevokedTokenRepository revokedRepo) throws Exception {
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
                .requestMatchers("/actuator/health", "/ping", "/api/auth/login", "/auth/login", "/api/auth/refresh", "/auth/refresh", "/api/auth/logout", "/auth/logout", "/auth/accept-invite", "/auth/forgot-password", "/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/open/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/open/leads").permitAll()

                // 🔒 Endpoints protegidos
                .requestMatchers(HttpMethod.POST, "/auth/admin/**").hasRole("ADMIN")
                // Credenciais SMTP: quem as controla controla os emails que saem em nome da plataforma.
                .requestMatchers("/settings/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/assets").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/banners").hasRole("ADMIN")
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
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // Origens permitidas (dev + produção)
        cfg.setAllowedOrigins(List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "https://portal.minhaapp.com",
            "https://backoffice.minhaapp.com"
        ));
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