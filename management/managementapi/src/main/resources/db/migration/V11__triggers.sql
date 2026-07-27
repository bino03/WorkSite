-- =============================================================
-- V10__triggers.sql
-- Apply worksite.tg_set_updated_at() to all worksite tables with updated_at
-- =============================================================

SET search_path TO worksite, public;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_schema, table_name
    FROM information_schema.columns
    WHERE table_schema = 'worksite'
      AND column_name = 'updated_at'
  LOOP
    EXECUTE format(
      'CREATE OR REPLACE TRIGGER %I
       BEFORE UPDATE ON %I.%I
       FOR EACH ROW EXECUTE FUNCTION worksite.tg_set_updated_at();',
      'tg_upd__' || r.table_name,
      r.table_schema,
      r.table_name
    );
  END LOOP;
END $$;
