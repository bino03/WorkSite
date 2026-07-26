# CLAUDE.md - Management Frontend

This file provides guidance to Claude Code when working with the frontend.

> 📦 Related documentation:
> - **Project root:** [`../../CLAUDE.md`](../../CLAUDE.md)
> - **Management folder:** [`../CLAUDE.md`](../CLAUDE.md)
> - **Backend API:** [`../managementapi/CLAUDE.md`](../managementapi/CLAUDE.md)
> - **Backoffice app:** [`./apps/backoffice/CLAUDE.md`](./apps/backoffice/CLAUDE.md)

---

## 📦 What's Here

Unlike the original Property-Management this was bootstrapped from, there is **no monorepo root config and no Portal app** — `apps/backoffice/` is the only application, and it's a self-contained Vite project (its own `package.json`, `tsconfig.json`, etc.). The `apps/` nesting was kept anyway so the copied skill docs' file-path references stay valid.

```
managementfrontend/
├── apps/
│   └── backoffice/               ← Admin dashboard (localhost:5173)
│       └── CLAUDE.md
└── CLAUDE.md                     ← This file
```

---

## 🚀 Quick Start

```bash
cd apps/backoffice
npm install
npm run dev       # http://localhost:5173
```

---

## 🔧 Stack

- **React 18** + **React Router v6** — UI framework & routing
- **Vite 7** — Build tool
- **TypeScript 5.8** (strict mode) — Type safety
- **Tailwind CSS 4** — Utility-first styling
- **Ant Design 5** — Component library
- **Axios** — HTTP client
- **React Hook Form + Zod** — Forms & validation
- **dayjs** — Date handling
- **Lucide React** / **Ant Design Icons** — Icons

---

## 📡 Backend Communication

```
┌─────────────────┐
│  Backoffice     │
│  (localhost:    │
│   5173)         │
└────────┬────────┘
         │
    HTTP/REST (JSON)
         │
┌────────▼───────────────┐
│  Management API         │
│  (localhost:8080)       │
└─────────────────────────┘
```

Environment variables (`.env` in `apps/backoffice/`):
```
VITE_API_URL=http://localhost:8080
VITE_GOOGLE_MAPS_API_KEY=   # optional — only needed for the map location picker
```

---

## 🔐 Authentication

**Supabase JWT** via the backend, stored in HttpOnly cookies (not localStorage/client-side Supabase SDK — the frontend never talks to Supabase directly). Axios (`src/api.ts`) auto-refreshes on 401 and redirects to `/login` on refresh failure. See `src/context/AuthContext.tsx` and `src/services/authService.ts`.

---

## See Also

- [`apps/backoffice/CLAUDE.md`](./apps/backoffice/CLAUDE.md) — Backoffice specifics
- [`../managementapi/CLAUDE.md`](../managementapi/CLAUDE.md) — Backend integration
- [[../../docs/skills/frontend/skill-frontend-design-system]] — Component patterns
