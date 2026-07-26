-- =============================================================
-- V2__enums.sql
-- PostgreSQL enum types used by JPA entities via columnDefinition
-- =============================================================

SET search_path TO pm, public;

-- Profile.role (columnDefinition = "pm.role_enum")
CREATE TYPE pm.role_enum AS ENUM ('ADMIN', 'EMPLOYEE');

-- Profile.account_status
CREATE TYPE pm.account_status_enum AS ENUM ('unlocked', 'blocked', 'deleted');

-- EnterprisesMedia.type / ConstructionExpense referencing media conventions
CREATE TYPE pm.media_type_enum AS ENUM (
  'image', 'floorplan', 'video', 'document', 'banner'
);

-- EnterprisesMedia.visibility
CREATE TYPE pm.visibility_enum AS ENUM ('private', 'public');

-- ActivityLog.activity_type (ActivityTypeConverter dbValues)
CREATE TYPE pm.activity_type AS ENUM (
  'view', 'create', 'edit', 'delete', 'login', 'logout', 'restore'
);

-- ActivityLog.entity_type (EntityTypeConverter dbValues)
CREATE TYPE pm.entity_type AS ENUM (
  'enterprise', 'user', 'construction_stage', 'construction_sub_stage', 'construction_expense'
);
