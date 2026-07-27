# CLAUDE.md - Worksite Central Hub

**Welcome!** This is the central hub for the **Worksite** project — a project & staff management app bootstrapped from [Property-Management](https://github.com/bino03/Property-Management)'s auth/enterprises/construction-management foundations.

> **Obsidian Vault:** This entire folder is configured as an Obsidian vault for easy navigation and search.
> Start here: [[00-INDEX|00-INDEX.md]] or use **Ctrl+P** to search.

---

## 📍 Quick Navigation

| I want to... | Go to |
|-------------|--------|
| **Work on Backend** | [[management/managementapi/CLAUDE.md]] |
| **Work on Backoffice (frontend)** | [[management/managementfrontend/CLAUDE.md]] |
| **Read Documentation** | [[docs/README.md]] |
| **Add Notes/Ideas** | [[notes/README.md]] |
| **View Architecture** | [[docs/architecture.md]] |

---

## 🏗️ The 2 Applications

```
Worksite/                     ← You are here (Obsidian vault)
└── management/               ← Single repo (Backend + Backoffice)
    ├── managementapi/        ← Backend (Spring Boot)
    └── managementfrontend/   ← Frontend (React)
        └── apps/
            └── backoffice/   ← Admin app (projects, construction, staff)
```

| App | Repo path | Tech Stack |
|-----|-----|-----------|
| **Backend API** | `management/managementapi` | Spring Boot 3.5, Java 21 |
| **Backoffice** | `management/managementfrontend/apps/backoffice` | React 18, Vite, TypeScript |

Unlike Property-Management, there is **no public portal** — this is an internal-only tool, single repo, no git submodules.

---

## 📁 Folder Structure

```
Worksite/
├── .obsidian/               ← Obsidian config
├── .claude/skills/          ← Invocable skills (carried over from Property-Management)
│
├── management/
│   ├── managementapi/       ← Spring Boot backend
│   ├── managementfrontend/  ← React frontend (apps/backoffice)
│   └── CLAUDE.md
│
├── docs/                    ← Centralized documentation
│   ├── README.md
│   ├── architecture.md / database.md / security.md
│   └── skills/              ← Mirrors .claude/skills content + design references
│
├── notes/                   ← Personal notes (NOT versioned — git ignored)
│
└── CLAUDE.md                ← This file
```

---

## 🚀 Quick Start

```bash
# Backend
cd management/managementapi
# Create .env from .env.example with your own Supabase project's credentials
./mvnw spring-boot:run

# Frontend (separate terminal)
cd management/managementfrontend/apps/backoffice
npm install
npm run dev
# Opens: Backoffice on 5173
```

> You need your **own Supabase project** (Auth + Storage + Postgres) — do not reuse Property-Management's. See `management/managementapi/.env.example` for the required variables.

---

## 📖 Documentation

- **Architecture** → [[docs/architecture.md]]
- **Database** → [[docs/database.md]]
- **Security** → [[docs/security.md]]
- **Backend guide** → [[management/managementapi/CLAUDE.md]]
- **Frontend guide** → [[management/managementfrontend/CLAUDE.md]]

---

## 🧬 Provenance — what came from Property-Management

This project started as a scoped copy of the [Property-Management](https://github.com/bino03/Property-Management) monorepo, keeping only:

- **Auth/accounts**: Supabase JWT auth, `worksite.profile` (staff/users, roles `ADMIN`/`EMPLOYEE`), admin invite flow.
- **Enterprises** (renamed conceptually to "projects" — the `enterprises` table/package names were kept as-is to minimize risk).
- **Construction management**: `construction_stage` → `construction_sub_stage` → `construction_expense`, including invoice upload.
- **Employees**: CRUD over `worksite.profile` (no separate entity).
- **Tasks**: standalone tasks assignable to one or more `worksite.profile` users, isolated in their own `tasks` schema (no link to any asset/property — that concept doesn't exist here).

Deliberately **not** carried over: property listings (`property_asset`, `buildings`, agency/characteristics/contacts/licenses), leads, banners, payments, the public portal, notifications/SSE — none of these were part of the original ask; they're candidates for future features, not gaps.

---

## 🧠 Using Obsidian

This vault provides:
- **Search**: **Ctrl+Shift+F** — Search all docs
- **Graph**: **Ctrl+G** — Visualize connections
- **Quick Open**: **Ctrl+P** — Find any file
- **Backlinks**: **Ctrl+Shift+I** — See what references this file

---

## 📞 Need Help?

See [[00-INDEX.md]] for the full index, or use Obsidian search (Ctrl+Shift+F).
