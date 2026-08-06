# Skill: Frontend Structure Brief

**When to use**: You want to discuss a redesign / UI change for a page, component, or feature in a *separate* conversation (e.g. a design-focused chat) and need an accurate, self-contained snapshot of what the current frontend actually does — without pasting raw source files or giving that conversation repo access.

**Output**: A single `.md` file, saved locally, ready to copy-paste elsewhere.

**Time**: ~15-20 minutes

> 📐 See also [[code-best-practices]] for naming conventions used when describing the code, and [[frontend-visual-consistency]] — it routes to the real design tokens/patterns so the brief describes actual current styling instead of inventing terminology.

---

## Step 1: Identify the Subject

Two ways this skill gets invoked:

- **A frontend file is referenced/attached** (a component, page, hook, or similar under `management/managementfrontend/apps/backoffice/src/`) → that file is the primary subject. If it's part of a larger domain (e.g. one card inside `components/property/`), ask whether the brief should cover just that file or the whole domain folder (create/edit/view/list) — don't assume scope.
- **No file was given** → do not guess. Ask directly:

```
What do you want to document for this brief?
- A specific page or component (give me the name or path)
- A whole feature/domain (e.g. "the Properties list + drawer")
- A general area of the UI you want feedback on (describe it)
```

Wait for the answer before proceeding — this skill never fabricates a subject.

---

## Step 2: Confirm the stack

There's only one frontend app in this project, so there's no app to disambiguate — everything lives under `management/managementfrontend/apps/backoffice/`:

| App | Stack |
|---|---|
| Backoffice | React 18, Vite 7, TypeScript 5.8, Tailwind CSS 4, Ant Design 5, React Hook Form + Zod |

Note: there is **no Zustand store** in this project (unlike the Property-Management Backoffice it was copied from) — state is local (`useState`) plus React Context (`AuthContext`, `ConfirmDialogContext`). Don't describe a global store that doesn't exist.

---

## Step 3: Gather the Structural Facts

Read the target file(s) in full — don't work from filename/memory. Extract, in this order:

1. **Component tree** — what it renders and imports (parent/child components, one level of relevant nesting is usually enough).
2. **Props in** — the component's own props interface.
3. **State** — local (`useState`/`useReducer`), ou partilhado via React Context (`AuthContext`, `ConfirmDialogContext`) — não existe store Zustand neste projeto, ver [[skill-frontend-design-system]].
4. **Data flow** — which service function / API endpoint it calls, and the shape of the data it consumes (key field names only, not the full DTO).
5. **Routing** — the URL/route this is mounted at, if applicable.
6. **Conditional behavior** — loading/error states, role-based visibility (who sees what), anything hidden or disabled based on data.
7. **Validation constraints** — if it's a form, which fields are required (mirrors backend `@NotBlank`/`@NotNull` — see [[skill-frontend-design-system]] → "Forms"), since a redesign can't silently drop a required field.
8. **Styling** — which Ant Design components and Tailwind classes are used; cross-check against [[frontend-visual-consistency]]'s routed sub-file so you describe the *actual* current tokens (spacing, drawer width, colors) rather than approximating them.

If the subject is a whole domain rather than one file, walk the folder structure from [[skill-frontend-design-system]] (`create/`, `edit/`, `view/`, list page, detail drawer) and summarize each piece instead of dumping every file.

**Describe only what's in the code.** Don't infer intended behavior or fill gaps with assumptions — if something is unclear from the code alone, list it under "Open Questions" (Step 4) instead of guessing.

---

## Step 4: Write the Brief

Structure the `.md` file so someone with **zero repo access** can understand the current implementation and start discussing changes:

```markdown
# Frontend Structure Brief: <Subject>

**App**: Backoffice
**Location**: `<file path(s)>`
**Route**: `<url, if applicable>`

## Component Tree
<parent → children, one level deep, plain text or a small fenced tree>

## Data & State
- Props in: ...
- Local state: ...
- Shared state: ... (React Context — `AuthContext`/`ConfirmDialogContext` — ou "none, só estado local")
- API call(s): `<method> <endpoint>` via `<serviceFunction>` — key fields: ...

## Current Behavior
<Plain-language walkthrough of what renders, in what order, and what's conditional
 — loading/error states, role-based visibility, empty states>

## Styling
<Ant Design components used / Tailwind patterns used, referencing the real tokens
 from frontend-visual-consistency's routed sub-file — not invented values>

## Constraints to Respect in Any Redesign
- Required fields (from Zod/backend): ...
- Role-based restrictions: ...
- Anything else that isn't just visual (e.g. a field that must stay editable inline)

## Open Questions
- <Anything ambiguous in the current code that's worth clarifying before redesigning>
- <What specifically the user wants feedback on / wants to change — leave blank
  slots here if not yet discussed, so the design conversation fills them in>
```

Keep it factual and compact — this is a briefing document, not a full spec. Prefer plain language over code dumps; only include a code snippet if the exact shape of something (e.g. a props interface) is genuinely load-bearing for the discussion.

---

## Step 5: Save the File

Save to `notes/design-briefs/` at the vault root — this is a personal working artifact meant to be pasted into another conversation and then discarded, not permanent project documentation, so it belongs in the git-ignored `notes/` folder (see `notes/README.md`) rather than `docs/`.

Filename: `notes/design-briefs/<YYYY-MM-DD>-<kebab-case-subject>.md`

Example: `notes/design-briefs/2026-07-23-property-view-drawer.md`

Create the `notes/design-briefs/` folder if it doesn't exist yet. No commit step — `notes/` is git-ignored, nothing to push.

After saving, tell the user the file path and that it's ready to copy-paste into the other conversation.

---

## Final Checklist

- [ ] Subject identified — from a referenced file, or asked explicitly if none was given
- [ ] Read the actual target file(s) — didn't work from memory/filename
- [ ] Component tree, props, state, data flow, routing, conditionals, validation, styling all covered
- [ ] Styling terminology cross-checked against [[frontend-visual-consistency]]'s routed sub-file
- [ ] Nothing invented — unclear behavior listed under Open Questions instead of guessed
- [ ] Saved to `notes/design-briefs/<date>-<subject>.md`
- [ ] Told the user the path

---

## Related Skills

- [[code-best-practices]] — General naming conventions used when describing the code
- [[frontend-visual-consistency]] — Router to real Backoffice design tokens/patterns so the brief doesn't invent styling terminology
- [[skill-frontend-design-system]] — Folder structure and patterns this skill reads to know what to look for
- [[skill-frontend-integration-guide]] — Similar "generate and save a `.md`" shape, but for handing a *new* backend feature to frontend rather than briefing an *existing* frontend piece for a design discussion
