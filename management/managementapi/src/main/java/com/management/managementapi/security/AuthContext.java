package com.management.managementapi.security;

import com.management.managementapi.repository.ProfileRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuthContext {

  private final ProfileRepository profileRepository;

  public AuthContext(ProfileRepository profileRepository) {
    this.profileRepository = profileRepository;
  }

  public Optional<UUID> currentProfileId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return Optional.empty();

    Object principal = auth.getPrincipal();
    if (principal instanceof Jwt jwt) {
      // Tentar claim explícito primeiro
      String pid = jwt.getClaimAsString("profile_id");
      if (pid == null) pid = jwt.getClaimAsString("profileId");
      if (pid == null) pid = jwt.getClaimAsString("profile");
      if (pid != null) {
        try { return Optional.of(UUID.fromString(pid)); } catch (Exception ignore) {}
      }
      // Fallback: lookup na BD pelo sub (auth user UUID) → profile.id
      String sub = jwt.getClaimAsString("sub");
      if (sub != null) {
        try {
          UUID authUserId = UUID.fromString(sub);
          return profileRepository.findByAuthUserId(authUserId).map(p -> p.getId());
        } catch (Exception ignore) {}
      }
    }
    return Optional.empty();
  }

  /**
   * Obtém o nome do utilizador atual.
   * Primeiro tenta o claim "name"/"full_name" do JWT.
   * Se não existir, vai à BD buscar o perfil pelo "sub" (auth user UUID).
   * Último fallback: email do JWT.
   */
  public Optional<String> currentUserName() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) return Optional.empty();
    Object principal = auth.getPrincipal();
    if (!(principal instanceof Jwt jwt)) return Optional.empty();

    String name = jwt.getClaimAsString("name");
    if (name == null) name = jwt.getClaimAsString("full_name");

    if (name != null) return Optional.of(name);

    // JWT não tem nome — procurar na BD pelo sub (auth user UUID)
    String sub = jwt.getClaimAsString("sub");
    if (sub != null) {
      try {
        UUID authUserId = UUID.fromString(sub);
        Optional<String> profileName = profileRepository.findByAuthUserId(authUserId)
            .map(p -> p.getName());
        if (profileName.isPresent()) return profileName;
      } catch (Exception ignore) {}
    }

    // Último fallback: email
    return Optional.ofNullable(jwt.getClaimAsString("email"));
  }
}
