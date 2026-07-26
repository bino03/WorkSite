package com.management.managementapi.integrations.supabase;



import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Objects;

@Component
public class SupabaseAuthClient {

  private final RestClient rest;
  @NonNull private final String serviceRoleKey;

  public SupabaseAuthClient(RestClient.Builder builder, SupabaseProperties props) {
    String rawUrl = Objects.requireNonNull(props.getUrl(), "supabase url");
    String base = rawUrl.endsWith("/") ? rawUrl.substring(0, rawUrl.length()-1) : rawUrl;
    this.serviceRoleKey = Objects.requireNonNull(Objects.requireNonNull(props.getServiceRoleKey(), "serviceRoleKey").trim(), "serviceRoleKey.trim");
    this.rest = builder.baseUrl(Objects.requireNonNull(base, "base url")).build();

  System.out.println("[supabase] url=" + base
  + " srk.len=" + (serviceRoleKey == null ? "null" : serviceRoleKey.length())
  + " srk.prefix=" + (serviceRoleKey == null || serviceRoleKey.length()<12 ? "null" : serviceRoleKey.substring(0, 12)));

  }

  // -------- Password login (public) -> precisa de apikey (já vai por defeito)
  public TokenResponse loginWithPassword(@NonNull String email, @NonNull String password) {
    return rest.post()
        .uri("/auth/v1/token?grant_type=password") // <-- começa com /
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "contentType"))
        .body(Objects.requireNonNull(Map.of("email", email, "password", password), "body"))
        .retrieve()
        .body(TokenResponse.class);
  }

  // -------- Logout (revoke) -> precisa de apikey e Authorization: Bearer <access>
  public void logout(String accessToken) {
    rest.post()
        .uri("/auth/v1/logout")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity();
  }

  // -------- Refresh token -> usa refresh_token para gerar novo access_token
  public TokenResponse refreshToken(@NonNull String refreshToken) {
    return rest.post()
        .uri("/auth/v1/token?grant_type=refresh_token")
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "contentType"))
        .body(Objects.requireNonNull(Map.of("refresh_token", refreshToken), "body"))
        .retrieve()
        .body(TokenResponse.class);
  }

  // -------- Admin: create user -> precisa de apikey + Authorization: Bearer <service_role>
  public AdminUserResponse adminCreateUser(@NonNull String email, @NonNull String password) {
    try {
      return rest.post()
          .uri("/auth/v1/admin/users")
          // garante os DOIS headers neste call admin
          .header("apikey", serviceRoleKey)
          .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
          .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "contentType"))
          .body(Objects.requireNonNull(Map.of(
              "email", email,
              "password", password,
              "email_confirm", true
          ), "body"))
          .retrieve()
          .body(AdminUserResponse.class);
    } catch (HttpClientErrorException.Unauthorized ex) {
      // mensagem clara para debugging
      throw new IllegalStateException("Supabase 401 (Invalid API key). Confirma service_role, URL e headers.", ex);
    }
  }

  // DTOs mínimos
  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") Long expiresIn,
      @JsonProperty("token_type") String tokenType,
      User user
  ) {
    public record User(String id, String email) {}
  }

  public record AdminUserResponse(String id, String email) {}



  // -------- Admin: update app_metadata de um user (ex.: account_status=locked)
// Só admins/servidor (service_role) podem escrever em app_metadata.
public void adminUpdateUserAppMetadata(@NonNull String userId, @NonNull Map<String, Object> appMetadata) {
  try {
    rest.patch()
        .uri("/auth/v1/admin/users/{id}", userId)                    // endpoint admin
        .header("apikey", serviceRoleKey)                             // header 1: apikey
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey) // header 2: bearer service_role
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "contentType"))
        // payload JSON: {"app_metadata": { ... }}
        .body(Objects.requireNonNull(Map.of("app_metadata", appMetadata), "body"))
        .retrieve()
        .toBodilessEntity(); // 200/204 esperado
  } catch (HttpClientErrorException.Unauthorized ex) {
    throw new IllegalStateException("Supabase 401 (Invalid API key). Confirma service_role, URL e headers.", ex);
  } catch (HttpClientErrorException.Forbidden ex) {
    throw new IllegalStateException("Supabase 403 (service_role sem permissões). Confirma que usas a chave *service role*.", ex);
  }
}
public void adminSetUserAppRole(@NonNull String userId, @NonNull String role) {
  rest.patch()
     .uri("/auth/v1/admin/users/{id}", userId)
     .header("apikey", serviceRoleKey)
     .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
     .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON, "contentType"))
     .body(Objects.requireNonNull(Map.of("app_metadata", Map.of("role", role)), "body"))
     .retrieve()
     .toBodilessEntity();
}
// -------- Helper conveniente: definir só o account_status
public void adminSetAccountStatus(@NonNull String userId, @NonNull String status) {
  // valores típicos: "unlocked" | "locked" | "deleted"
  adminUpdateUserAppMetadata(userId, Objects.requireNonNull(Map.of("account_status", status), "appMetadata"));
}
}
