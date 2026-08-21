package com.management.managementapi.controller;

import com.management.managementapi.dto.auth.AuthDTO;
import com.management.managementapi.dto.auth.AuthResponse;
import com.management.managementapi.dto.auth.LoginRequest;
import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.security.CookieUtil;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.InviteService;
import com.management.managementapi.service.PasswordResetService;
import com.management.managementapi.service.SupabaseAuthService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.model.Profile;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SupabaseAuthService supabaseAuthService;
    private final CookieUtil cookieUtil;
    private final ProfileRepository profileRepository;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;
    private final SupabaseStorageService storage;
    private final InviteService inviteService;
    private final PasswordResetService passwordResetService;

    /**
     * Login: Frontend → Backend → Supabase → Cookies
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("=== LOGIN REQUEST ===");
        log.info("Email: {}", loginRequest.getEmail());

        try {
            // 1. Chama Supabase Auth
            SupabaseAuthResponse supabaseResponse = supabaseAuthService.signIn(
                loginRequest.getEmail(),
                loginRequest.getPassword()
            );

            log.info("✅ JWT obtido do Supabase! Tamanho: {}", supabaseResponse.getAccessToken().length());

            // 2. Converte tokens em HttpOnly cookies (usando headers)
            var accessCookie = cookieUtil.createAccessTokenCookie(
                supabaseResponse.getAccessToken()
            );
            var refreshCookie = cookieUtil.createRefreshTokenCookie(
                supabaseResponse.getRefreshToken()
            );

            response.addHeader("Set-Cookie", accessCookie.toString());
            response.addHeader("Set-Cookie", refreshCookie.toString());

            log.info("✅ Cookies criados e adicionados ao response!");

            // 3. Consulta Profile na BD para obter role correta
            UUID authUserId = UUID.fromString(supabaseResponse.getUser().getId());
            Profile profile = profileRepository.findByAuthUserId(authUserId).orElse(null);

            // 4. Retorna apenas user data (SEM tokens no body!)
            String photoUrl = resolvePhotoUrl(profile);

            AuthResponse.UserData userData = new AuthResponse.UserData(
                supabaseResponse.getUser().getId(),
                supabaseResponse.getUser().getEmail(),
                profile != null ? profile.getName() : (
                    supabaseResponse.getUser().getUserMetadata() != null
                        ? supabaseResponse.getUser().getUserMetadata().getName()
                        : null
                ),
                profile != null ? profile.getRole().name() : "EMPLOYEE",
                photoUrl,
                profile != null ? profile.getId().toString() : null
            );

            log.info("✅ Login successful for email: {} with role: {}", loginRequest.getEmail(), userData.getRole());

            if (profile != null) {
                activityLogger.logLogin(authUserId, profile.getName(), request);
            }

            return ResponseEntity.ok(new AuthResponse(userData));
        } catch (Exception e) {
            log.error("❌ Erro no login: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Aceitar convite: token do email → conta no Supabase + perfil na BD.
     *
     * Público (já estava em `permitAll` no `SecurityConfig`) — quem aceita ainda não
     * tem conta. Não devolve sessão: a página redireciona para o login, para a
     * password acabada de definir ser usada uma vez à entrada.
     */
    @PostMapping("/accept-invite")
    public ResponseEntity<Void> acceptInvite(@Valid @RequestBody AuthDTO.AcceptInviteRequest request) {
        inviteService.accept(request.token(), request.password(), request.name());
        return ResponseEntity.noContent().build();
    }

    /**
     * Pedir recuperação de password: email → link com token.
     *
     * Devolve sempre `204`, exista ou não conta com aquele email. Público e sem
     * autenticação: distinguir os dois casos transformava isto num verificador de
     * contas para quem quisesse experimentar emails à sorte.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody AuthDTO.ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    /** Definir a password nova a partir do token do email. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody AuthDTO.ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Refresh: Lê refresh_token do cookie → Novo access_token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Token refresh request received");

        // 1. Lê refresh token do cookie
        String refreshToken = getRefreshTokenFromCookies(request);
        if (refreshToken == null) {
            log.error("Refresh token not found in cookies");
            return ResponseEntity.status(401).build();
        }

        // 2. Chama Supabase para renovar
        SupabaseAuthResponse supabaseResponse = supabaseAuthService.refreshToken(refreshToken);

        // 3. Atualiza cookies (usando headers)
        response.addHeader("Set-Cookie", cookieUtil.createAccessTokenCookie(
            supabaseResponse.getAccessToken()
        ).toString());
        response.addHeader("Set-Cookie", cookieUtil.createRefreshTokenCookie(
            supabaseResponse.getRefreshToken()
        ).toString());

        log.info("Token refresh successful - New cookies set");

        // 4. Consulta Profile na BD para obter role correta
        UUID authUserId = UUID.fromString(supabaseResponse.getUser().getId());
        Profile profile = profileRepository.findByAuthUserId(authUserId).orElse(null);

        // 5. Retorna user data
        String photoUrl = resolvePhotoUrl(profile);

        AuthResponse.UserData userData = new AuthResponse.UserData(
            supabaseResponse.getUser().getId(),
            supabaseResponse.getUser().getEmail(),
            profile != null ? profile.getName() : (
                supabaseResponse.getUser().getUserMetadata() != null
                    ? supabaseResponse.getUser().getUserMetadata().getName()
                    : null
            ),
            profile != null ? profile.getRole().name() : "EMPLOYEE",
            photoUrl,
            profile != null ? profile.getId().toString() : null
        );

        return ResponseEntity.ok(new AuthResponse(userData));
    }

    /**
     * Logout: Remove cookies + Supabase logout (opcional)
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        log.info("Logout request received");

        // 1. Tenta fazer logout no Supabase (opcional)
        String accessToken = getAccessTokenFromCookies(request);
        if (accessToken != null) {
            try {
                supabaseAuthService.signOut(accessToken);
            } catch (Exception e) {
                log.warn("Supabase logout failed (non-critical): {}", e.getMessage());
            }
        }

        // 2. Remove cookies (usando headers)
        response.addHeader("Set-Cookie", CookieUtil.createLogoutCookie("access_token").toString());
        response.addHeader("Set-Cookie", CookieUtil.createLogoutCookie("refresh_token").toString());

        log.info("Logout successful - Cookies cleared");

        authContext.currentProfileId().ifPresent(uid ->
            activityLogger.logLogout(uid, authContext.currentUserName().orElse("unknown"), request));

        return ResponseEntity.noContent().build();
    }

    /**
     * GET /auth/me — Dados do utilizador autenticado com signed URL fresca
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication authentication) {
        UUID authUserId = UUID.fromString(authentication.getName());
        Profile profile = profileRepository.findByAuthUserId(authUserId).orElse(null);

        String email = null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            email = jwt.getClaimAsString("email");
        }

        AuthResponse.UserData userData = new AuthResponse.UserData(
            authUserId.toString(),
            email,
            profile != null ? profile.getName() : null,
            profile != null ? profile.getRole().name() : "EMPLOYEE",
            resolvePhotoUrl(profile),
            profile != null ? profile.getId().toString() : null
        );

        return ResponseEntity.ok(new AuthResponse(userData));
    }

    private String resolvePhotoUrl(Profile profile) {
        if (profile == null || profile.getPhotoKey() == null) return null;
        try {
            String bucket = profile.getPhotoBucket();
            String key = profile.getPhotoKey().startsWith("/") ? profile.getPhotoKey().substring(1) : profile.getPhotoKey();
            return storage.createSignedUrl(bucket, key, 3600);
        } catch (Exception e) {
            log.warn("Não foi possível gerar signed URL para a foto do perfil: {}", e.getMessage());
            return null;
        }
    }

    private String getAccessTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(cookie -> "access_token".equals(cookie.getName()))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
    }

    private String getRefreshTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(cookie -> "refresh_token".equals(cookie.getName()))
            .findFirst()
            .map(Cookie::getValue)
            .orElse(null);
    }
}
