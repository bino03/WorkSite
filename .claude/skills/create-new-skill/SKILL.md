---
name: create-new-skill
description: Meta-skill para documentar um padrão repetível como skill invocável ou como referência passiva, e ligá-lo corretamente (SKILLS-INDEX.md, SKILLS-QUICK-REFERENCE.md e — só para skills — um ponteiro .claude/skills/<nome>/SKILL.md na raiz do repo). Usar quando queres transformar um workflow que já repetiste numa skill documentada, ou uma convenção numa referência que outras skills devem ler.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/process/skill-create-new-skill.md` in full and follow it step by step. It first has you decide whether what you're documenting is a **skill** (invocable, gets a `.claude/skills/` pointer at the repo root) or a **reference** (a conventions doc other skills read while doing their work — no pointer, no `skill-` filename prefix). Only skills get the pointer described in Step 7b.

Worksite is a single git repository with one frontend app, so there is **no mirroring step** — one pointer at the repo root covers backend and Backoffice.

If asked to update this process, edit the vault file above, not this pointer.
