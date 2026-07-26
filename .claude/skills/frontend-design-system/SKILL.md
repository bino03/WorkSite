---
name: frontend-design-system
description: React component patterns for the Backoffice/Portal frontends — folder structure (create/edit/view), naming conventions, React Hook Form + Zod forms, service layer, Drawer pattern, Zustand for list state, Tailwind + Ant Design styling. Use when building any React component in this monorepo's frontends.
---

Before writing any component code, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` (general conventions) and `${CLAUDE_PROJECT_DIR}/docs/skills/references/frontend-visual-consistency.md` — the latter is a router: it has you determine Backoffice vs Portal first, then points to the specific `docs/skills/references/design/<project>-<area>.md` sub-file for what you're building (cards, drawers, tables, buttons, tokens, listings, detail pages, layout). Apply what that sub-file says instead of inventing new colors/widths/patterns.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-design-system.md` in full and follow it step by step for the component structure, naming, and patterns.

If asked to update this checklist, edit the vault file above, not this pointer.
