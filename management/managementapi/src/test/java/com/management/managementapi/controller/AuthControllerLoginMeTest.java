package com.management.managementapi.controller;

import com.management.managementapi.dto.auth.AuthResponse;
import com.management.managementapi.dto.auth.LoginRequest;
import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.security.CookieUtil;
import com.management.managementapi.service.ActivityLogger;
import com.management.managementapi.service.InviteService;
import com.management.managementapi.service.PasswordResetService;
import com.management.managementapi.service.SupabaseAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A remoção das fotos de perfil (2026-09-16) tirou {@code photoUrl} do
 * {@code AuthResponse.UserData} e do {@code resolvePhotoUrl(Profile)} que só
 * existia para o gerar. Nada cobria `/auth/login` nem `/auth/me` antes disto —
 * este teste fecha essa lacuna: confirma que os dois continuam a devolver os
 * dados de sessão certos sem a foto.
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginMeTest {

    @Mock private SupabaseAuthService supabaseAuthService;
    @Mock private CookieUtil cookieUtil;
    @Mock private ProfileRepository profileRepository;
    @Mock private ActivityLogger activityLogger;
    @Mock private AuthContext authContext;
    @Mock private InviteService inviteService;
    @Mock private PasswordResetService passwordResetService;

    @InjectMocks private AuthController controller;

    private static Profile profile(UUID authUserId) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setAuthUserId(authUserId);
        profile.setName("João Carvalho");
        profile.setRole(ProfileRole.ADMIN);
        return profile;
    }

    @Test
    @DisplayName("login devolve os dados de sessão sem photoUrl")
    void loginReturnsUserDataWithoutPhotoUrl() {
        UUID authUserId = UUID.randomUUID();
        Profile profile = profile(authUserId);

        SupabaseAuthResponse.SupabaseUser supabaseUser = new SupabaseAuthResponse.SupabaseUser();
        supabaseUser.setId(authUserId.toString());
        supabaseUser.setEmail("joao@worksite.pt");

        SupabaseAuthResponse supabaseResponse = new SupabaseAuthResponse();
        supabaseResponse.setAccessToken("access-token");
        supabaseResponse.setRefreshToken("refresh-token");
        supabaseResponse.setUser(supabaseUser);

        LoginRequest request = new LoginRequest();
        request.setEmail("joao@worksite.pt");
        request.setPassword("segredo123");

        when(supabaseAuthService.signIn("joao@worksite.pt", "segredo123")).thenReturn(supabaseResponse);
        when(cookieUtil.createAccessTokenCookie("access-token"))
                .thenReturn(ResponseCookie.from("access_token", "access-token").build());
        when(cookieUtil.createRefreshTokenCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refresh_token", "refresh-token").build());
        when(profileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile));

        ResponseEntity<AuthResponse> response = controller.login(
                request, mock(HttpServletRequest.class), mock(HttpServletResponse.class));

        AuthResponse.UserData userData = response.getBody().getUser();
        assertThat(userData.getId()).isEqualTo(authUserId.toString());
        assertThat(userData.getName()).isEqualTo("João Carvalho");
        assertThat(userData.getRole()).isEqualTo("ADMIN");
        assertThat(userData.getProfileId()).isEqualTo(profile.getId().toString());
        // Sem getPhotoUrl() no UserData — se a foto voltasse, isto deixava de compilar.
    }

    @Test
    @DisplayName("/auth/me devolve o email do JWT e os dados do perfil, sem photoUrl")
    void meReturnsUserDataFromJwtAndProfile() {
        UUID authUserId = UUID.randomUUID();
        Profile profile = profile(authUserId);

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(authUserId.toString());
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("email")).thenReturn("joao@worksite.pt");
        when(authentication.getPrincipal()).thenReturn(jwt);

        when(profileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.of(profile));

        ResponseEntity<AuthResponse> response = controller.me(authentication);

        AuthResponse.UserData userData = response.getBody().getUser();
        assertThat(userData.getEmail()).isEqualTo("joao@worksite.pt");
        assertThat(userData.getName()).isEqualTo("João Carvalho");
        assertThat(userData.getRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("/auth/me sem perfil ainda devolve EMPLOYEE por omissão")
    void meFallsBackToEmployeeWhenProfileMissing() {
        UUID authUserId = UUID.randomUUID();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(authUserId.toString());
        when(authentication.getPrincipal()).thenReturn("not-a-jwt");

        when(profileRepository.findByAuthUserId(authUserId)).thenReturn(Optional.empty());

        ResponseEntity<AuthResponse> response = controller.me(authentication);

        AuthResponse.UserData userData = response.getBody().getUser();
        assertThat(userData.getRole()).isEqualTo("EMPLOYEE");
        assertThat(userData.getProfileId()).isNull();
    }
}
