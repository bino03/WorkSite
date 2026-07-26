-- =============================================================
-- V10__triggers.sql
-- Apply pm.tg_set_updated_at() to all pm tables with updated_at
-- =============================================================

SET search_path TO pm, public;

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT table_schema, table_name
    FROM information_schema.columns
    WHERE table_schema = 'pm'
      AND column_name = 'updated_at'
  LOOP
    EXECUTE format(
      'CREATE OR REPLACE TRIGGER %I
       BEFORE UPDATE ON %I.%I
       FOR EACH ROW EXECUTE FUNCTION pm.tg_set_updated_at();',
      'tg_upd__' || r.table_name,
      r.table_schema,
      r.table_name
    );
  END LOOP;
END $$;
