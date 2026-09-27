---
name: frontend-design-system
description: Padrões de componentes React do Backoffice do Worksite — estrutura de pastas (create/edit/view), convenções de nomes, formulários com React Hook Form + Zod, camada de serviços, padrão Drawer, estado local/Context (sem Zustand neste projeto), estilos com Tailwind + Ant Design. Usar ao construir qualquer componente React neste projeto.
---

Before writing any component code, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` (the language-agnostic principles only — the frontend rules are in the skill file itself, "Regras de base") and `${CLAUDE_PROJECT_DIR}/docs/skills/references/frontend-visual-consistency.md` — the latter is a router: it points to the specific `docs/skills/references/design/backoffice-<area>.md` sub-file for what you're building (tokens, cards, drawers-and-modals, tables-and-lists, buttons-and-icons, forms-and-validation, services-and-error-handling, app-shell-and-auth). Apply what that sub-file says instead of inventing new colors/widths/patterns.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-design-system.md` in full and follow it step by step for the component structure, naming, and patterns.

If asked to update this checklist, edit the vault file above, not this pointer.
