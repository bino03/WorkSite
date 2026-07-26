-- =============================================================
-- V12__activity_log.sql
-- activity_log with custom PG enum types
-- =============================================================

SET search_path TO pm, public;

CREATE TABLE pm.activity_log (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       UUID NOT NULL,
  user_name     VARCHAR(255) NOT NULL,
  activity_type pm.activity_type NOT NULL,
  entity_type   pm.entity_type,
  entity_id     UUID,
  entity_name   VARCHAR(255),
  description   TEXT,
  metadata      JSONB,
  ip_address    VARCHAR(45),
  user_agent    TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_activity_log_user_id ON pm.activity_log (user_id);
CREATE INDEX idx_activity_log_created_at ON pm.activity_log (created_at DESC);
CREATE INDEX idx_activity_log_activity_type ON pm.activity_log (activity_type);
CREATE INDEX idx_activity_log_user_created ON pm.activity_log (user_id, created_at DESC);
