---
name: frontend-integration-guide
description: Generate a frontend integration doc for a backend feature (API contract, component architecture, code templates, testing checklist) and auto-save it to the Backoffice's docs/integration folder. Use when a backend feature just got built and needs to be handed off for frontend implementation.
---

Before generating the doc, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` and `${CLAUDE_PROJECT_DIR}/docs/skills/references/frontend-visual-consistency.md` — the latter is a router: it points to the right `docs/skills/references/design/backoffice-<area>.md` sub-file(s) for whatever UI the generated doc's code templates cover. Apply that, not invented conventions.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-integration-guide.md` in full and follow its question flow exactly: ask whether the feature is from the current chat or pre-existing, then ask the 5 implementation questions, then generate and save the `.md` file to `management/managementfrontend/apps/backoffice/docs/integration/`. There is only one frontend app in this project, so there is no "which app?" question.

If asked to update this process, edit the vault file above, not this pointer.
