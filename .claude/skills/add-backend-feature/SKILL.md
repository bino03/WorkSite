---
name: add-backend-feature
description: Checklist completo para acrescentar um endpoint/feature REST ao backend Spring Boot (management/managementapi) — ErrorCodes, DTOs, Repository, mapper MapStruct, Service, Controller, SecurityConfig, EntityType, códigos de estado da resposta. Usar ao acrescentar um recurso ou feature à API do backend.
---

Before writing any code, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` — it's a reference, not a skill, but every line of code this skill produces (DTOs, Service, Controller, etc.) should follow it.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/backend/skill-add-backend-feature.md` in full and follow it step by step for this task. It also points to `skill-add-database-table`, `skill-add-file-upload`, and `skill-permissions-and-auth` when relevant — check those too if the feature needs a new table, file uploads, or access control.

If asked to update this checklist, edit the vault file above, not this pointer.
