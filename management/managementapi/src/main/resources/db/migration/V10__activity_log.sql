-- =============================================================
-- V12__activity_log.sql
-- activity_log with custom PG enum types
-- =============================================================

SET search_path TO worksite, public;

CREATE TABLE worksite.activity_log (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL,
  user_name     VARCHAR(255) NOT NULL,
  activity_type worksite.activity_type NOT NULL,
  entity_type   worksite.entity_type,
  entity_id     UUID,
  entity_name   VARCHAR(255),
  description   TEXT,
  metadata      JSONB,
  ip_address    VARCHAR(45),
  user_agent    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_log_user_id ON worksite.activity_log (user_id);
CREATE INDEX idx_activity_log_created_at ON worksite.activity_log (created_at DESC);
CREATE INDEX idx_activity_log_activity_type ON worksite.activity_log (activity_type);
CREATE INDEX idx_activity_log_user_created ON worksite.activity_log (user_id, created_at DESC);
