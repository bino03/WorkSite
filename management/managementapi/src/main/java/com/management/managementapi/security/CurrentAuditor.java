// src/main/java/com/management/managementapi/security/CurrentAuditor.java
package com.management.managementapi.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.management.managementapi.model.Profile;
import com.management.managementapi.repository.ProfileRepository;

@Component("currentAuditor") // se usas auditorAwareRef, mantém este nome
public class CurrentAuditor implements AuditorAware<UUID> {

  private final RequestAuditorCache cache;
  private final JdbcTemplate jdbc;
    private final ProfileRepository profileRepository; // 🔥 ADICIONA ISTO

  public CurrentAuditor(RequestAuditorCache cache, JdbcTemplate jdbc, ProfileRepository profileRepository) { // 🔥 ADICIONA ISTO
    this.cache = cache;
    this.jdbc = jdbc;
        this.profileRepository = profileRepository; // 🔥 ADICIONA ISTO
  }

  @Override
  @NonNull
  @SuppressWarnings("null") // Optional.of/empty() são non-null mas JDT não os anota como tal
  public Optional<UUID> getCurrentAuditor() {
    // 1) cache por request
    if (cache.getProfileId() != null) return Optional.of(cache.getProfileId());

    // 2) extrair auth
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) return Optional.empty();
    if (!(auth.getPrincipal() instanceof Jwt jwt)) return Optional.empty();

    // 3) primeiro tenta vir direto do token (se tiveres este claim no futuro)
    Object claim = jwt.getClaim("profile_id");
    if (claim instanceof String s) {
      try {
        UUID pid = UUID.fromString(s);
        cache.setProfileId(pid);
        return Optional.of(pid);
      } catch (IllegalArgumentException ignored) {}
    }

    // 4) fallback: query *sem JPA* (evita autoFlush/loop)
    try {
      UUID authUserId = UUID.fromString(jwt.getSubject()); // "sub"
      UUID pid = jdbc.query(
          "select id from pm.profile where auth_user_id = ? limit 1",
          ps -> ps.setObject(1, authUserId),
          rs -> rs.next() ? rs.getObject(1, java.util.UUID.class) : null
      );
      if (pid != null) {
        cache.setProfileId(pid);
        return Optional.of(pid);
      }
    } catch (Exception e) {
      // se quiseres: log a DEBUG
      // log.debug("Auditor lookup failed", e);
    }

    return Optional.empty();
  }


   /**
   * Obtém o JWT do usuário autenticado atual
   */
  public Optional<Jwt> getCurrentJwt() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) return Optional.empty();
    if (!(auth.getPrincipal() instanceof Jwt jwt)) return Optional.empty();
    
    return Optional.of(jwt);
  }

  /**
   * Obtém o token JWT como string
   */
  public Optional<String> getCurrentJwtToken() {
    return getCurrentJwt().map(Jwt::getTokenValue);
  }


  /**
   * Obtém o Profile completo do utilizador autenticado atual
   */
  public Optional<Profile> getCurrentProfile() {
    return getCurrentAuditor()
        .flatMap(profileRepository::findById);
  }

  /**
   * Obtém a role do utilizador autenticado atual do JWT
   * Tenta vários claims possíveis do Supabase
   */
  public Optional<String> getCurrentUserRole() {
    return getCurrentJwt().map(jwt -> {
      // 1) Tenta claim "role" direto
      Object roleClaim = jwt.getClaim("role");
      if (roleClaim instanceof String role && !role.isEmpty()) {
        return role;
      }

      // 2) Tenta "user_role"
      Object userRoleClaim = jwt.getClaim("user_role");
      if (userRoleClaim instanceof String userRole && !userRole.isEmpty()) {
        return userRole;
      }

      // 3) Tenta dentro de "app_metadata"
      Object appMetadata = jwt.getClaim("app_metadata");
      if (appMetadata instanceof java.util.Map) {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> metadata = 
          (java.util.Map<String, Object>) appMetadata;
        
        Object roleInMetadata = metadata.get("role");
        if (roleInMetadata instanceof String role && !role.isEmpty()) {
          return role;
        }
      }

      // Fallback: retorna "user" se nenhum claim for encontrado
      return "user";
    });
  }

  /**
   * Obtém a role do utilizador autenticado, com fallback
   */
  public String getCurrentUserRoleOrDefault(String defaultRole) {
    return getCurrentUserRole().orElse(defaultRole);
  }
}