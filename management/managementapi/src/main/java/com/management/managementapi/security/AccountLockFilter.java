// src/main/java/.../security/AccountLockFilter.java
package com.management.managementapi.security;

import com.management.managementapi.repository.ProfileRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.Authentication; // << IMPORT QUE FALTAVA

import java.io.IOException;
import java.util.Map;

public class AccountLockFilter extends OncePerRequestFilter {

  private final ProfileRepository profileRepo;
  private final boolean allowAuthMe; // se true, /auth/me passa mesmo bloqueado

  public AccountLockFilter(ProfileRepository profileRepo, boolean allowAuthMe) {
    this.profileRepo = profileRepo;
    this.allowAuthMe = allowAuthMe;
  }

  @Override
  protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
    if (!allowAuthMe) return false;
    String uri = request.getRequestURI();
    return "/auth/me".equals(uri) || "/actuator/health".equals(uri) || "/ping".equals(uri);
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse res,
                                   @NonNull FilterChain chain)
      throws ServletException, IOException {

    var principal = (Authentication) req.getUserPrincipal();

    if (principal instanceof JwtAuthenticationToken jwtAuth) {
      var claims = jwtAuth.getToken().getClaims();

      // 1) Bloqueio por estado
      Object am = claims.get("app_metadata");
      if (am instanceof Map<?,?> meta) {
        Object status = meta.get("account_status");
        if ("blocked".equals(status) || "deleted".equals(status)) {
          res.setStatus(HttpStatus.FORBIDDEN.value());
          res.setContentType("application/json");
          res.getWriter().write("{\"error\":\"ACCOUNT_LOCKED\",\"message\":\"A conta está Bloqueada ou Inativa.\"}");
          return;
        }
      }

      // 2) Revogação imediata por lastTokenResetAt
      var sub = jwtAuth.getName(); // = subject
      try {
        var uid = java.util.UUID.fromString(sub);
        var iat = jwtAuth.getToken().getIssuedAt(); // Instant
        if (iat != null) {
          var p = profileRepo.findByAuthUserId(uid).orElse(null);
          if (p != null && p.getLastTokenResetAt() != null &&
              iat.isBefore(p.getLastTokenResetAt())) {
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"TOKEN_REVOKED\",\"message\":\"O token foi invalidado.\"}");
            return;
          }
        }
      } catch (Exception ignore) {}
    }

    chain.doFilter(req, res);
  }

}
