# 📚 Documentation

Central documentation for the Worksite project (backend + backoffice).

## Structure

- **code-map.md** — Onde vive cada funcionalidade (domínio → ficheiros) + tabela "onde procurar, por sintoma". É por aqui que se começa quando a pergunta é *"onde está o código disto?"*
- **architecture.md** — System architecture overview
- **database.md** — Database schema and design
- **security.md** — Security & authorization
- **api.md** — API endpoint reference (rotas, métodos, regras de acesso por controller)
- **vault-sync-hooks.md** — O hook `pre-commit` que avisa quando um commit precisa de uma atualização de docs
- **skills/** — Everything related to invocable skills, organized by topic:
  - **[[SKILLS-INDEX]]** / **[[SKILLS-QUICK-REFERENCE]]** — Master index of all skills and references
  - **backend/** — 4 backend skills (`skill-add-backend-feature`, `skill-add-database-table`, `skill-add-file-upload`, `skill-permissions-and-auth`)
  - **frontend/** — 4 frontend skills (`skill-frontend-design-system`, `skill-frontend-error-handling`, `skill-frontend-integration-guide`, `skill-frontend-structure-brief`)
  - **process/** — 3 process/workflow skills (`skill-create-new-skill`, `skill-git-commits`, `skill-implement-todo`)
  - **references/** — Conventions docs read by skills, not invocable on their own: [[code-best-practices]], [[frontend-visual-consistency]], and **design/** (8 Backoffice sub-files with verified visual/structural conventions, auditadas contra o código real deste projeto — as sub-files específicas do Portal foram descartadas por este projeto não ter portal público)

## Keeping this in sync

O hook `.githooks/pre-commit` avisa (sem bloquear) quando o diff staged toca ficheiros que normalmente exigem atualizar um destes documentos. Num clone novo, ativa-o com:

```bash
git config core.hooksPath .githooks
```

Ver [[vault-sync-hooks]] para a lista completa do que é detetado.

## Related

- Backend: [[../management/managementapi/CLAUDE.md]]
- Frontend: [[../management/managementfrontend/CLAUDE.md]]
- Backlog & notas pessoais: [[../notes/README.md]]
