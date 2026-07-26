-- =============================================================
-- V11__enterprises_location_media.sql
-- Join tables for location and media on enterprises
-- =============================================================

SET search_path TO pm, public;

-- ===========================
-- enterprises_location
-- ===========================
CREATE TABLE pm.enterprises_location (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  enterprise_id UUID NOT NULL,
  location_id   UUID NOT NULL,
  is_primary    BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order    INT NOT NULL DEFAULT 0,
  notes         VARCHAR(255),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE pm.enterprises_location
  ADD CONSTRAINT fk_ent_loc_enterprise
  FOREIGN KEY (enterprise_id) REFERENCES pm.enterprises(id) ON DELETE CASCADE;

ALTER TABLE pm.enterprises_location
  ADD CONSTRAINT fk_ent_loc_location
  FOREIGN KEY (location_id) REFERENCES pm.location(id) ON DELETE CASCADE;

CREATE INDEX idx_ent_loc_enterprise ON pm.enterprises_location (enterprise_id);
CREATE INDEX idx_ent_loc_location   ON pm.enterprises_location (location_id);

-- ===========================
-- enterprises_media
-- ===========================
CREATE TABLE pm.enterprises_media (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  enterprise_id   UUID NOT NULL,
  type            VARCHAR(50) NOT NULL,
  storage_key     VARCHAR(255) NOT NULL,
  bucket          VARCHAR(255) NOT NULL DEFAULT 'media',
  alt_text        VARCHAR(255),
  sort_order      INT DEFAULT 0,
  mime_type       VARCHAR(255),
  file_size_bytes BIGINT,
  width           INT,
  height          INT,
  duration_ms     INT,
  checksum_sha256 VARCHAR(64),
  visibility      pm.visibility_enum NOT NULL DEFAULT 'private',
  uploaded_by     UUID,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE pm.enterprises_media
  ADD CONSTRAINT fk_ent_media_enterprise
  FOREIGN KEY (enterprise_id) REFERENCES pm.enterprises(id) ON DELETE CASCADE;

ALTER TABLE pm.enterprises_media
  ADD CONSTRAINT fk_ent_media_uploaded_by
  FOREIGN KEY (uploaded_by) REFERENCES pm.profile(id) ON DELETE SET NULL;

CREATE INDEX idx_ent_media_enterprise ON pm.enterprises_media (enterprise_id);

-- ===========================
-- Triggers updated_at
-- ===========================
CREATE TRIGGER tg_upd__enterprises_location
  BEFORE UPDATE ON pm.enterprises_location
  FOR EACH ROW EXECUTE FUNCTION pm.tg_set_updated_at();

CREATE TRIGGER tg_upd__enterprises_media
  BEFORE UPDATE ON pm.enterprises_media
  FOR EACH ROW EXECUTE FUNCTION pm.tg_set_updated_at();
