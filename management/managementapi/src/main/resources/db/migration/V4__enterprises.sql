-- =============================================================
-- V4__enterprises.sql
-- worksite.enterprises — projeto (empreendimento)
-- =============================================================

SET search_path TO worksite, public;

CREATE TABLE worksite.enterprises (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name                VARCHAR(255) NOT NULL,
  internal_reference  VARCHAR(100),
  type                VARCHAR(50),
  status              VARCHAR(50) NOT NULL DEFAULT 'planning',
  start_date          DATE,
  completion_date     DATE,
  total_area          NUMERIC(10,2),
  land_area           NUMERIC(10,2),
  total_units         INT,
  total_investment    NUMERIC(15,2),
  current_value       NUMERIC(15,2),
  currency            VARCHAR(3) DEFAULT 'EUR',
  construction_company VARCHAR(255),
  architect           VARCHAR(255),
  manager_id          UUID,
  owner_id            UUID,
  banner              TEXT,
  is_active           BOOLEAN NOT NULL DEFAULT TRUE,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by          UUID
);
