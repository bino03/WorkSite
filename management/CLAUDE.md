# CLAUDE.md - Management (Project Root)

This file provides guidance to Claude Code when working with the **management** folder, which contains the backend API and frontend application.

---

## 📚 Projects in This Folder

```
management/
├── managementapi/              ← Backend (Spring Boot)
│   └── CLAUDE.md               ← See this for backend work
├── managementfrontend/         ← Frontend (React)
│   ├── CLAUDE.md               ← See this for frontend setup & patterns
│   └── apps/
│       └── backoffice/         ← Admin dashboard (only app here)
│           └── CLAUDE.md       ← See this for backoffice-specific work
```

No portal, no submodules — this is a single-repo internal tool.

---

## 🎯 Quick Navigation

| Task | Refer to |
|------|----------|
| **Backend API, database, auth, migrations** | [`managementapi/CLAUDE.md`](./managementapi/CLAUDE.md) |
| **Frontend setup, shared patterns** | [`managementfrontend/CLAUDE.md`](./managementfrontend/CLAUDE.md) |
| **Backoffice admin dashboard features** | [`managementfrontend/apps/backoffice/CLAUDE.md`](./managementfrontend/apps/backoffice/CLAUDE.md) |
| **Project-wide setup & architecture** | [`../CLAUDE.md`](../CLAUDE.md) (root) |

---

## 🚀 Quick Start

```bash
cd Worksite/management

# Backend
cd managementapi
# Create .env from .env.example (see managementapi/CLAUDE.md)
./mvnw spring-boot:run

# Frontend (new terminal)
cd managementfrontend/apps/backoffice
npm install
npm run dev
```

This starts:
- ✅ Backend at `http://localhost:8080`
- ✅ Backoffice at `http://localhost:5173`

---

## 📂 File Structure

```
management/
├── managementapi/                      ← Spring Boot backend
│   ├── pom.xml
│   ├── src/
│   │   ├── main/java/com/management/managementapi/
│   │   │   ├── controller/             ← REST endpoints (auth, profile, employees, locations, activities)
│   │   │   ├── service/                ← Business logic
│   │   │   ├── repository/             ← JPA repositories
│   │   │   ├── model/                  ← JPA entities
│   │   │   ├── dto/                    ← Request/response objects (dto/common/* is shared)
│   │   │   ├── mapper/                 ← MapStruct mappers
│   │   │   ├── security/               ← JWT, filters, auth
│   │   │   ├── config/                 ← Spring configs
│   │   │   ├── enterprises/            ← Enterprise (project) & construction module
│   │   │   ├── exeption/               ← Custom exceptions
│   │   │   ├── infra/                  ← Logging & diagnostics
│   │   │   └── util/                   ← General utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application.properties
│   │       └── db/migration/           ← Flyway migrations (V1-V18)
│   └── CLAUDE.md                       ← Backend guidance
│
├── managementfrontend/                 ← React frontend
│   ├── CLAUDE.md                       ← Frontend guidance
│   └── apps/
│       └── backoffice/                 ← Admin app (only one here)
│           ├── package.json
│           ├── vite.config.ts
│           ├── src/
│           │   ├── main.tsx            ← Routes
│           │   ├── api.ts              ← Axios instance
│           │   ├── pages/              ← Page components
│           │   ├── components/         ← Reusable components
│           │   ├── services/           ← API service layer
│           │   ├── types/              ← TypeScript interfaces
│           │   ├── errors/             ← Error handling
│           │   ├── hooks/              ← Custom hooks
│           │   ├── config/             ← Configuration
│           │   └── utils/              ← Utilities
│           ├── .env                    ← Environment variables
│           └── CLAUDE.md               ← Backoffice guidance
```

---

## 🔄 How Projects Communicate

```
┌─────────────────┐
│  Backoffice     │
│  (React 5173)   │
└────────┬────────┘
         │
         │ HTTP/REST
         │
┌────────▼────────┐         ┌──────────────────┐
│ Management API  │◄───────►│   Supabase       │
│ (Spring 8080)   │         │   (Auth + Storage)
└─────────────────┘         └──────────────────┘
```

**Backend** handles: authentication (JWT via Supabase), database (PostgreSQL), business logic, file storage (Supabase).
**Backoffice** handles: all admin CRUD (projects, construction, staff).

---

## 📝 Commands Reference

### Backend (Java/Maven)

```bash
cd managementapi

./mvnw spring-boot:run          # Run
./mvnw clean install            # Build
./mvnw test                     # Run all tests
./mvnw package -DskipTests      # Package JAR
```

### Frontend (Node/npm)

```bash
cd managementfrontend/apps/backoffice

npm install       # Install dependencies
npm run dev       # Dev server
npm run build     # Build
npm run lint      # Lint
```

---

## 🔐 Environment Variables

### Backend (`.env` in `managementapi/`)

```
DB_URL=jdbc:postgresql://<pooler-host>:5432/postgres?sslmode=require&preferQueryMode=simple&prepareThreshold=0
DB_USER=postgres.<project-ref>
DB_PASS=your_password
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_key
SUPABASE_ANON_KEY=your_anon_key
SUPABASE_JWT_SECRET=your_jwt_secret
```

**See:** [`managementapi/.env.example`](./managementapi/.env.example) for the full template.

### Frontend (`.env` in `managementfrontend/apps/backoffice/`)

```
VITE_API_URL=http://localhost:8080
VITE_GOOGLE_MAPS_API_KEY=          # optional, only needed for the map location picker
```

---

## 🛠 Technology Stack

| Layer | Tech |
|-------|------|
| **Backend** | Spring Boot 3.5, Java 21, PostgreSQL, Supabase, Flyway, MapStruct |
| **Frontend (Backoffice)** | React 18, Vite 7, TypeScript 5.8, Tailwind CSS 4, Ant Design 5 |
| **Auth** | Supabase JWT (HS256) |
| **API Communication** | REST (JSON), Axios, HttpOnly Cookies |
| **Forms** | React Hook Form + Zod |

---

## 📖 Documentation Hierarchy

1. **This file** (`CLAUDE.md`) — Overview of the management folder
2. [`managementapi/CLAUDE.md`](./managementapi/CLAUDE.md) — Backend details
3. [`managementfrontend/CLAUDE.md`](./managementfrontend/CLAUDE.md) — Frontend setup & patterns
4. [`managementfrontend/apps/backoffice/CLAUDE.md`](./managementfrontend/apps/backoffice/CLAUDE.md) — Backoffice specifics
5. [`../CLAUDE.md`](../CLAUDE.md) — Project root (parent directory)
