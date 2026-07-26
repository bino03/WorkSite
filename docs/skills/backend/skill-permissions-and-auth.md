# Skill: Implement Permissions & Authorization

**When to use**: Feature needs access control (who can do what)

**Time**: ~30 minutes

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.

---

## Permission Model

| Action | ADMIN | EMPLOYEE/USER |
|--------|-------|---------------|
| **VIEW (GET)** | ✅ All | ✅ All assets |
| **CREATE (POST)** | ✅ Yes | ✅ Yes (creator gets `createdBy = user_id`) |
| **EDIT (PUT)** | ✅ All | ✅ Own records or assigned ones |
| **DELETE (DELETE)** | ✅ Yes | ❌ No |
| **LIST (GET)** | ✅ All | ✅ All (filter by permissions in service) |

### Visibilidade a nível de campo (não só de registo)

A tabela acima é sobre **acesso ao registo inteiro**. Um caso diferente: um registo é visível para uma role, mas **um campo específico dentro dele** (ex. dados financeiros, notas internas) só deve ser visto por `ADMIN`. Isso não se resolve com `@PreAuthorize` no endpoint — resolve-se no **mapper/DTO de resposta**: construir o DTO condicionalmente (ex. `null` ou omitir o campo se `!authContext.hasRole("ADMIN")`), nunca confiar que o frontend vai escondê-lo — quem inspecionar a resposta de rede continua a ver o campo se a API o devolver. O frontend só deve espelhar essa mesma regra por cima (esconder a coluna/campo na UI), como reforço, não como única defesa — ver [[skill-frontend-design-system]] → Visibilidade de campos por role.

---

## Step 1: Confirm the ErrorCode

O backend já tem códigos genéricos de autorização no bloco `USER_xxx` de `dto/error/ErrorCode.java` — **verifica antes de adicionar um novo**, para não duplicar:

```java
// dto/error/ErrorCode.java — já existem, não recriar
ACCESS_DENIED("USER_025", "Sem permissão para aceder a este recurso"),
INSUFFICIENT_PERMISSIONS("USER_027", "Permissões insuficientes para esta ação"),
```

Usa estes dois para a generalidade dos casos de `ForbiddenException`. Só cria um código novo se o caso for específico de um módulo (ex. um motivo de negação particular de `Transaction` ou `Asset`) que os dois genéricos acima não descrevem bem — nesse caso, adiciona-o ao bloco do módulo correspondente (`ASSET_xxx`, `TRANSACTION_xxx`, etc.), nunca a um prefixo novo tipo `AUTH_xxx` que não existe no ficheiro.

---

## Step 2: Use @PreAuthorize on Controller

```java
@RestController
@RequestMapping("/resources")
public class ResourceController {
    
    // Anyone authenticated can view
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<ResourceDTO> list(...) { ... }
    
    // Anyone can create (but service sets createdBy)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ResourceDTO> create(...) { ... }
    
    // Only ADMIN can delete
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { ... }
    
    // Only ADMIN or owner can edit
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ResourceDTO> update(
            @PathVariable UUID id,
            @RequestBody ResourceUpsertDTO dto) {
        
        // Service validates ownership
        service.validateEditAccess(id);
        return ResponseEntity.ok(mapper.toResponse(service.update(id, dto)));
    }
}
```

---

## Step 3: Validate Ownership in Service

```java
@Service
@RequiredArgsConstructor
public class ResourceService {
    
    private final ResourceRepository repository;
    private final AuthContext authContext;
    
    public void validateEditAccess(UUID id) {
        Resource resource = getById(id);
        UUID currentUser = authContext.currentProfileId()
            .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED));
        
        boolean isAdmin = authContext.hasRole("ADMIN");
        boolean isCreator = resource.getCreatedBy().equals(currentUser);
        
        if (!isAdmin && !isCreator) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
        }
    }
    
    public void validateDeleteAccess(UUID id) {
        Resource resource = getById(id);
        boolean isAdmin = authContext.hasRole("ADMIN");
        
        if (!isAdmin) {
            throw new ForbiddenException(ErrorCode.ACCESS_DENIED);
        }
    }
    
    public Resource create(ResourceUpsertDTO dto) {
        UUID createdBy = authContext.currentProfileId()
            .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED));
        
        Resource resource = new Resource();
        // ... set fields from dto
        resource.setCreatedBy(createdBy);
        
        return repository.save(resource);
    }
    
    public Resource update(UUID id, ResourceUpsertDTO dto) {
        validateEditAccess(id);
        
        Resource resource = getById(id);
        // ... update fields
        return repository.save(resource);
    }
    
    public void delete(UUID id) {
        validateDeleteAccess(id);
        repository.deleteById(id);
    }
}
```

---

## Step 4: Add createdBy to Entity

```java
@Entity
@Table(name = "resource")
public class Resource extends BaseEntity {
    
    private String name;
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    
    // ... other fields
}
```

---

## Step 5: Query Filter (Optional)

If you want employees to see only their own records:

```java
@Transactional(readOnly = true)
public Page<ResourceDTO> list(Pageable pageable) {
    UUID currentUser = authContext.currentProfileId()
        .orElseThrow(() -> new ForbiddenException(ErrorCode.ACCESS_DENIED));
    
    boolean isAdmin = authContext.hasRole("ADMIN");
    
    Specification<Resource> spec = (root, query, cb) -> {
        if (isAdmin) {
            return cb.conjunction();  // No filter for admin
        } else {
            return cb.equal(root.get("createdBy"), currentUser);
        }
    };
    
    return repository.findAll(spec, pageable)
        .map(mapper::toResponse);
}
```

---

## Security Hierarchy

```
REQUEST (JWT)
    ↓
[SecurityConfig] Validates JWT
    ↓
[Controller @PreAuthorize] Role-based check
    ↓
[Service validateAccess()] Ownership check
    ↓
[ForbiddenException] 403 if denied
    ↓
[GlobalExceptionHandler] Returns error response
```

---

## Common Roles

```java
@PreAuthorize("hasRole('ADMIN')")           // Admin only
@PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")  // Admin or employee
@PreAuthorize("permitAll()")                // Public (no auth)
@PreAuthorize("isAuthenticated()")          // Any logged-in user
```

---

## Final Checklist

- [ ] ErrorCodes confirmados (`ACCESS_DENIED`/`USER_025`, `INSUFFICIENT_PERMISSIONS`/`USER_027` — reutilizados, não recriados)
- [ ] Entity has `createdBy` field
- [ ] Controller has `@PreAuthorize` on all methods
- [ ] Service has `validateAccess()` methods for sensitive operations
- [ ] Create sets `createdBy` to current user
- [ ] Edit/delete checks ownership or admin
- [ ] List filters by user if needed
- [ ] ForbiddenException thrown on access denied
- [ ] Tested with different roles (admin vs employee)
- [ ] Changed a global/public rule (`SecurityConfig`, roles, CORS)? → update `docs/security.md` (the git pre-commit hook flags `SecurityConfig.java` changes, but you still write the summary by hand)

---

## Related Skills

- [[code-best-practices]] — General code quality rules, incl. backend error-handling conventions
- [[skill-add-backend-feature]] — Use this with auth checks
