---
name: create-new-skill
description: Meta-skill for documenting a new repeatable pattern as either an invocable project skill or a passive reference doc, and wiring it up correctly (SKILLS-INDEX.md, SKILLS-QUICK-REFERENCE.md, and — for skills only — a .claude/skills/<name>/SKILL.md pointer at the repo root). Use when you want to turn a workflow you've repeated into a documented skill, or a standing convention into a reference other skills should read.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/process/skill-create-new-skill.md` in full and follow it step by step. It first has you decide whether what you're documenting is a **skill** (invocable, gets a `.claude/skills/` pointer at the repo root) or a **reference** (a conventions doc other skills read while doing their work — no pointer, no `skill-` filename prefix). Only skills get the pointer described in Step 7b.

Worksite is a single git repository with one frontend app, so there is **no mirroring step** — one pointer at the repo root covers backend and Backoffice.

If asked to update this process, edit the vault file above, not this pointer.
