# 🎯 Skills Index

**Master reference for all project skills and implementation guides.**

> ⚠️ **MAINTENANCE RULE**: Every time a new skill is created:
> 1. Add entry below with name, description, and tags
> 2. Create wikilink to the skill file
> 3. Categorize under the right folder (`backend/`, `frontend/`, `process/`) and section
> 4. Update last-modified date in frontmatter
> 5. Commit with message: `docs: add skill-<name>.md to SKILLS-INDEX`
>
> A **skill** is an invocable workflow (`/name`) — a checklist you run to *do* something. A **reference** (below) is a conventions document a skill *reads* while doing its work; it's never invoked on its own and has no `.claude/skills/` entry. Don't confuse the two — see [[skill-create-new-skill]] for the distinction.

---

## 📋 Quick Navigation

- **[[docs/skills/SKILLS-QUICK-REFERENCE.md]]** ⭐ **Fast lookup table** — All skills at a glance
- **[References](#-references-not-invocable)** 📐 — Conventions every code-touching skill should read before writing
- **[Backend Skills](#backend-skills)** — API, database, auth, uploads
- **[Frontend Skills](#frontend-skills)** — React components, forms, errors
- **[Process Skills](#process-skills)** — Meta-workflow and commit conventions
- **[By Topic](#by-topic)** — Organized by concern

---

## 📐 References (not invocable)

These are **not skills** — nothing invokes `/code-best-practices` on its own. Instead, every skill below that produces code or a `.md` file is expected to **read the relevant reference first, then write its output consistent with it** (a skill that writes code applies it to the code; a skill that writes a `.md` doc applies it to that doc). No `.claude/skills/` pointer exists for these — they're only reachable through the skills that cite them. Files live in `docs/skills/references/`.

### [[code-best-practices]]
**General code quality rules shared by every skill — naming, error handling, what to test before committing**

- **File**: `docs/skills/references/code-best-practices.md`
- **Applies to**: Any skill that writes or reviews code (backend or frontend)
- **Tags**: `#quality` `#conventions` `#backend` `#frontend` `#errors`
- **Covers**: General principles → Backend conventions → Frontend conventions → Pre-commit checklist

### [[frontend-visual-consistency]]
**Router for verified visual/structural conventions in the Backoffice — points to the matching sub-file in `docs/skills/references/design/`**

- **File**: `docs/skills/references/frontend-visual-consistency.md` (router) + 5 sub-files in `docs/skills/references/design/` (tokens, cards, drawers-and-modals, tables-and-lists, buttons-and-icons)
- **Applies to**: Any skill that writes or generates UI code in the Backoffice
- **Tags**: `#frontend` `#backoffice` `#design` `#consistency`
- **Covers**: Real design tokens → component-area-specific conventions and documented drift to avoid repeating

---

## Backend Skills

Files live in `docs/skills/backend/`.

### [[skill-add-backend-feature]]
**Complete CRUD checklist for new REST endpoints**

- **File**: `docs/skills/backend/skill-add-backend-feature.md`
- **Time**: ~2-3 hours
- **Tags**: `#backend` `#api` `#java` `#spring` `#crud` `#checklist`
- **Covers**: ErrorCodes → DTOs → Repository → Mapper → Service → Controller → Tests
- **Use when**: Adding a new resource/feature to the backend

---

### [[skill-add-database-table]]
**Database table creation with SQL template and JPA entity**

- **File**: `docs/skills/backend/skill-add-database-table.md`
- **Time**: ~30 minutes + migrations
- **Tags**: `#database` `#postgresql` `#flyway` `#jpa` `#schema` `#migration`
- **Covers**: Planning → SQL template → Flyway migration → JPA entity → Repository
- **Use when**: Creating a new database table

---

### [[skill-add-file-upload]]
**File upload pattern with Supabase Storage and signed URLs**

- **File**: `docs/skills/backend/skill-add-file-upload.md`
- **Time**: ~1-2 hours
- **Tags**: `#files` `#storage` `#supabase` `#upload` `#validation` `#security`
- **Covers**: MIME validation → Upload → Signed URLs → Error handling
- **Use when**: Adding file/photo upload to a feature

---

### [[skill-permissions-and-auth]]
**Authorization and access control implementation**

- **File**: `docs/skills/backend/skill-permissions-and-auth.md`
- **Time**: ~30 minutes
- **Tags**: `#auth` `#permissions` `#security` `#rbac` `#ownership` `#validation`
- **Covers**: @PreAuthorize → Ownership checks → IDOR prevention → ErrorCodes
- **Use when**: Adding access control to endpoints

---

## Frontend Skills

Files live in `docs/skills/frontend/`.

### [[skill-frontend-design-system]]
**React component patterns, naming, and architecture**

- **File**: `docs/skills/frontend/skill-frontend-design-system.md`
- **Time**: Part of component creation
- **Tags**: `#frontend` `#react` `#components` `#patterns` `#typescript` `#tailwind` `#antd`
- **Covers**: Folder structure → Naming → Forms → Services → State → Styling
- **Use when**: Building any React component in the Backoffice

---

### [[skill-frontend-error-handling]]
**Centralized error handling, error codes, and messages**

- **File**: `docs/skills/frontend/skill-frontend-error-handling.md`
- **Time**: ~15 minutes per feature
- **Tags**: `#frontend` `#errors` `#validation` `#notifications` `#typescript`
- **Covers**: Error codes → Error messages → Centralized handler → Validation
- **Use when**: Handling API errors in components

---

### [[skill-frontend-integration-guide]]
**Generate frontend integration documentation from backend feature and auto-save to correct location**

- **File**: `docs/skills/frontend/skill-frontend-integration-guide.md`
- **Time**: ~15 minutes (1 routing question + 5 implementation questions + doc generation)
- **Tags**: `#frontend` `#integration` `#documentation` `#workflow` `#communication` `#backoffice`
- **Covers**: Feature detection → API contract → Component architecture → Code templates → Testing
- **Use when**: Documenting a backend feature for frontend implementation
- **Process**:
  1. You call the skill
  2. I ask: "Feature from this chat or existing?" → Determines data extraction method
  3. I ask 5 questions (location, UI, fields, workflow, integrations)
  4. I generate `.md` file and auto-save to correct folder

---

## Process Skills

Files live in `docs/skills/process/`. Meta-workflow and commit conventions — neither strictly backend nor frontend.

### [[skill-create-new-skill]]
**Plan, write, and add a new skill to the project**

- **File**: `docs/skills/process/skill-create-new-skill.md`
- **Time**: ~30-45 minutes
- **Tags**: `#process` `#documentation` `#workflow` `#meta`
- **Covers**: Deciding if it's a skill (vs. a reference) → Metadata → Steps → SKILLS-INDEX update → `.claude/skills/` pointer → Commit
- **Use when**: You want to document a repeatable pattern as a skill

---

### [[skill-git-commits]]
**Commit message style and conventions**

- **File**: `docs/skills/process/skill-git-commits.md`
- **Time**: ~1-2 minutes per commit
- **Tags**: `#git` `#commits` `#conventions` `#style`
- **Covers**: Message format → Types → Body → Footer → Examples
- **Use when**: Before committing code

---

## By Topic

### Code Quality (applies everywhere — reference, not a skill)
- [[code-best-practices]] — Naming, error handling conventions, pre-commit checklist

### Authentication & Security
- [[skill-permissions-and-auth]] — Authorization, access control, ownership
- [[skill-add-file-upload]] — File validation, security best practices

### Database
- [[skill-add-database-table]] — Table creation, migrations, JPA entities
- [[skill-add-backend-feature]] — Includes repository layer

### API Development
- [[skill-add-backend-feature]] — Complete REST endpoint checklist
- [[skill-add-file-upload]] — File endpoint specifics
- [[skill-permissions-and-auth]] — Securing endpoints

### Frontend Development
- [[skill-frontend-design-system]] — Components, patterns, architecture
- [[frontend-visual-consistency]] — Router to Backoffice design sub-files (tokens, drawers, tables, listings, etc.) — reference, not a skill
- [[skill-frontend-error-handling]] — Error handling, validation
- [[skill-frontend-integration-guide]] — Integrating backend changes

### Communication & Process
- [[skill-git-commits]] — Clear commit messages
- [[skill-frontend-integration-guide]] — Documenting for team
- [[skill-create-new-skill]] — How to create a new skill

---

## Usage Flow

### Adding a New Backend Feature
```
1. skill-add-backend-feature.md     ← Main checklist (reads code-best-practices.md as it goes)
   + skill-add-database-table.md    ← If new table needed
   + skill-add-file-upload.md       ← If files involved
   + skill-permissions-and-auth.md  ← If access control needed
   + skill-git-commits.md           ← Before each commit
2. Generate frontend docs
   → skill-frontend-integration-guide.md
3. Frontend team gets docs
   + skill-frontend-design-system.md   (reads frontend-visual-consistency.md, which routes to the
   + skill-frontend-error-handling.md   right docs/skills/references/design/backoffice-<area>.md sub-file)
```

### Adding a Frontend Feature
```
1. skill-frontend-design-system.md   ← Component patterns (reads code-best-practices.md and
   + skill-frontend-error-handling.md ← Error handling      frontend-visual-consistency.md, which
                                          routes to the matching design sub-file as it writes the component)
2. Integrate with backend docs
   + skill-frontend-integration-guide.md result
3. Commit
   → skill-git-commits.md
```

---

## Folder Layout

```
docs/skills/
├── SKILLS-INDEX.md            ← This file
├── SKILLS-QUICK-REFERENCE.md  ← Fast lookup table
├── backend/                   ← 4 skills
├── frontend/                  ← 3 skills
├── process/                   ← 2 skills
└── references/                ← 2 docs + design/ (5 sub-files), not invocable
```

## Statistics

| Category | Count |
|----------|-------|
| References (not invocable) | 2 |
| Backend Skills | 4 |
| Frontend Skills | 3 |
| Process Skills | 2 |
| **Total Invocable Skills** | **9** |

---

## Last Updated

- **Date**: 2026-07-26
- **Latest**: Bootstrapped from the Property-Management project's skills — dropped the Portal-specific `design/` sub-files and routing questions since Worksite has no public portal.
- **Next to Add**: As needed (update this index when adding)

---

## Navigation Tips

- **Find by topic**: Use "By Topic" section above
- **Find by time**: Sort by "Time" estimate
- **Find by tags**: Search for `#tag` in this file
- **Cross-references**: Follow wikilinks to related skills
- **Full checklist**: Each skill file has its own detailed checklist

---

## See Also

- [[docs/skills/SKILLS-QUICK-REFERENCE.md]] — ⭐ Fast lookup (use when you type "skills")
- [[00-INDEX.md]] — Main Obsidian entry point
- [[CLAUDE.md]] — Main project documentation
- [[management/managementapi/CLAUDE.md]] — Backend-specific docs
- [[management/managementfrontend/CLAUDE.md]] — Frontend-specific docs

---

## Adding New Skills (or References)

**When creating a new skill file:**

1. First decide: is this an invocable workflow (**skill**) or a conventions doc other skills consult (**reference**)? See the callout at the top of this file.
2. Create file: `docs/skills/<category>/skill-<name>.md` (skill — `<category>` is `backend`, `frontend`, or `process`) or `docs/skills/references/<name>.md` without the `skill-` prefix (reference)
3. Follow the skill template (see any existing skill)
4. Include: Time estimate, tags, what it covers, when to use
5. Add entry to this index under the appropriate section — **References** section if it's a reference, not a numbered skill category
6. Add tags for easy filtering
7. Commit with message referencing this index
8. Update "Last Updated" date above
9. **Skills only**: create `.claude/skills/<name>/SKILL.md` at the repo root — see [[skill-create-new-skill]]. **References never get a `.claude/skills/` entry** — they're not invocable, only cited by skills.

**Template for a new skill entry:**
```markdown
### [[skill-<name>]]
**One-line description**

- **File**: `docs/skills/<category>/skill-<name>.md`
- **Time**: ~X hours/minutes
- **Tags**: `#tag1` `#tag2` `#tag3`
- **Covers**: List of topics/steps
- **Use when**: When to apply this skill
```

**Template for a new reference entry:**
```markdown
### [[<name>]]
**One-line description**

- **File**: `docs/skills/references/<name>.md`
- **Applies to**: Which skills should read this while doing their work
- **Tags**: `#tag1` `#tag2` `#tag3`
- **Covers**: List of topics/steps
```

---

**This index is the single source of truth for all skills (and references) in the project.**
Keep it updated as new ones are created.
