package com.management.managementapi.security;

// ProfileRoleJwtAuthConverter.java (corrigido)
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.repository.ProfileRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.lang.NonNull;

public class ProfileRoleJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {
  private final ProfileRepository profileRepo;

  public ProfileRoleJwtAuthConverter(ProfileRepository profileRepo) {
    this.profileRepo = profileRepo;
  }

  @Override
  public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
    Set<SimpleGrantedAuthority> auths = new HashSet<>();
    String sub = jwt.getSubject(); // UUID do utilizador no Supabase

    if (sub != null) {
      profileRepo.findByAuthUserId(UUID.fromString(sub)).ifPresent(p -> {
        // usa GETTERS — NUNCA p.role / p.accountStatus diretamente
        if (p.getRole() == ProfileRole.ADMIN) {
          auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        auths.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));
      });
    }
    return new JwtAuthenticationToken(jwt, auths);
  }
}
