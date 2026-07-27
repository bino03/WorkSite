# CLAUDE.md - Management API (Backend)

This file provides guidance to Claude Code when working with the **backend**.

> 📦 Related documentation:
> - **Project root:** [`../../CLAUDE.md`](../../CLAUDE.md)
> - **Management folder:** [`../CLAUDE.md`](../CLAUDE.md)
> - **Frontend (general):** [`../managementfrontend/CLAUDE.md`](../managementfrontend/CLAUDE.md)
> - **Backoffice app:** [`../managementfrontend/apps/backoffice/CLAUDE.md`](../managementfrontend/apps/backoffice/CLAUDE.md)

## Commands

```bash
./mvnw spring-boot:run                          # Run
./mvnw clean install                             # Build
./mvnw test                                      # Run all tests
./mvnw test -Dtest=ManagementApiApplicationTests # Run a single test class
./mvnw package -DskipTests                       # Package JAR
```

The app requires environment variables from a `.env` file (or system env) — copy `.env.example` and fill in your **own** Supabase project's credentials: `DB_URL`, `DB_USER`, `DB_PASS`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_ANON_KEY`, `SUPABASE_JWT_SECRET`.

## Architecture

**Stack:** Spring Boot 3.5 · Java 21 · PostgreSQL (Supabase) · Flyway · MapStruct · Lombok

### Layer structure

```
controller/       → REST endpoints (auth, profile, employees, locations, activities)
service/          → Business logic
repository/       → JPA repositories (Spring Data)
model/            → JPA entities
dto/              → Request/Response objects (dto/common/* is shared: location, media)
mapper/           → MapStruct mappers (entity ↔ DTO; mapper/common/* is shared)
security/         → JWT auth, filters, security config, authorization
config/           → Spring config classes (JPA auditing, async, Supabase)
integrations/     → Supabase auth + storage clients
enterprises/      → Enterprise (project) & construction-management sub-module
exeption/         → Custom exceptions (BusinessException, ResourceNotFoundException, etc.)
infra/            → Infrastructure utilities (datasource diagnostics, startup failure logging)
util/             → General utilities (TokenHash, etc.)
```

This is a scoped copy of [Property-Management](https://github.com/bino03/Property-Management)'s backend — see [[../../docs/database.md]] for exactly what was kept vs. dropped (no property listings, no payments, no portal-facing endpoints).

### Database

- Schemas: `worksite` (main), `settings` (config/invites), `tasks` (standalone task management), `auth` (Supabase-managed — never touch)
- Migrations: `src/main/resources/db/migration/` — Flyway V1–V14, `ddl-auto: none`
- When adding a new table: create a new `V{next}__description.sql` in `db/migration/` — never alter the DB directly via Supabase. See [[../../docs/skills/backend/skill-add-database-table]]
- Base entity: UUID PK + `createdAt`/`updatedAt` (JPA auditing enabled)

### Security & Authorization

**Authentication:** Fully delegated to Supabase (JWT HS256). Spring Security config (`SecurityConfig.java`) sets up an OAuth2 Resource Server with a custom JWT decoder.

**JWT Handling:**
- Decoding: HS256 with Supabase secret (loaded from `SUPABASE_JWT_SECRET`)
- Custom filters handle token revocation checks and account lock status
- `AuthContext.java` provides current user information and role resolution

**Authorization:**
- Role-based access control (RBAC) using JWT claims (`app_metadata`) and `Profile.role`
- Method-level security with `@PreAuthorize` annotations
- Custom filters (`AccountLockFilter`, `TokenRevocationFilter`) enforce account-status/lock and revocation rules at request level — they are not role-based authorization

**Token Revocation:**
- Revoked tokens stored in database (`RevokedTokenRepository`)
- Cleanup scheduled via `RevokedTokenCleanupConfig`
- Hash utility: `TokenHash.java` for secure token comparison

See [[../../docs/skills/backend/skill-permissions-and-auth]] for adding access control to a new endpoint.

### MapStruct + Lombok

Annotation processor order in `pom.xml` is intentional: **Lombok must run before MapStruct**. Do not reorder these processors.

### Error handling

- **ErrorCode enum** (`dto/error/ErrorCode.java`) — codes organized by domain module
- **Custom exceptions** (`exeption/` package — note: typo in package name, kept for consistency with the original):
  - `BusinessException` — domain business logic violations
  - `ResourceNotFoundException` — resource not found (404)
  - `FileUploadException` — file upload failures
  - `StorageException` — Supabase storage errors
  - `ForbiddenException` — authorization failures (403)
- **Global exception handler** (`GlobalExceptionHandler.java`) — maps exceptions → structured error responses with ErrorCode. This is the *only* `@RestControllerAdvice` in the app — the original project had a second, conflicting one in `infra/` that was dropped during the copy (dead code + duplicate `HttpMediaTypeNotSupportedException` handler).

### File uploads

Max 25 MB (configured in `application.yml`). Construction expense invoices go through `SupabaseStorageService` (bucket `"documents"`) — see [[../../docs/skills/backend/skill-add-file-upload]].

### CORS

Configured in `SecurityConfig.java` (`corsConfigurationSource()` method) — `WebConfig.java` is intentionally empty. Update the allowed origins list for your actual Backoffice dev/prod URLs (defaults to `http://localhost:5173`).

### Email

`spring-boot-starter-mail` is included. Used for the admin-invite flow (`AdminAuthController` → `EmailService`), configured via `settings.email_providers`.

### Photo URLs in API responses

Whenever an endpoint returns a photo/media URL, **always generate a Supabase signed URL** using `SupabaseStorageService.createSignedUrl(bucket, key, expiresInSeconds)`. Never return a raw stored key/path directly.

Pattern to follow (used in `ProfileService` and `AuthController`):

```java
private String resolvePhotoUrl(Profile profile) {
    if (profile == null || profile.getPhotoKey() == null) return null;
    try {
        String bucket = profile.getPhotoBucket();
        String key = profile.getPhotoKey().startsWith("/") ? profile.getPhotoKey().substring(1) : profile.getPhotoKey();
        return storage.createSignedUrl(bucket, key, 3600);
    } catch (Exception e) {
        log.warn("Não foi possível gerar signed URL: {}", e.getMessage());
        return null;
    }
}
```

- Strip any leading `/` from the key before calling `createSignedUrl`
- Return `null` gracefully if the profile has no photo or if the call fails
- The reference implementation is `POST /profile/photo-url` in `ProfileController`

### Infrastructure & Diagnostics

Located in `infra/` package:
- **DataSourceDiagnostics.java** — Database connection diagnostics
- **StartupFailureLogger.java** — Logs initialization failures for debugging
