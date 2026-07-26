# Skill: Write Good Commit Messages

**When to use**: Before you commit code

**Time**: 1-2 minutes per commit

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.

> 🔔 A `pre-commit` hook (in the main repo and in `portal/`) prints a **non-blocking** reminder when the staged diff touches files that usually need a matching vault doc update. It never fails the commit, only warns — see [[vault-sync-hooks]] for exactly what it checks and how to set it up on a new clone.

---

## Format

```
<type>: <subject>

<body>

<footer>
```

---

## Type

Use one of:
- `feat:` — New feature
- `fix:` — Bug fix
- `refactor:` — Code reorganization (no behavior change)
- `docs:` — Documentation changes
- `chore:` — Build, dependencies, configuration
- `test:` — Test changes
- `style:` — Formatting (no logic change)

---

## Subject

- Imperative mood: "add", "fix", "update" (not "added", "adds")
- Lower case
- No period at end
- Max 50 characters

**Good:**
```
feat: add lead management endpoints
fix: prevent IDOR in asset edit
docs: update deployment guide
```

**Bad:**
```
Fixed bug
Added new feature to the system
Updated the code for things
```

---

## Body

- Optional but recommended for non-trivial changes
- Explain **WHY**, not **WHAT** (the code shows what)
- Wrap at 72 characters
- Separate from subject with blank line

**Example:**
```
feat: add soft-delete to assets

Allows admins to recover accidentally deleted assets
instead of permanently removing them from database.
Implements with deleted_at timestamp pattern used
throughout the codebase.
```

---

## Footer

Reference issue numbers or breaking changes:

```
feat: add lead management

Closes #42
Closes #51
```

---

## Examples

### Feature with body
```
feat: add lead management endpoints

Implement CRUD endpoints for leads:
- GET /leads (list, paginated, searchable)
- POST /leads (create new lead)
- PUT /leads/{id} (update)
- DELETE /leads/{id} (admin only)

Includes proper error handling and activity logging.

Closes #123
```

### Bug fix
```
fix: prevent IDOR in asset edit

Validate user is asset creator before allowing PUT.
Only ADMIN can edit other users' assets.

Closes #45
```

### Refactoring
```
refactor: extract permission check to SecurityUtils

Move repeated authorization logic to reusable methods:
- validateAssetEditAccess()
- validateAssetDeleteAccess()
- validateUserIsAdmin()

No behavior change, improves maintainability.
```

### Documentation
```
docs: add file upload guide

Document the pattern for uploading files to
Supabase Storage:
- Validation (MIME type, size)
- Naming convention
- Signed URL generation
- Common mistakes

Closes #67
```

---

## Rules

✅ **DO:**
- Use imperative mood ("add", "fix", "update")
- Capitalize subject line
- Write for someone who hasn't seen the code
- Explain **why** the change was needed
- Reference related issues

❌ **DON'T:**
- Use past tense ("added", "fixed")
- End subject with period
- Write all in lowercase
- Commit multiple unrelated changes
- Leave out meaningful messages ("wip", "fix", "update")

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[skill-add-backend-feature]] — You commit these
- [[skill-add-database-table]] — You commit migrations
