# 📚 Worksite - Knowledge Base Index

**Welcome to the Worksite documentation hub!**

This is your starting point. Use the links below or **Ctrl+Shift+F** to search everything.

---

## ⭐ SKILLS INDEX

**New way to navigate implementation guides:**

👉 **Go to [[docs/skills/SKILLS-INDEX.md]]** ← Master reference for all skills

- 11 skills organized by topic (backend, frontend, process)
- Quick tags for filtering (`#backend`, `#frontend`, `#auth`, etc.)
- Time estimates for each skill

👉 **Para avançar o backlog**: [[docs/skills/process/skill-implement-todo]] (`/implement-todo`) — lê [[notes/ToDo.md]], prioriza e implementa

---

## 🎯 Quick Actions

| Action | Shortcut |
|--------|----------|
| **View all skills** | [[docs/skills/SKILLS-INDEX.md]] |
| **Search everywhere** | **Ctrl+Shift+F** |
| **Find file** | **Ctrl+P** |
| **View graph** | **Ctrl+G** |
| **See backlinks** | **Ctrl+Shift+I** |

---

## 📍 The 2 Projects

### 🔧 Backend API
- **File**: [[management/managementapi/CLAUDE.md]]
- **What**: Spring Boot REST API, Database, Authentication
- **Tech**: Java 21, Spring Boot 3.5, PostgreSQL
- **Start**: `cd management/managementapi && ./mvnw spring-boot:run`

### 🎨 Backoffice (Admin Dashboard)
- **File**: [[management/managementfrontend/apps/backoffice/CLAUDE.md]]
- **What**: Admin dashboard — projects, construction management, staff
- **Tech**: React 18, Vite, TypeScript, Tailwind, Ant Design
- **Port**: `http://localhost:5173`

---

## 📖 Documentation

### 🏗️ Architecture & Design
- **Onde está o código disto?** → [[docs/code-map.md]] ← mapa domínio → ficheiros, e "onde procurar por sintoma"
- **System Architecture** → [[docs/architecture.md]]
- **Database Schema** → [[docs/database.md]]
- **Security & Auth** → [[docs/security.md]]
- **API Reference** → [[docs/api.md]]
- **Vault sync hooks** → [[docs/vault-sync-hooks.md]]

### 🎯 Working on Specific Tasks
- **Add Backend Feature** → [[docs/skills/backend/skill-add-backend-feature]]
- **Add Frontend Feature** → [[docs/skills/frontend/skill-frontend-design-system]]
- **Database Migrations** → [[docs/skills/backend/skill-add-database-table]] + [[docs/database.md]]

---

## 📝 My Notes

Personal, git-ignored. O ciclo é `ideas → ToDo → plans → whatIveDone` — ver [[notes/README.md]].

- **Notes index** → [[notes/README.md]]
- **Backlog acionável** → [[notes/ToDo.md]] ⭐ (o que `/implement-todo` consome)
- **Ideias em bruto** → [[notes/ideas.md]]
- **Roadmap & planos** → [[notes/roadmap/README.md]]
- **Work log** → [[notes/whatIveDone.md]]
- **Bugs** → [[notes/bugs.md]] · **Refactoring** → [[notes/refactoring.md]] · **Learnings** → [[notes/learning.md]]

---

## 🧠 Using This Vault (Obsidian Tips)

### Wikilinks
Instead of regular markdown links, this vault uses **wikilinks**:
```markdown
[[file-name]]           ← Links to file
[[file-name|Label]]     ← Custom label
```

### Tags
Add tags to notes for filtering:
```markdown
#todo #bug #idea #inprogress
```

---

## 📋 Setup Checklist

First time here? Complete these steps:

- [ ] Read [[CLAUDE.md]] (main overview)
- [ ] Enable the vault-sync reminder hook: `git config core.hooksPath .githooks` (ver [[docs/vault-sync-hooks.md]])
- [ ] Create a **new Supabase project** (do not reuse Property-Management's)
- [ ] Create `management/managementapi/.env` from `.env.example`
- [ ] Create `management/managementfrontend/apps/backoffice/.env` (see [[management/managementfrontend/apps/backoffice/CLAUDE.md]])
- [ ] Start backend: `./mvnw spring-boot:run`
- [ ] Start frontend: `npm run dev`
- [ ] Open Obsidian: Point to `Worksite/` folder

---

## 📚 All Sections

### Root Level
- [[CLAUDE.md]] — Main overview (you should start here)
- [[management/CLAUDE.md]] — Backend + Backoffice overview

### Documentation (Centralized)
- [[docs/README.md]] — Docs index
- [[docs/architecture.md]] — System design
- [[docs/database.md]] — Database info
- [[docs/security.md]] — Security notes
- [[docs/api.md]] — API endpoint reference
- [[docs/vault-sync-hooks.md]] — Pre-commit reminder hook (`.githooks/pre-commit`)
- [[docs/skills/SKILLS-INDEX.md]] — All skills & references (backend/, frontend/, process/, references/)
- [[docs/skills/references/code-best-practices.md]] — Code conventions (reference)
- [[docs/skills/references/frontend-visual-consistency.md]] — Visual conventions router → `docs/skills/references/design/` (8 sub-files)

### References (External)
- [[references/useful-links.md]] — Links de documentação da stack e serviços

### Notes (Personal — not versioned)
- [[notes/README.md]] — Notes index (ToDo, ideas, roadmap/, bugs, refactoring, learning, whatIveDone, design-briefs/)

### Project-Specific
- [[management/managementapi/CLAUDE.md]] — Backend guide
- [[management/managementfrontend/CLAUDE.md]] — Frontend guide
- [[management/managementfrontend/apps/backoffice/CLAUDE.md]] — Backoffice app

---

## ❓ How Do I...?

| Task | Where |
|------|-------|
| Work on backend | [[management/managementapi/CLAUDE.md]] |
| Add a feature | See respective project CLAUDE.md |
| Understand architecture | [[docs/architecture.md]] |
| See database schema | [[docs/database.md]] |
| Search everything | **Ctrl+Shift+F** in Obsidian |
| Find a file | **Ctrl+P** in Obsidian |
| See connections | **Ctrl+G** (Graph View) |

---

## 🚀 Quick Start Commands

```bash
# Backend
cd management/managementapi
./mvnw spring-boot:run

# Frontend (separate terminal)
cd management/managementfrontend/apps/backoffice
npm install && npm run dev

# Open in Obsidian
# File → Open Vault → Select Worksite/
```

---

**Happy coding! Use **Ctrl+Shift+F** to search or **Ctrl+P** to find files.** 🚀
