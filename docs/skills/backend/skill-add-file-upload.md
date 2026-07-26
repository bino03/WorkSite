# Skill: Add File Upload

**When to use**: Feature needs to upload files/photos to Supabase Storage

**Time**: ~1-2 hours (validation + upload + storage + signed URLs)

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.

---

## Fundamental Rules

1. **Never return raw `photoUrl`** — Supabase Storage URLs don't work without signatures. Always use signed URLs.
2. **Never store file in database** — Store `bucket` + `storageKey` in the table. File goes to Supabase Storage.
3. **Validate MIME type before upload** — Never trust filename extension alone.
4. **Strip leading `/` from key** before calling `createSignedUrl()` — Supabase rejects keys with leading `/`.
5. **Global limit: 25 MB** per file (configured in `application.yml`).

---

## Entity Fields

Any entity storing a file needs these two fields:

```java
@Column(name = "photo_bucket")
private String photoBucket;       // e.g. "media", "documents"

@Column(name = "photo_key")
private String photoKey;          // e.g. "assets/uuid/banner/uuid.jpg"
```

**Never store the full URL** — it changes (signed URLs expire).

---

## Storage Key Naming Convention

```
<entity>/<id>/<type>/<uuid>.<ext>

Examples:
  assets/3fa85f64/banner/9b1a2c3d.jpg
  assets/3fa85f64/gallery/1e2f3a4b.png
  profiles/7c8d9e0f/photo/2a3b4c5d.jpg
  documents/asset-requests/5f6g7h8i.pdf
```

---

## Step 1: Allowed MIME Types & Sizes

Define what you accept:

```java
private static final Set<String> ALLOWED_IMAGE_MIME = Set.of(
    "image/jpeg", "image/jpg", "image/png", "image/webp", "image/avif"
);
private static final Set<String> ALLOWED_DOC_MIME = Set.of(
    "application/pdf", "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
);
private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;  // 15 MB
private static final long MAX_DOC_BYTES   = 25L * 1024 * 1024;  // 25 MB
```

---

## Step 2: Validate File

```java
private void validateImage(MultipartFile file) {
    if (file == null || file.isEmpty())
        throw FileUploadException.empty(file != null ? file.getOriginalFilename() : "file");

    String mime = Optional.ofNullable(file.getContentType()).orElse("");
    if (!ALLOWED_IMAGE_MIME.contains(mime))
        throw FileUploadException.invalidImageFormat(file.getOriginalFilename());

    if (file.getSize() > MAX_IMAGE_BYTES)
        throw FileUploadException.imageSizeExceeded(file.getOriginalFilename(), file.getSize());
}
```

---

## Step 3: Upload to Supabase Storage

```java
@RequiredArgsConstructor
public class MediaService {
    
    private final SupabaseStorageService storageService;
    
    public void uploadAssetBanner(UUID assetId, MultipartFile file) {
        validateImage(file);
        
        String mime = file.getContentType();
        String ext = extFromMime(mime);  // "jpg", "png", etc.
        String bucket = "media";
        String key = "assets/" + assetId + "/banner/" + UUID.randomUUID() + "." + ext;
        
        try {
            storageService.uploadFile(bucket, key, file.getInputStream(), mime);
        } catch (IOException e) {
            throw new StorageException("Upload failed: " + e.getMessage());
        }
    }
}
```

---

## Step 4: Save to Entity

```java
@Service
@RequiredArgsConstructor
public class AssetService {
    
    private final AssetRepository repository;
    private final MediaService mediaService;
    
    public void uploadBanner(UUID assetId, MultipartFile file) {
        Asset asset = repository.findById(assetId)
            .orElseThrow(() -> new ResourceNotFoundException(...));
        
        // Upload to storage
        String key = mediaService.uploadAssetBanner(assetId, file);
        
        // Save reference in entity
        asset.setPhotoBucket("media");
        asset.setPhotoKey(key);
        repository.save(asset);
    }
}
```

---

## Step 5: Generate Signed URL When Returning Data

```java
// In Service or DTO mapper
public AssetResponseDTO toResponse(Asset asset) {
    String photoUrl = null;
    if (asset.getPhotoKey() != null) {
        try {
            String bucket = asset.getPhotoBucket();
            String key = asset.getPhotoKey().startsWith("/") 
                ? asset.getPhotoKey().substring(1) 
                : asset.getPhotoKey();
            photoUrl = storageService.createSignedUrl(bucket, key, 3600); // 1 hour
        } catch (Exception e) {
            log.warn("Failed to generate signed URL: {}", e.getMessage());
        }
    }
    
    return new AssetResponseDTO(
        asset.getId(),
        asset.getName(),
        photoUrl,  // ← Return signed URL, not raw path
        // ... other fields
    );
}
```

---

## Step 6: Controller Endpoint

```java
@RestController
@RequestMapping("/assets/{id}/banner")
@RequiredArgsConstructor
public class AssetPhotosController {
    
    private final AssetService assetService;
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetResponseDTO> uploadBanner(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        
        assetService.uploadBanner(id, file);
        Asset updated = assetService.getById(id);
        return ResponseEntity.ok(mapper.toResponse(updated));
    }
    
    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBanner(@PathVariable UUID id) {
        assetService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## Step 7: Error Handling

O bloco `FILE_xxx` de `dto/error/ErrorCode.java` já cobre os casos comuns de upload — **usa estes, não recries com números diferentes** (`FILE_001`–`FILE_010` já estão todos atribuídos a outros significados):

| Caso | Usa |
|---|---|
| Formato/tipo não permitido | `FILE_TYPE_NOT_ALLOWED` (`FILE_003`) |
| Tamanho excede o limite | `FILE_SIZE_EXCEEDED` (`FILE_002`) |
| Ficheiro vazio | `FILE_EMPTY` (`FILE_008`) |
| Erro genérico no upload | `FILE_UPLOAD_ERROR` (`FILE_001`) |
| Erro no storage (Supabase) | `STORAGE_CONNECTION_ERROR` ou outro do bloco `STORAGE_xxx` — não `FILE_STORAGE_ERROR` (`FILE_010`) a menos que o erro seja especificamente sobre o *ficheiro em storage*, não sobre a *ligação* ao storage |

Só adiciona um código novo ao bloco `FILE_xxx` se o caso for genuinamente novo (ex. uma regra de negócio específica do teu upload que nenhum dos existentes descreve) — confirma sempre no ficheiro real antes de escolher o próximo número livre do bloco.

---

## Final Checklist

- [ ] Entity has `photoBucket` + `photoKey` columns
- [ ] MIME types whitelist defined
- [ ] File size limits set
- [ ] Validation method validates both MIME + size
- [ ] Upload generates unique key (includes UUID)
- [ ] File saved to Supabase Storage (not database)
- [ ] Entity reference saved to database
- [ ] Signed URL generated when returning data (1 hour expiry)
- [ ] Leading `/` stripped from key before `createSignedUrl()`
- [ ] Error codes added for all error cases
- [ ] Controller has `@PreAuthorize` authorization
- [ ] Tested with various file types + sizes

---

## Common Mistakes

❌ Storing full Supabase URL in entity (it expires)
❌ Not validating MIME type
❌ Not stripping leading `/` from key
❌ Storing file in database instead of storage
❌ Not handling upload exceptions
❌ Forgetting to delete old file when uploading new one

---

## Related Skills

- [[code-best-practices]] — General code quality rules, incl. backend error-handling conventions
- [[skill-add-backend-feature]] — General feature implementation
