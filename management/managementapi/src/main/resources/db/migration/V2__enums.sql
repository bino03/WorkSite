-- =============================================================
-- V2__enums.sql
-- PostgreSQL enum types used by JPA entities via columnDefinition
-- =============================================================

SET search_path TO worksite, public;

-- Profile.role (columnDefinition = "worksite.role_enum")
CREATE TYPE worksite.role_enum AS ENUM ('ADMIN', 'EMPLOYEE');

-- Profile.account_status
CREATE TYPE worksite.account_status_enum AS ENUM ('unlocked', 'blocked', 'deleted');

-- EnterprisesMedia.type / ConstructionExpense referencing media conventions
CREATE TYPE worksite.media_type_enum AS ENUM (
  'image', 'floorplan', 'video', 'document', 'banner'
);

-- EnterprisesMedia.visibility
CREATE TYPE worksite.visibility_enum AS ENUM ('private', 'public');

-- ActivityLog.activity_type (ActivityTypeConverter dbValues)
CREATE TYPE worksite.activity_type AS ENUM (
  'view', 'create', 'edit', 'delete', 'login', 'logout', 'restore'
);

-- ActivityLog.entity_type (EntityTypeConverter dbValues)
CREATE TYPE worksite.entity_type AS ENUM (
  'enterprise', 'user', 'construction_stage', 'construction_sub_stage', 'construction_expense', 'task'
);
