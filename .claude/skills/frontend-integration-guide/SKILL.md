---
name: frontend-integration-guide
description: Gerar um doc de integração frontend para uma feature do backend (contrato da API, arquitetura de componentes, templates de código, checklist de testes) e gravá-lo na pasta docs/integration do Backoffice. Usar quando uma feature do backend acabou de ser construída e tem de passar para a implementação no frontend.
---

Before generating the doc, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` (the language-agnostic principles), the "Regras de base" section of `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-design-system.md` (the frontend rules the code templates must follow) and `${CLAUDE_PROJECT_DIR}/docs/skills/references/frontend-visual-consistency.md` — the latter is a router: it points to the right `docs/skills/references/design/backoffice-<area>.md` sub-file(s) for whatever UI the generated doc's code templates cover. Apply that, not invented conventions.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-integration-guide.md` in full and follow its question flow exactly: ask whether the feature is from the current chat or pre-existing, then ask the 5 implementation questions, then generate and save the `.md` file to `management/managementfrontend/apps/backoffice/docs/integration/`. There is only one frontend app in this project, so there is no "which app?" question.

If asked to update this process, edit the vault file above, not this pointer.
