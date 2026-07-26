# Skill: Add Database Table

**When to use**: Creating a new table/schema in PostgreSQL

**Time**: ~30 minutes + waiting for Flyway

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.

---

## Phase 1: Planning (Answer These First!)

Copy this block, fill it out, save it before coding:

```markdown
## Planning: <table_name>

### 1. What does this table represent?
(1-2 sentences. Example: "Stores rental contracts linked to an asset")

### 2. Who creates/edits records?
[ ] Admin only
[ ] Admin + Employee
[ ] Self-service (users)
[ ] System (auto-generated)

### 3. Who can read records?
[ ] Admin only
[ ] Admin + Employee
[ ] Any authenticated user
[ ] Public (no auth)

### 4. Related to an asset/property?
[ ] Yes - has FK to property_asset
[ ] No - independent

### 5. Relations to other tables?
(List FKs: "FK to pm.profile, FK to pm.property_asset")

### 6. Required fields?
(field: TYPE, NOT NULL, default value)
Example:
- name: TEXT, NOT NULL
- value: NUMERIC(12,2)
- state: TEXT, NOT NULL, default 'draft'
- asset_id: UUID, FK → pm.property_asset

### 7. Need soft-delete (deleted_at)?
[ ] Yes (important data that shouldn't disappear)
[ ] No (physical delete is enough)

### 8. Need RLS (Row Level Security)?
[ ] Yes - per-tenant/user data
[ ] No - backend handles security

### 9. Next Flyway migration number?
Run: `ls src/main/resources/db/migration/ | tail -1`
Answer: V___
```

---

## Step 1: Create SQL Migration

Create file: `src/main/resources/db/migration/V{number}__description_of_table.sql`

### Template

```sql
set search_path to pm, public;

-- Main table
create table if not exists pm.my_table (
    id          uuid        not null default gen_random_uuid() primary key,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    -- Your fields
    name        text        not null,
    description text,
    state       text        not null default 'draft',
    
    -- Foreign keys
    asset_id    uuid        not null references pm.property_asset(id) on delete cascade,
    owner_id    uuid        references pm.profile(id) on delete set null
);

-- Trigger for updated_at (uses existing function)
create trigger tg_my_table_updated_at
    before update on pm.my_table
    for each row execute procedure pm.tg_set_updated_at();

-- Indexes (add ones relevant to your queries)
create index if not exists idx_my_table_asset_id on pm.my_table(asset_id);
create index if not exists idx_my_table_owner_id on pm.my_table(owner_id);
create index if not exists idx_my_table_state on pm.my_table(state);
```

### If you need soft-delete

```sql
-- Add to table definition:
deleted_at  timestamptz null,

-- Index for "only active" queries
create index if not exists idx_my_table_active
    on pm.my_table(id) where deleted_at is null;
```

### If you need RLS

```sql
-- Enable RLS
alter table pm.my_table enable row level security;

-- Policy example: users see only their own records
create policy my_table_user_read on pm.my_table
    for select using (owner_id = auth.uid());
```

---

## Step 2: Run Migration in Supabase

> **Note**: Flyway reads from `db/migration/` at startup, or manually via Spring Boot.

1. **Option A (Recommended)**: Start Spring Boot
   ```bash
   cd managementapi
   ./mvnw spring-boot:run
   ```
   Flyway runs automatically on startup.

2. **Option B (Manual in Supabase UI)**:
   - Go to Supabase → SQL Editor
   - Create new query
   - Paste your SQL
   - Run

---

## Step 3: Create JPA Entity

Location: `src/main/java/com/management/managementapi/model/MyTable.java`

```java
@Entity
@Table(name = "my_table", schema = "pm")
@Getter
@Setter
@NoArgsConstructor
public class MyTable extends BaseEntity {

    private String name;
    
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private State state = State.DRAFT;

    @Column(name = "owner_id")
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false, foreignKey = @ForeignKey(name = "fk_my_table_asset"))
    private PropertyAsset asset;
    
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public enum State {
        DRAFT, ACTIVE, ARCHIVED
    }
}
```

**Important**:
- Extend `BaseEntity` (gives you `id`, `createdAt`, `updatedAt`)
- Use `@Enumerated(EnumType.STRING)` for enums
- Use `FetchType.LAZY` on relations to avoid N+1 queries
- Match column names exactly to your SQL

---

## Step 4: Add to EntityType Enum

Open `model/enums/EntityType.java`:

```java
public enum EntityType {
    MY_TABLE,
    // ...
}
```

Used for activity logging.

---

## Step 5: Create Repository

```java
@Repository
public interface MyTableRepository extends JpaRepository<MyTable, UUID> {
    Page<MyTable> findByOwnerIdAndDeletedAtIsNull(UUID ownerId, Pageable pageable);
    List<MyTable> findByAssetId(UUID assetId);
    Optional<MyTable> findByIdAndDeletedAtIsNull(UUID id);
}
```

If soft-delete: Always filter `deletedAt is null` in queries.

---

## Step 6: Create Service with Soft-Delete Support

```java
@Service
@RequiredArgsConstructor
@Transactional
public class MyTableService {

    private final MyTableRepository repository;

    @Transactional(readOnly = true)
    public Page<MyTableResponseDTO> list(UUID ownerId, Pageable pageable) {
        return repository.findByOwnerIdAndDeletedAtIsNull(ownerId, pageable)
            .map(mapper::toResponse);
    }

    public MyTable getById(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException(...));
    }

    public void softDelete(UUID id) {
        MyTable entity = getById(id);
        entity.setDeletedAt(OffsetDateTime.now());
        repository.save(entity);
    }
}
```

---

## Final Checklist

- [ ] SQL migration file created in `db/migration/`
- [ ] Migration number sequential (V1, V2, etc.)
- [ ] Table has `id`, `created_at`, `updated_at` (BaseEntity)
- [ ] Foreign keys use `on delete cascade` or `set null` appropriately
- [ ] Indexes created for columns used in WHERE/JOIN
- [ ] JPA Entity created matching SQL exactly
- [ ] Entity extends `BaseEntity`
- [ ] EntityType enum updated
- [ ] Repository has custom queries needed
- [ ] Service filters `deleted_at is null` if soft-delete
- [ ] Tested: can insert/query rows in Supabase
- [ ] New table/relationship reflected in `docs/database.md` (the git pre-commit hook flags a new migration file, but you still write the schema summary by hand)

---

## Common Patterns

### Self-referential (parent-child)

```sql
create table pm.node (
    id        uuid primary key,
    parent_id uuid references pm.node(id) on delete cascade
);
```

### Many-to-many

```sql
create table pm.my_table_tags (
    my_table_id uuid not null references pm.my_table(id) on delete cascade,
    tag_id      uuid not null references pm.tag(id) on delete cascade,
    primary key (my_table_id, tag_id)
);
```

### Versioning/History

```sql
create table pm.my_table_history (
    id           serial primary key,
    my_table_id  uuid not null references pm.my_table(id),
    changed_at   timestamptz default now(),
    changed_by   uuid references pm.profile(id),
    change_type  text, -- 'CREATE', 'UPDATE', 'DELETE'
    old_value    jsonb,
    new_value    jsonb
);
```

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[skill-add-backend-feature]] — Create API endpoints after table
- [[skill-permissions-and-auth]] — Set up RLS policies if needed
