-- =============================================================
-- V7__settings_schema.sql
-- settings schema: pending_invites, email_providers
-- =============================================================

GRANT USAGE ON SCHEMA settings TO service_role;

CREATE TABLE settings.pending_invites (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_by   UUID,
  email        VARCHAR(255) NOT NULL,
  phone        VARCHAR(50),
  role         TEXT NOT NULL,
  invite_token VARCHAR(500) NOT NULL UNIQUE,
  status       TEXT NOT NULL DEFAULT 'PENDING',
  expires_at   TIMESTAMPTZ NOT NULL,
  sent_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  accepted_at  TIMESTAMPTZ,
  invited_by   UUID NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE settings.email_providers (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  provider_name VARCHAR(100) NOT NULL,
  host          VARCHAR(255) NOT NULL,
  port          INT NOT NULL,
  username      VARCHAR(255) NOT NULL,
  password      VARCHAR(255) NOT NULL,
  from_email    VARCHAR(255) NOT NULL,
  from_name     VARCHAR(255),
  encryption    VARCHAR(20) DEFAULT 'tls',
  is_default    BOOLEAN,
  is_active     BOOLEAN DEFAULT TRUE,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
