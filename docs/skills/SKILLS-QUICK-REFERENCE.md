# 🎯 Skills - Quick Reference

**Fast lookup for all skills. For full details, see [[docs/skills/SKILLS-INDEX.md]]**

A **skill** is invocable (`/name`). A **reference** below is not — it's a conventions doc that skills read while doing their work (writing code or a `.md` file), never invoked on its own.

---

## 📐 References (read by skills, not invocable)

| Reference | Description | Tags | Applies to |
|-------|---|---|---|
| [[code-best-practices]] | General code quality rules shared by every skill | `#quality` `#conventions` `#backend` `#frontend` | Any skill writing/reviewing code |
| [[frontend-visual-consistency]] | Router → 8 sub-files in `docs/skills/references/design/` with verified tokens/drift per area | `#frontend` `#backoffice` `#design` | Any skill writing UI |
| [[project-vocabulary]] | Dicionário partilhado — Drawer vs. Modal, fatura vs. despesa, rubrica, token | `#vocabulary` `#frontend` `#domain` | Any skill describing code to a person |

---

## Backend Skills

| Skill | Description | Tags | Time |
|-------|---|---|---|
| [[skill-add-backend-feature]] | Complete CRUD checklist for new REST endpoints | `#backend` `#api` `#java` `#spring` | ~2-3h |
| [[skill-add-database-table]] | Database table creation with SQL template and JPA entity | `#database` `#postgresql` `#flyway` | ~30m |
| [[skill-add-file-upload]] | File upload pattern with Supabase Storage and signed URLs | `#files` `#storage` `#supabase` | ~1-2h |
| [[skill-permissions-and-auth]] | Authorization and access control implementation | `#auth` `#permissions` `#security` | ~30m |

---

## Frontend Skills

| Skill | Description | Tags | Time |
|-------|---|---|---|
| [[skill-frontend-design-system]] | React component patterns, naming, and architecture | `#frontend` `#react` `#components` | Part of component creation |
| [[skill-frontend-error-handling]] | Centralized error handling, error codes, and messages | `#frontend` `#errors` `#validation` | ~15m |
| [[skill-frontend-integration-guide]] | Generate frontend integration documentation from backend feature | `#frontend` `#integration` `#workflow` | ~15m |
| [[skill-frontend-structure-brief]] | Snapshot da estrutura atual de uma página/componente para discutir redesign | `#frontend` `#redesign` `#documentation` | ~15-20m |

---

## Process Skills

| Skill                      | Description                                                                           | Tags                                | Time               |
| -------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------- | ------------------ |
| [[skill-create-new-skill]] | Plan, write, and add a new skill (or reference) to the project                        | `#process` `#documentation` `#meta` | ~30-45m            |
| [[skill-git-commits]]      | Commit message style and conventions                                                  | `#git` `#commits` `#style`          | ~1-2m              |
| [[skill-implement-todo]]   | Backlog (`notes/ToDo.md`) → prioridade → implementação, orquestrando as outras skills | `#process` `#backlog` `#planning`   | ~1m a várias horas |

---

## By Category

**References (not invocable)**: [[code-best-practices]], [[frontend-visual-consistency]], [[project-vocabulary]]  
**Authentication & Security**: [[skill-permissions-and-auth]], [[skill-add-file-upload]]  
**Database**: [[skill-add-database-table]], [[skill-add-backend-feature]]  
**API Development**: [[skill-add-backend-feature]], [[skill-add-file-upload]], [[skill-permissions-and-auth]]  
**Frontend**: [[skill-frontend-design-system]], [[skill-frontend-error-handling]], [[skill-frontend-integration-guide]], [[skill-frontend-structure-brief]]  
**Process**: [[skill-create-new-skill]], [[skill-git-commits]], [[skill-implement-todo]]

---

## Total: 11 Invocable Skills + 3 References

- References: 3 (+ 8 design sub-files)
- Backend: 4
- Frontend: 4
- Process: 3

---

**Last Updated**: 2026-08-09

**Note**: When adding a new skill, update this file + [[docs/skills/SKILLS-INDEX.md]] + [[skill-create-new-skill.md]]. References don't get a `.claude/skills/` pointer — only skills do, see [[skill-create-new-skill]]. Files live under `docs/skills/{backend,frontend,process,references}/`.
