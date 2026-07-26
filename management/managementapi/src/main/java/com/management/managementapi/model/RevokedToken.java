package com.management.managementapi.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revoked_token", schema = "pm")
public class RevokedToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // jti pode vir a null (nem todos os JWTs têm jti)
  @Column(columnDefinition = "uuid")
  private UUID jti;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "auth_user_id", nullable = false, columnDefinition = "uuid")
  private UUID authUserId;

  @Column
  private String reason;

  @Column(name = "revoked_at", nullable = false)
  private Instant revokedAt;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /* --- getters/setters --- */

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public UUID getJti() { return jti; }
  public void setJti(UUID jti) { this.jti = jti; }

  public String getTokenHash() { return tokenHash; }
  public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

  public UUID getAuthUserId() { return authUserId; }
  public void setAuthUserId(UUID authUserId) { this.authUserId = authUserId; }

  public String getReason() { return reason; }
  public void setReason(String reason) { this.reason = reason; }

  public Instant getRevokedAt() { return revokedAt; }
  public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

  @PrePersist
  void prePersist() {
    if (createdAt == null) createdAt = Instant.now();
    if (revokedAt == null) revokedAt = Instant.now();
  }
}
