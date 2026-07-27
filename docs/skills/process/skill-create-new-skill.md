# Skill: Create a New Skill

**When to use**: When you want to document a repeatable implementation pattern or workflow

**Time**: ~30-45 minutes (depending on complexity)

> 📐 See also [[code-best-practices]] — every new skill involving code should link to it instead of repeating general naming/error-handling rules (see Step 6).

---

## What is a Skill?

A **skill** is a step-by-step guide for implementing a **repeatable pattern** in your project. It's **invocable** — it gets a `.claude/skills/<name>/SKILL.md` pointer so you can run it directly (`/name`).

**DO create a skill for:**
- ✅ How to add a backend feature (CRUD checklist)
- ✅ How to handle errors in frontend
- ✅ How to set up database tables
- ✅ How to configure authentication
- ✅ Any workflow you'll repeat multiple times

**DON'T create a skill for:**
- ❌ One-time features
- ❌ Project roadmaps (put in `notes/roadmap.md`)
- ❌ Architecture decisions (put in `docs/architecture.md`)
- ❌ Bug reports (put in `notes/bugs.md`)

### Skill vs Reference — don't confuse the two

A **reference** (like [[code-best-practices]] or [[frontend-visual-consistency]]) is *not* a skill, even though it lives under `docs/skills/` alongside skill files — specifically in `docs/skills/references/`. The difference:

| | Skill | Reference |
|---|---|---|
| Invoked directly? | Yes — `/name` | No — never on its own |
| Gets a `.claude/skills/` pointer? | Yes (root + Portal) | No |
| Folder | `docs/skills/<category>/skill-<name>.md` (`<category>` = `backend`, `frontend`, or `process`) | `docs/skills/references/<name>.md` (no `skill-` prefix) |
| Purpose | A checklist you *run* to do something | Conventions another skill *reads* while doing its work, then applies to whatever it produces (code or a `.md` file) |
| Example | "Add a backend feature" — you follow it top to bottom | "Code best practices" — no one runs this alone; `add-backend-feature` reads it before writing the DTOs/Service/Controller |

If what you're documenting is genuinely a set of standing conventions rather than a sequence of steps to execute, it's a reference — skip the `.claude/skills/` pointer entirely (Steps 7b/7c don't apply) and instead go add a line to the **actionable** skills that should read it, telling them to do so before producing their output. See how `code-best-practices.md` is cited from `add-backend-feature`, `frontend-design-system`, etc.

---

## Step 1: Decide If It's a Skill (and if so, a Skill or a Reference)

Ask yourself:

1. **Will I use this pattern again?** → YES = Skill or Reference ✅
2. **Is it a sequence of steps you execute, ending in something built/committed?** → YES = **Skill**. **Is it instead a standing convention other work should conform to, with nothing to "run"?** → YES = **Reference** (see table above)
3. **Can someone else follow it (steps) or apply it (convention)?** → YES = proceed
4. **Is it a one-time task?** → YES = Don't document it as either ❌

If it's a Skill, proceed to Step 2 as normal, and also decide which category it belongs to: `backend`, `frontend`, or `process` (git conventions, meta-workflow — neither stack-specific). If it's a Reference, skip ahead to **Step 4** (write the content) — Steps 2-3's metadata/step-outline format still mostly applies, but drop "Time estimate" (references are consulted, not timed) and skip Steps 7b/7c entirely (no `.claude/skills/` pointer). Remember to add it to the **References** section of `SKILLS-INDEX.md`/`SKILLS-QUICK-REFERENCE.md`, not the numbered skill categories, and to go edit each actionable skill that should read it (add a "read this before writing X" line to its own `docs/skills/<category>/skill-<name>.md` **and** its `.claude/skills/<name>/SKILL.md` pointer).

---

## Step 2: Define the Skill Metadata

Before writing, answer these:

```
Name: What is the short name? (examples: "add-backend-feature", "error-handling")
Description: One-line summary of what this teaches
Time estimate: ~30 min, ~1-2 hours, etc.
Category: backend, frontend, or process? (determines which docs/skills/ subfolder it lives in)
Tags: #backend, #frontend, #auth, #database, etc.
When to use: Specific scenarios where you'd apply this
```

**Example:**

```
Name: create-database-migration
Description: Plan and execute database migrations with Flyway
Time: ~45 minutes
Category: backend
Tags: #database, #postgresql, #flyway, #migration
When to use: Adding a new table or modifying schema
```

---

## Step 3: Outline the Steps

Write the main steps as headers (Step 1, Step 2, etc.):

```markdown
# Skill: Your Skill Name

**When to use**: [From metadata above]
**Time**: ~X hours/minutes

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist. (omit this line only if the skill has nothing to do with writing code, e.g. pure process skills) [[frontend-visual-consistency]] too, if this skill writes or generates UI code — it routes to the real Backoffice/Portal design tokens instead of letting the skill invent new ones.

---

## Step 1: [First action]
Brief explanation + code example if needed

## Step 2: [Second action]
Brief explanation + code example if needed

## Step 3: [Third action]
...

## Final Checklist
- [ ] Item 1
- [ ] Item 2
- [ ] Item 3

## Related Skills
- [[code-best-practices]] — General code quality rules
- [[frontend-visual-consistency]] — Design tokens/patterns (only if this skill writes UI code)
- [[skill-other]] — If this relates to another skill
```

---

## Step 4: Write Detailed Content

For each step:

1. **Explain WHY** — What problem does this solve?
2. **Show HOW** — Code examples, templates, configs
3. **Show WHAT** — Expected output or result
4. **Avoid OVER-EXPLAINING** — Assume reader knows basics

**Good example:**

```markdown
## Step 2: Create Flyway Migration

Flyway needs migrations named `V{number}__{description}.sql`.

Create `src/main/resources/db/migration/V20__add_users_table.sql`:

\`\`\`sql
CREATE TABLE worksite.users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) NOT NULL UNIQUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
\`\`\`

Run: `./mvnw flyway:migrate`
```

---

## Step 5: Add Checklist

At the end, include a **Final Checklist** so people know when they're done:

```markdown
## Final Checklist

- [ ] Step 1 completed
- [ ] Step 2 completed
- [ ] Error handling added
- [ ] Tests written
- [ ] Commit message follows conventions
- [ ] Code reviewed by team
```

---

## Step 6: Link to Related Skills

Add a **Related Skills** section at the bottom. If the skill touches code (backend or frontend), link [[code-best-practices]] first — don't repeat its general naming/error-handling rules inline, just link to it. **If the skill writes or generates UI code (Backoffice or Portal), also link [[frontend-visual-consistency]]** — it's the router to real, verified design tokens/patterns per project (`docs/skills/references/design/backoffice-*.md`, `docs/skills/references/design/portal-*.md`); don't invent colors, widths, or component patterns that it already documents:

```markdown
## Related Skills

- [[code-best-practices]] — General code quality rules
- [[frontend-visual-consistency]] — Design tokens and patterns (only if this skill writes UI code)
- [[skill-add-backend-feature]] — Includes this workflow
- [[skill-git-commits]] — Commit message format
- [[skill-permissions-and-auth]] — If this involves auth
```

---

## Step 7: Create the File

Create: `docs/skills/<category>/skill-<name>.md`, where `<category>` is `backend`, `frontend`, or `process` (decided in Step 1).

```bash
# Name it after the pattern, not the feature
# Good: skill-database-migration.md    (in docs/skills/backend/)
# Good: skill-error-handling.md        (in docs/skills/frontend/)
# Bad: skill-user-table.md (too specific)
```

---

## Step 7b: Create the Claude Code Skill Pointer (repo root — covers backend AND Backoffice)

Create `.claude/skills/<name>/SKILL.md` at the **repo root** (`Property-Management/.claude/skills/`, not inside any sub-project). `management/managementapi`, `management/managementfrontend` (including Backoffice), and the vault root are all **the same git repository** — Claude Code searches upward from the working directory to the repo root for `.claude/skills/`, so one pointer here is automatically enough for backend *and* Backoffice work. Do **not** create separate copies inside `managementapi/.claude/` or `managementfrontend/.claude/` — that would just be a second thing to keep in sync for no benefit. Drop the `skill-` prefix from the directory name (the vault file keeps it, the command doesn't need it).

```markdown
---
name: your-skill-name
description: One or two sentences — what it covers AND when to use it, with concrete trigger words. This is what Claude reads to decide whether to invoke the skill, so be specific, not just a title restated.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/<category>/skill-your-skill-name.md` in full and follow it step by step.

If asked to update this checklist, edit the vault file above, not this pointer.
```

**Keep it a thin pointer** — the vault file (`docs/skills/<category>/skill-your-skill-name.md`) stays the single source of truth. Don't duplicate steps or checklists into the `SKILL.md` body; if the two drift apart, whoever reads the `SKILL.md` gets outdated instructions.

---

## Step 7c: Mirror the Pointer to Portal (always — it's a separate repo)

`portal/` is a **separate git repository** (submodule) — the Step 7b pointer is invisible from inside it, and `${CLAUDE_PROJECT_DIR}` there resolves to Portal's own root, not the vault. **Always** create `portal/.claude/skills/<name>/SKILL.md` too — which of the 3 templates below to use depends on what the skill covers:

**A. Universal skill** (git conventions, general code quality, meta-process — nothing stack-specific) → mirror as a plain pointer, same as Step 7b but with an absolute path since `${CLAUDE_PROJECT_DIR}` doesn't reach the vault from Portal:

```markdown
---
name: your-skill-name
description: [same description as the root one]
---

Read `C:\Users\jlalv\Desktop\utad\projetos\Property-Management\docs\skills\<category>\skill-your-skill-name.md` in full and follow it.

If asked to update this checklist, edit the vault file above, not this pointer.
```

**B. Backend-only skill** (Spring Boot / database / anything under `managementapi`) → Portal has no backend code, so mark it reference-only instead of actionable:

```markdown
---
name: your-skill-name
description: Reference only — [what it covers] for the Spring Boot backend (management/managementapi), which lives in a different repo. [why Portal doesn't need to act on it].
---

This Portal repo has no [backend/database/auth/upload] code — read `C:\Users\jlalv\Desktop\utad\projetos\Property-Management\docs\skills\backend\skill-your-skill-name.md` only for background; there's nothing to implement here.
```

**C. Backoffice-specific frontend skill** (assumes Ant Design, Zustand, React Hook Form — stack Portal doesn't have) → mark reference-only, state what Portal actually does instead, AND point to `frontend-visual-consistency.md`'s Portal sub-files for the real (actionable) equivalent — don't just say "doesn't apply" and stop there:

```markdown
---
name: your-skill-name
description: [what it covers] written for the Backoffice admin app ([the specific libraries assumed]). Use only as a reference — Portal does NOT use this stack.
---

Read `C:\Users\jlalv\Desktop\utad\projetos\Property-Management\docs\skills\frontend\skill-your-skill-name.md` for reference, but **do not apply it literally to this Portal codebase** — [state the real Portal pattern from portal/CLAUDE.md instead].

For actual visual/structural conventions (as opposed to the Backoffice-specific doc above), also read `C:\Users\jlalv\Desktop\utad\projetos\Property-Management\docs\skills\references\frontend-visual-consistency.md` — it routes to real Portal sub-files under `docs/skills/references/design/portal-*.md` (tokens, components, listings, detail pages, layout). These document verified, real Portal conventions — read whichever matches what you're building.
```

If genuinely unsure which template fits, use B or C (reference-only) rather than A — a skill that's wrongly treated as actionable in Portal risks introducing dependencies/patterns that don't belong there (e.g. adding Ant Design to a Next.js app that doesn't use it).

---

## Step 8: Update SKILLS-INDEX.md

Open `docs/skills/SKILLS-INDEX.md` and add entry in correct section:

```markdown
### [[skill-your-skill-name]]
**One-line description from metadata**

- **File**: `docs/skills/<category>/skill-your-skill-name.md`
- **Time**: ~X hours/minutes
- **Tags**: `#tag1` `#tag2` `#tag3`
- **Covers**: List of what steps are included
- **Use when**: When to apply this skill
```

---

## ⚠️ Step 8b: Update SKILLS-QUICK-REFERENCE.md (CRITICAL!)

**THIS IS MANDATORY!** Add a row to `docs/skills/SKILLS-QUICK-REFERENCE.md` in the appropriate table section:

```markdown
| [[skill-your-skill-name]] | One-line description | `#tag1` `#tag2` | ~X hours |
```

**Why?** This is the fast-lookup file that gets loaded when user types "skills". Without this, the new skill won't appear in the quick reference!

**Don't forget:**
- ✅ Correct section (Backend, Frontend, or Process)
- ✅ Exact table format (pipe-separated columns)
- ✅ Keep tags consistent with SKILLS-INDEX
- ✅ Update "Last Updated" date in QUICK-REFERENCE

---

## Step 9: Update "Last Updated"

In `docs/skills/SKILLS-INDEX.md`, update:

```markdown
## Last Updated

- **Date**: 2026-07-20  ← Today's date
- **Latest Skill**: `skill-your-skill-name.md`  ← New skill name
```

---

## Step 10: Commit

```bash
git add docs/skills/<category>/skill-your-skill-name.md docs/skills/SKILLS-INDEX.md docs/skills/SKILLS-QUICK-REFERENCE.md
git commit -m "docs: add skill-your-skill-name.md to SKILLS-INDEX"
git push
```

---

## Quality Checklist

Before committing, verify:

- [ ] **Is it repeatable?** — Can someone follow it multiple times
- [ ] **Is it clear?** — Someone unfamiliar with the pattern can follow it
- [ ] **Does it have examples?** — Code snippets, not just descriptions
- [ ] **Does it have a checklist?** — Clear definition of "done"
- [ ] **Related skills linked?** — Uses wikilinks `[[skill-other]]`
- [ ] **Links [[code-best-practices]]?** — Required if the skill touches code (backend or frontend); skip only for pure process/docs skills
- [ ] **Links [[frontend-visual-consistency]]?** — Required if the skill writes or generates UI code (Backoffice or Portal); not needed for backend-only or process skills
- [ ] **Metadata complete?** — Name, time, tags, when-to-use
- [ ] **File created in the right category folder?** — `docs/skills/backend/`, `docs/skills/frontend/`, or `docs/skills/process/`
- [ ] **SKILLS-INDEX updated?** — New skill added with exact template
- [ ] **SKILLS-QUICK-REFERENCE updated?** — Row added to correct table
- [ ] **`.claude/skills/<name>/SKILL.md` pointer created at the repo root?** — Thin pointer to the vault file (Step 7b)
- [ ] **`.claude/skills/<name>/SKILL.md` mirrored to `portal/`?** — Always, using template A/B/C as appropriate (Step 7c)
- [ ] **"Last Updated" changed?** — Date is today's date in both files
- [ ] **Commit message clear?** — References SKILLS-INDEX & QUICK-REFERENCE

---

## Example: From Start to Finish

### 1. Decide
"I keep implementing JWT validation. This is repeatable → Create skill (category: backend)"

### 2. Define Metadata
```
Name: validate-jwt-tokens
Description: Implement JWT validation in Spring Boot
Time: ~1 hour
Category: backend
Tags: #backend, #auth, #security, #jwt, #spring
When to use: When adding protected endpoints
```

### 3. Write Steps
```
Step 1: Create JwtValidator class
Step 2: Add to SecurityConfig
Step 3: Test with curl
Step 4: Handle errors
```

### 4. Add to SKILLS-INDEX.md
```markdown
### [[skill-validate-jwt-tokens]]
**Implement JWT token validation in Spring Boot endpoints**

- **File**: `docs/skills/backend/skill-validate-jwt-tokens.md`
- **Time**: ~1 hour
- **Tags**: `#backend` `#auth` `#security` `#jwt` `#spring`
- **Covers**: JwtValidator class → SecurityConfig integration → Error handling
- **Use when**: Adding authorization to new endpoints
```

### 5. Add to SKILLS-QUICK-REFERENCE.md
```markdown
| [[skill-validate-jwt-tokens]] | Implement JWT token validation in Spring Boot endpoints | `#backend` `#auth` `#security` `#jwt` | ~1h |
```

### 6. Create the Root Pointer
```
.claude/skills/validate-jwt-tokens/SKILL.md   ← at repo root, thin pointer to the vault file (covers backend + Backoffice)
```

### 7. Mirror to Portal (Template B — backend-only, reference-only)
```
portal/.claude/skills/validate-jwt-tokens/SKILL.md
```
```markdown
---
name: validate-jwt-tokens
description: Reference only — JWT validation pattern for the Spring Boot backend (management/managementapi), which lives in a different repo. Portal has no backend/JWT code of its own.
---

This Portal repo has no auth code — read `C:\Users\jlalv\Desktop\utad\projetos\Property-Management\docs\skills\backend\skill-validate-jwt-tokens.md` only for background; there's nothing to implement here.
```

### 8. Commit
```bash
git add docs/skills/backend/skill-validate-jwt-tokens.md docs/skills/SKILLS-INDEX.md docs/skills/SKILLS-QUICK-REFERENCE.md .claude/skills/validate-jwt-tokens/SKILL.md
git commit -m "docs: add skill-validate-jwt-tokens to SKILLS-INDEX and QUICK-REFERENCE"
git push
# then, from inside portal/ (separate repo):
git add .claude/skills/validate-jwt-tokens/SKILL.md
git commit -m "docs: mirror skill-validate-jwt-tokens pointer (reference only)"
git push
```

---

## Final Checklist for THIS Skill

- [ ] Understood what makes a "skill" vs documentation
- [ ] Have a repeatable pattern to document
- [ ] Created `docs/skills/<category>/skill-<name>.md` with steps
- [ ] Added entry to `docs/skills/SKILLS-INDEX.md`
- [ ] Added row to `docs/skills/SKILLS-QUICK-REFERENCE.md`
- [ ] Created `.claude/skills/<name>/SKILL.md` pointer at the repo root
- [ ] Mirrored `.claude/skills/<name>/SKILL.md` to `portal/` (template A/B/C)
- [ ] Updated "Last Updated" date in BOTH index files
- [ ] Committed with proper message (mentions both INDEX files) — as **two separate commits** if Portal was touched, since it's a different repo
- [ ] Pushed to GitHub (both repos, if Portal was touched)

**CRITICAL**: When you create a new skill, update ALL FIVE files:
1. ✅ Create: `docs/skills/<category>/skill-<name>.md` — Full implementation guide (the source of truth)
2. ✅ Update: `docs/skills/SKILLS-INDEX.md` — Master index with full details
3. ✅ Update: `docs/skills/SKILLS-QUICK-REFERENCE.md` — Quick lookup table ⚠️ DO NOT SKIP!
4. ✅ Create: `.claude/skills/<name>/SKILL.md` at the repo root — Thin pointer so it's invocable as `/<name>` from backend/Backoffice ⚠️ DO NOT SKIP!
5. ✅ Create: `portal/.claude/skills/<name>/SKILL.md` — Mirrored pointer (Portal is a separate repo, doesn't inherit the root one) ⚠️ DO NOT SKIP!
6. ✅ Update: "Last Updated" date in BOTH INDEX files
7. ✅ Commit all vault-side files together; commit the Portal pointer separately from inside `portal/`

**If you forget SKILLS-QUICK-REFERENCE.md**, the new skill will exist but won't show up when user types "skills". **If you forget the root `.claude/skills/` pointer**, the skill won't be invocable as a slash command anywhere. **If you forget the Portal mirror**, the skill silently won't exist when working inside `portal/` — easy to miss since nothing errors, it just isn't there.

---

## Related Skills

- [[code-best-practices]] — Link every code-related skill here instead of duplicating rules
- [[skill-git-commits]] — Commit message format for skills
- [[skill-add-backend-feature]] — Example of a complete skill
- [[skill-frontend-design-system]] — Another example

---

## Tips

1. **Keep it simple** — One clear pattern per skill, not a book chapter
2. **Use examples** — Code > words
3. **Link related skills** — Help people navigate
4. **Update SKILLS-INDEX** — It's the only way I know about it
5. **Test the skill** — Follow it yourself to verify it works
