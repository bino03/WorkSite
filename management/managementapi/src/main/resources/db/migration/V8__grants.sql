-- =============================================================
-- V8__grants.sql
-- Schema and table grants for Supabase roles
-- =============================================================

GRANT USAGE ON SCHEMA worksite TO anon, authenticated, service_role;
GRANT USAGE ON SCHEMA settings TO service_role;

GRANT ALL ON ALL TABLES IN SCHEMA worksite TO service_role;
GRANT ALL ON ALL TABLES IN SCHEMA settings TO service_role;

GRANT ALL ON ALL SEQUENCES IN SCHEMA worksite TO service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA worksite
  GRANT ALL ON TABLES TO service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA settings
  GRANT ALL ON TABLES TO service_role;
