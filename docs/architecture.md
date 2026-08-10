# 🏗️ Arquitetura do Sistema

Visão geral de como o backend e o frontend se encaixam. Bootstrapped a partir do [Property-Management](https://github.com/bino03/Property-Management) — ver [[../CLAUDE.md#-provenance--o-que-veio-do-property-management]] para o que foi mantido vs. deixado de fora.

## Os 2 Projetos

| Projeto | Repo | Stack | Porta (dev) |
|---|---|---|---|
| **Backend API** | `management/managementapi` | Spring Boot 3.5.6, Java 21 | `8080` |
| **Backoffice** | `management/managementfrontend/apps/backoffice` | React 18 + Vite 7 + TS 5.8 | `5173` |

Ao contrário do Property-Management, **não existe portal público** — esta é uma ferramenta interna, um único repositório, sem submódulos git.

## Diagrama de comunicação

```
┌────────────────────────┐
│   Backoffice              │
│   React + Vite            │
│   localhost:5173          │
│   (única aplicação)       │
└──────────┬──────────────────┘
           │
           │        HTTP/REST (JSON)
           │
┌──────────▼──────────────────┐
│   Management API              │
│   Spring Boot 3.5.6              │
│   localhost:8080                  │
└──────────┬──────────────────────────┘
           │
┌──────────▼──────────────────┐
│  PostgreSQL                     │
│  (via Flyway)                    │
│  schemas: worksite, settings, tasks      │
│  + Supabase Auth (JWT) + Storage    │
└─────────────────────────────────────┘
```

O backend é a única fonte de verdade e o único componente com acesso direto à base de dados.

## Backend — `managementapi`

- **Spring Boot 3.5.6**, Java 21.
- Persistência: **Spring Data JPA** + **PostgreSQL**, migrações geridas por **Flyway** (`src/main/resources/db/migration/`, `V1` a `V18`). Ver [[database.md]] para o schema completo.
- Mapeamento DTO ↔ entidade via **MapStruct 1.6.0**.
- Autenticação: **Spring OAuth2 Resource Server** a validar JWTs emitidos pelo **Supabase** (HS256, chave partilhada). Ver [[security.md]].
- Não usa um SDK oficial do Supabase — a integração é feita por chamadas REST próprias via **OkHttp**.
- API organizada em: auth/perfil (`/auth/**`, `/profile/**`), funcionários (`/employees/**`), projetos (`/enterprises/**`, `/enterprise-relations/**`), orçamento de obra (`/construction-budget/**`, `/construction-expenses/**`), tarefas (`/tasks/**`), localizações (`/locations/**`), atividade (`/activities/**`). Todas as rotas requerem JWT válido (e em muitos casos role `ADMIN`/`EMPLOYEE`), exceto `/auth/login|refresh|logout|accept-invite`.

## Backoffice — `management/managementfrontend/apps/backoffice`

- Dashboard administrativo (role `ADMIN`/`EMPLOYEE`), React 18 + Vite 7 + TypeScript 5.8 (strict), Ant Design 5, Tailwind CSS 4.
- Consome a API via Axios (`src/api.ts`), com JWT Supabase em cookies HttpOnly (refresh automático em 401).
- Padrão de UI: operações de criar/ver/editar feitas em `Drawer`s do Ant Design em vez de páginas dedicadas; formulários com React Hook Form + Zod.
- Detalhe completo em [[../management/managementfrontend/apps/backoffice/CLAUDE.md]].

## Autenticação entre projetos

Ver [[security.md]] para o fluxo completo. Resumo: o Supabase emite o JWT; o Backoffice guarda-o em cookie HttpOnly e injeta-o nos pedidos; o backend valida-o localmente (HS256, sem chamar o Supabase a cada pedido).

## Relacionado

- [[database.md]] — Schema da base de dados
- [[security.md]] — Autenticação e autorização
- [[../CLAUDE.md]] — Hub principal do projeto
