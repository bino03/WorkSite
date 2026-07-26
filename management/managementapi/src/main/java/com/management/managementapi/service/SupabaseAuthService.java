package com.management.managementapi.service;

import com.management.managementapi.config.SupabaseConfig;
import com.management.managementapi.dto.auth.LoginRequest;
import com.management.managementapi.dto.auth.SupabaseAuthResponse;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupabaseAuthService {

    private final SupabaseConfig supabaseConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Faz login no Supabase e retorna tokens
     */
    public SupabaseAuthResponse signIn(String email, String password) {
        String url = supabaseConfig.getAuthUrl() + "/token?grant_type=password";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseConfig.getAnonKey());

        Map<String, String> body = Map.of(
            "email", email,
            "password", password
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Attempting Supabase login for email: {}", email);
            ResponseEntity<SupabaseAuthResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                SupabaseAuthResponse.class
            );

            log.info("Supabase login successful for email: {}", email);
            return response.getBody();

        } catch (HttpClientErrorException.BadRequest e) {
            // Supabase retorna 400 BAD_REQUEST para credenciais inválidas
            log.error("Supabase login failed - invalid credentials for email: {}", email);
            throw new BusinessException(ErrorCode.USER_001); // Credenciais inválidas
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Supabase login failed - unauthorized for email: {}", email);
            throw new BusinessException(ErrorCode.USER_001); // Credenciais inválidas
        } catch (HttpClientErrorException e) {
            log.error("Supabase login error: {} - {}", e.getStatusCode(), e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR); // Erro genérico
        } catch (Exception e) {
            log.error("Unexpected error during Supabase login", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Renova access token usando refresh token
     */
    public SupabaseAuthResponse refreshToken(String refreshToken) {
        String url = supabaseConfig.getAuthUrl() + "/token?grant_type=refresh_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseConfig.getAnonKey());

        Map<String, String> body = Map.of(
            "refresh_token", refreshToken
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("Attempting to refresh token");
            ResponseEntity<SupabaseAuthResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                SupabaseAuthResponse.class
            );

            log.info("Token refresh successful");
            return response.getBody();

        } catch (HttpClientErrorException.BadRequest e) {
            // Supabase retorna 400 BAD_REQUEST para refresh token inválido/expirado
            log.error("Token refresh failed - invalid or expired refresh token");
            throw new BusinessException(ErrorCode.USER_002); // Token inválido
        } catch (HttpClientErrorException.Unauthorized e) {
            log.error("Token refresh failed - unauthorized");
            throw new BusinessException(ErrorCode.USER_002); // Token inválido
        } catch (Exception e) {
            log.error("Unexpected error during token refresh", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Cria utilizador via Supabase Admin API (requer service_role key)
     */
    public SupabaseAuthResponse.SupabaseUser createUser(String email, String password) {
        String url = supabaseConfig.getUrl() + "/auth/v1/admin/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("apikey", supabaseConfig.getServiceRoleKey());
        headers.set("Authorization", "Bearer " + supabaseConfig.getServiceRoleKey());

        Map<String, Object> body = Map.of(
            "email", email,
            "password", password,
            "email_confirm", true
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<SupabaseAuthResponse.SupabaseUser> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                SupabaseAuthResponse.SupabaseUser.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.error("Supabase createUser error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 422) {
                throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
            }
            throw new BusinessException(ErrorCode.USER_CREATE_ERROR);
        }
    }

    /**
     * Faz logout no Supabase (opcional - cookies já removem acesso)
     */
    public void signOut(String accessToken) {
        String url = supabaseConfig.getAuthUrl() + "/logout";

        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseConfig.getAnonKey());
        headers.set("Authorization", "Bearer " + accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            log.info("Attempting Supabase logout");
            restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Void.class
            );
            log.info("Supabase logout successful");
        } catch (Exception e) {
            // Logout silencioso - mesmo se falhar, cookies são removidos
            log.warn("Supabase logout failed (non-critical): {}", e.getMessage());
        }
    }
}
