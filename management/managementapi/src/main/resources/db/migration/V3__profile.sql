-- =============================================================
-- V3__profile.sql
-- pm.profile — utilizador interno (admin/funcionário), ligado a auth.users
-- =============================================================

SET search_path TO pm, public;

CREATE TABLE pm.profile (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id    UUID UNIQUE,
  name            TEXT NOT NULL,
  photo_url       TEXT,
  phone_number    TEXT,
  last_token_reset_at TIMESTAMPTZ,
  photo_bucket    TEXT DEFAULT 'private',
  photo_key       TEXT,
  account_status  pm.account_status_enum NOT NULL DEFAULT 'unlocked',
  role            pm.role_enum NOT NULL DEFAULT 'EMPLOYEE',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
