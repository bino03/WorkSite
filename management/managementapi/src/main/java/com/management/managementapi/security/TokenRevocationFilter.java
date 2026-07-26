package com.management.managementapi.security;

import com.management.managementapi.repository.RevokedTokenRepository;
import com.management.managementapi.util.TokenHash;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class TokenRevocationFilter extends OncePerRequestFilter {
  private final RevokedTokenRepository repo;

  public TokenRevocationFilter(RevokedTokenRepository repo) {
    this.repo = repo;
  }

  @Override
  protected void doFilterInternal(@NonNull HttpServletRequest req,
                                   @NonNull HttpServletResponse res,
                                   @NonNull FilterChain chain)
      throws ServletException, IOException {

    String auth = req.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
      String token = auth.substring(7);
      String hash = TokenHash.sha256B64(token);
      if (repo.existsByTokenHash(hash)) {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"TOKEN_REVOKED\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
