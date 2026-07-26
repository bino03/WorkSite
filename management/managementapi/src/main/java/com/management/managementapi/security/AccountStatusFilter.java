package com.management.managementapi.security;

import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.repository.ProfileRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class AccountStatusFilter extends OncePerRequestFilter {

  private final ProfileRepository profileRepo;
  private final Set<String> ignorePrefixes = Set.of("/auth/login");

  public AccountStatusFilter(ProfileRepository profileRepo) {
    this.profileRepo = profileRepo;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    String path = request.getRequestURI();
    return ignorePrefixes.stream().anyMatch(path::startsWith);
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest request,
                                   @NonNull HttpServletResponse response,
                                   @NonNull FilterChain chain)
      throws ServletException, IOException {
    Authentication a = SecurityContextHolder.getContext().getAuthentication();
    if (a instanceof JwtAuthenticationToken jwtAuth) {
      String sub = jwtAuth.getToken().getSubject();
      if (sub != null) {
        var prof = profileRepo.findByAuthUserId(UUID.fromString(sub))
            .orElse(null);
        if (prof == null || prof.getAccountStatus() != AccountStatus.unlocked) {
          response.setStatus(HttpServletResponse.SC_FORBIDDEN);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\":\"account_not_unlocked\"}");
          return;
        }
      }
    }
    chain.doFilter(request, response);
  }
}