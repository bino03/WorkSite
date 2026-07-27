-- =============================================================
-- V3__profile.sql
-- worksite.profile — utilizador interno (admin/funcionário), ligado a auth.users
-- =============================================================

SET search_path TO worksite, public;

CREATE TABLE worksite.profile (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id    UUID UNIQUE,
  name            TEXT NOT NULL,
  photo_url       TEXT,
  phone_number    TEXT,
  last_token_reset_at TIMESTAMPTZ,
  photo_bucket    TEXT DEFAULT 'private',
  photo_key       TEXT,
  account_status  worksite.account_status_enum NOT NULL DEFAULT 'unlocked',
  role            worksite.role_enum NOT NULL DEFAULT 'EMPLOYEE',
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
