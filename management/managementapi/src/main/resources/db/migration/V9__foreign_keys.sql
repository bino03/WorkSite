-- =============================================================
-- V9__foreign_keys.sql
-- Foreign keys for the kept tables
-- =============================================================

-- FK from profile to auth.users (only if the Supabase auth schema exists)
DO $$ BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'auth' AND table_name = 'users'
  ) THEN
    ALTER TABLE pm.profile
      ADD CONSTRAINT fk_profile_authuser
      FOREIGN KEY (auth_user_id) REFERENCES auth.users(id) ON DELETE SET NULL;
  END IF;
END $$;
