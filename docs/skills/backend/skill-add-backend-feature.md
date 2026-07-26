# Skill: Add Backend Feature (Full Checklist)

**When to use**: Adding a new REST endpoint/feature to Spring Boot backend

**Time**: ~2-3 hours for a complete CRUD feature

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.

---

## Phase 0: API Contract Definition

Before writing any code, answer these questions:

### Which operations do you need?

| Operation | Method | Path | Needed? |
|-----------|--------|------|---------|
| List (paginated) | GET | `/resource` | [ ] |
| Get by ID | GET | `/resource/{id}` | [ ] |
| Create | POST | `/resource` | [ ] |
| Update (full) | PUT | `/resource/{id}` | [ ] |
| Update (partial) | PATCH | `/resource/{id}` | [ ] |
| Delete | DELETE | `/resource/{id}` | [ ] |
| Business action | POST | `/resource/{id}/action` | [ ] |
| Sub-resource | GET/POST | `/resource/{id}/sub` | [ ] |

### Other considerations

- **Who can call each operation?** (public read? admin-only write?)
- **Does list need filters?** (e.g. `?q=`, `?state=`)
- **Return files/photos?** (if yes → see `skill-add-file-upload.md` for signed URLs)

---

## Step 1: Add ErrorCodes

Open `dto/error/ErrorCode.java` and add codes for all possible error cases:

```java
// Add in the appropriate module block (or create new block)
MY_ENTITY_NOT_FOUND("MYENTITY_001", "Entity not found"),
MY_ENTITY_CREATE_ERROR("MYENTITY_002", "Error creating entity"),
MY_ENTITY_DUPLICATE("MYENTITY_003", "Entity already exists"),
```

**Do this FIRST** — Service and Controller will reference these codes.

---

## Step 2: Create DTOs

Create in `dto/<module>/`:

```
dto/
  my-entity/
    request/
      MyEntityUpsertDTO.java    ← Data client sends
    response/
      MyEntityResponseDTO.java  ← What you return
```

### Request DTO (with validation)

```java
public record MyEntityUpsertDTO(
    @NotBlank(message = "Name is required") String name,
    @NotNull @Positive long value,
    @NotNull UUID relatedId
) {}
```

### Response DTO (no sensitive data!)

```java
public record MyEntityResponseDTO(
    UUID id,
    String name,
    long value,
    OffsetDateTime createdAt
) {}
```

---

## Step 3: Create Entity (if new table)

If you need a new table → follow `skill-add-database-table.md` first.

Otherwise, use existing entity.

---

## Step 4: Create Repository

```java
@Repository
public interface MyEntityRepository extends JpaRepository<MyEntity, UUID> {
    // Add custom queries if needed
    Page<MyEntity> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Optional<MyEntity> findByExternalId(String externalId);
}
```

---

## Step 5: Create MapStruct Mapper

```java
@Mapper(componentModel = "spring")
public interface MyEntityMapper {
    MyEntityResponseDTO toResponse(MyEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(MyEntityUpsertDTO dto, @MappingTarget MyEntity entity);
}
```

> **Important**: Lombok runs before MapStruct in `pom.xml` — order is correct, don't change it.

---

## Step 6: Create Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class MyEntityService {

    private final MyEntityRepository repository;
    private final MyEntityMapper mapper;

    @Transactional(readOnly = true)
    public Page<MyEntityResponseDTO> list(String query, Pageable pageable) {
        return repository.findByNameContainingIgnoreCase(query, pageable)
            .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MyEntity getById(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.MY_ENTITY_NOT_FOUND, id.toString()));
    }

    public MyEntity create(MyEntityUpsertDTO dto) {
        MyEntity entity = new MyEntity();
        mapper.updateEntity(dto, entity);
        return repository.save(entity);
    }

    public MyEntity update(UUID id, MyEntityUpsertDTO dto) {
        MyEntity entity = getById(id);
        mapper.updateEntity(dto, entity);
        return repository.save(entity);
    }

    public void delete(UUID id) {
        MyEntity entity = getById(id);
        repository.delete(entity);
    }
}
```

---

## Step 7: Create Controller

Create with **ALL operations from Step 0**. Remove methods you don't need — no empty stubs.

```java
@RestController
@RequestMapping("/my-entity")
@RequiredArgsConstructor
public class MyEntityController {

    private final MyEntityService service;
    private final MyEntityMapper mapper;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── READ ──────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<MyEntityResponseDTO> list(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.list(q, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<MyEntityResponseDTO> getById(@PathVariable @NonNull UUID id) {
        return ResponseEntity.ok(mapper.toResponse(service.getById(id)));
    }

    // ── WRITE ─────────────────────────────────

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MyEntityResponseDTO> create(
            @Valid @RequestBody MyEntityUpsertDTO dto,
            HttpServletRequest request) {

        MyEntity created = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
            activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                EntityType.MY_ENTITY, created.getId(), created.getName(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MyEntityResponseDTO> update(
            @PathVariable @NonNull UUID id,
            @Valid @RequestBody MyEntityUpsertDTO dto,
            HttpServletRequest request) {

        MyEntity updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
            activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                EntityType.MY_ENTITY, id, updated.getName(), null, request));

        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable @NonNull UUID id,
            HttpServletRequest request) {

        MyEntity entity = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
            activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                EntityType.MY_ENTITY, id, entity.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

### Response Status Codes

| Operation | Status | Body |
|-----------|--------|------|
| GET list | 200 | `Page<DTO>` |
| GET by id | 200 | `DTO` |
| POST (create) | **201** | `DTO` |
| PUT / PATCH | 200 | `DTO` |
| DELETE | **204** | empty |

---

## Step 8: Update SecurityConfig

Open `security/SecurityConfig.java` and confirm your path is covered:

```java
.requestMatchers(HttpMethod.GET, "/my-entity/**").permitAll()
.requestMatchers(HttpMethod.POST, "/my-entity/**").hasRole("ADMIN")
```

---

## Step 9: Add EntityType (if needed)

Open `model/enums/EntityType.java` and add your entity:

```java
public enum EntityType {
    MY_ENTITY,
    // ...
}
```

---

## Final Checklist

- [ ] ErrorCode added for all error cases
- [ ] Request DTO has `@Valid` + field validations
- [ ] Response DTO doesn't expose sensitive data (passwords, tokens)
- [ ] Service has `@Transactional(readOnly = true)` on read methods
- [ ] Controller has `@PreAuthorize` on all methods (or `permitAll()`)
- [ ] `activityLogger` called on create/update/delete
- [ ] Return file URLs as signed URLs (see [[skill-add-file-upload]])
- [ ] Tested manually with curl/Postman before committing
- [ ] New/changed endpoints reflected in `docs/api.md` (the git pre-commit hook flags new controller files, but table changes and endpoint changes to existing controllers need updating by hand)

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[skill-add-database-table]] — If you need a new table
- [[skill-add-file-upload]] — If returning files/photos
- [[skill-permissions-and-auth]] — For auth/authorization rules
