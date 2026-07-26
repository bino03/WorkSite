---
name: create-new-skill
description: Meta-skill for documenting a new repeatable pattern as either an invocable project skill or a passive reference doc, and wiring it up correctly (SKILLS-INDEX.md, SKILLS-QUICK-REFERENCE.md, and — for skills only — .claude/skills/<name>/SKILL.md pointers at the repo root and in Portal). Use when you want to turn a workflow you've repeated into a documented skill, or a standing convention into a reference other skills should read.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/process/skill-create-new-skill.md` in full and follow it step by step. It first has you decide whether what you're documenting is a **skill** (invocable, gets a `.claude/skills/` pointer here and in `portal/`) or a **reference** (a conventions doc other skills read while doing their work — no pointer, no `skill-` filename prefix). Only skills get the pointer described in Step 7b/7c.

If asked to update this process, edit the vault file above, not this pointer.
