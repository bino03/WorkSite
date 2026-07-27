-- =============================================================
-- V14__tasks.sql
-- tasks schema: tarefa standalone, atribuível a um ou mais
-- utilizadores (worksite.profile). Sem ligação a nenhum ativo/imóvel
-- (esse conceito não existe neste projeto).
-- =============================================================

GRANT USAGE ON SCHEMA tasks TO service_role;

CREATE TYPE tasks.task_status_enum AS ENUM ('PENDING', 'IN_PROGRESS', 'DONE');

CREATE TABLE tasks.task (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  description TEXT,
  due_date    TIMESTAMPTZ NOT NULL,
  status      tasks.task_status_enum NOT NULL DEFAULT 'PENDING',
  created_by  UUID REFERENCES worksite.profile(id) ON DELETE SET NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE tasks.task_assignee (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  task_id     UUID NOT NULL REFERENCES tasks.task(id) ON DELETE CASCADE,
  profile_id  UUID NOT NULL REFERENCES worksite.profile(id) ON DELETE CASCADE,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (task_id, profile_id)
);

CREATE INDEX idx_task_status ON tasks.task (status);
CREATE INDEX idx_task_created_by ON tasks.task (created_by);
CREATE INDEX idx_task_assignee_task_id ON tasks.task_assignee (task_id);
CREATE INDEX idx_task_assignee_profile_id ON tasks.task_assignee (profile_id);

CREATE TRIGGER tg_upd__task
  BEFORE UPDATE ON tasks.task
  FOR EACH ROW EXECUTE FUNCTION worksite.tg_set_updated_at();

GRANT ALL ON ALL TABLES IN SCHEMA tasks TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA tasks TO service_role;

ALTER DEFAULT PRIVILEGES IN SCHEMA tasks
  GRANT ALL ON TABLES TO service_role;
