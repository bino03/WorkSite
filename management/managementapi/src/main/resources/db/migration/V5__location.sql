-- =============================================================
-- V5__location.sql
-- worksite.location — localização standalone (reutilizada por enterprises)
-- =============================================================

SET search_path TO worksite, public;

CREATE TABLE worksite.location (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  address_line1   TEXT,
  address_line2   TEXT,
  postal_code     TEXT,
  city            TEXT,
  parish          TEXT,
  municipality    TEXT,
  country         TEXT,
  latitude        NUMERIC(9,6),
  longitude       NUMERIC(9,6),
  google_place_id TEXT,
  notes           TEXT,
  name            TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
