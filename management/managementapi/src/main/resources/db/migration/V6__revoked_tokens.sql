-- =============================================================
-- V6__revoked_tokens.sql
-- revoked_token for JWT revocation (pm schema, BIGSERIAL PK)
-- =============================================================

SET search_path TO pm, public;

CREATE TABLE pm.revoked_token (
  id           BIGSERIAL PRIMARY KEY,
  jti          UUID,
  token_hash   TEXT NOT NULL UNIQUE,
  auth_user_id UUID NOT NULL,
  reason       TEXT,
  revoked_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at   TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_revoked_token_hash ON pm.revoked_token (token_hash);
CREATE INDEX idx_revoked_token_auth_user ON pm.revoked_token (auth_user_id);
CREATE INDEX idx_revoked_token_expires ON pm.revoked_token (expires_at);
