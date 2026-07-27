-- =============================================================
-- V1__init.sql
-- Extensions + worksite schema helper function
-- =============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

SET search_path TO worksite, public;

-- updated_at trigger function (reused by V10)
CREATE OR REPLACE FUNCTION worksite.tg_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$;
