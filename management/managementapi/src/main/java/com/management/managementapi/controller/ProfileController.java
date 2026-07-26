package com.management.managementapi.controller;
// TRATAMENTOS DE ERROS ✅✅

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.profile.EmailUpdateRequest;
import com.management.managementapi.dto.profile.PasswordUpdateRequest;
import com.management.managementapi.dto.profile.PhotoUrlRequestDTO;
import com.management.managementapi.dto.profile.ProfileDTO;
import com.management.managementapi.dto.profile.ProfileListResponseDTO;
import com.management.managementapi.dto.profile.ProfileUpdateRequest;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.service.ProfileService;
import com.management.managementapi.repository.ProfileRepository;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storage;
    private final JdbcTemplate jdbcTemplate;

    public ProfileController(
            ProfileService profileService,
            ProfileRepository profileRepository,
            SupabaseStorageService storage,
            JdbcTemplate jdbcTemplate) {
        this.profileService = profileService;
        this.profileRepository = profileRepository;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ===================== PERFIL (já tinhas) =====================

/**
     * GET /api/profiles/agents
     * Retorna todos os profiles exceto o do utilizador autenticado
     */
    @GetMapping("/agents")
    public ResponseEntity<List<ProfileListResponseDTO>> getAllAgents(Authentication authentication) {
        // Obter o auth_user_id do utilizador autenticado (vem do JWT sub)
        UUID currentAuthUserId = UUID.fromString(authentication.getName());
        
        String sql = """
            SELECT 
                p.id,
                p.name,
                COALESCE(u.email, '') as email,
                p.role,
                p.phone_number
            FROM pm.profile p
            LEFT JOIN auth.users u ON p.auth_user_id = u.id
            WHERE p.auth_user_id != ? OR p.auth_user_id IS NULL
            ORDER BY p.name ASC
        """;
        
        List<ProfileListResponseDTO> profiles = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> {
                ProfileListResponseDTO dto = new ProfileListResponseDTO();
                dto.setId(UUID.fromString(rs.getString("id")));
                dto.setName(rs.getString("name"));
                dto.setEmail(rs.getString("email"));
                
                // ✅ CONVERTER STRING PARA ENUM
                String roleString = rs.getString("role");
                if (roleString != null && !roleString.isBlank()) {
                    try {
                        dto.setRole(ProfileRole.valueOf(roleString));
                    } catch (IllegalArgumentException e) {
                        // Se o valor não for válido, usar um default ou null
                        dto.setRole(null);
                    }
                }
                
                dto.setPhoneNumber(rs.getString("phone_number"));
                return dto;
            },
            currentAuthUserId
        );

        return ResponseEntity.ok(profiles);
    }





    @GetMapping("/myprofile")
    public ResponseEntity<ProfileDTO> getMyProfile(Authentication authentication) {
        UUID authUserId = UUID.fromString(authentication.getName());
        ProfileDTO profile = profileService.getProfileByUserId(authUserId)
                .orElseThrow(() -> ResourceNotFoundException.profile(authUserId.toString()));
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/updateNamePhone")
    public ResponseEntity<String> updateNamePhone(@RequestBody ProfileUpdateRequest request,
            Authentication authentication) {
        UUID authUserId = UUID.fromString(authentication.getName());
        try {
            profileService.updateNamePhone(authUserId, request);
            return ResponseEntity.ok("Perfil atualizado com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    @PutMapping("/updateEmail")
    public ResponseEntity<String> updateEmail(@RequestBody EmailUpdateRequest request,
            Authentication authentication) {
        UUID authUserId = Objects.requireNonNull(UUID.fromString(authentication.getName()), "authUserId");
        try {
            profileService.updateEmail(authUserId, request);
            return ResponseEntity.ok("Email atualizado com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

   @PutMapping("/updatePassword")
public ResponseEntity<String> updatePassword(@RequestBody @Valid PasswordUpdateRequest request,
                                             Authentication authentication) {
    UUID authUserId = Objects.requireNonNull(UUID.fromString(authentication.getName()), "authUserId");
        try {
            profileService.updatePassword(authUserId, request);
            return ResponseEntity.ok("Palavra-passe atualizada com sucesso.");
        } catch (ValidationException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
        }
    }

    // ===================== FOTO POR ID (NOVO) =====================

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp");

    /**
     * Upload da foto de perfil por AUTH USER ID (id do supabase).
     * POST /profile/{authUserId}/photo
     * Body: multipart/form-data, campo "file"
     */
    @PostMapping(path = "/{authUserId}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Map<String, Object>> uploadPhotoByAuthUserId(
        @PathVariable UUID authUserId,
        @RequestParam("file") MultipartFile file,
        Authentication auth
) throws IOException {

    // segurança: ADMIN pode tudo; restantes só o próprio
    if (auth == null) throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED, "Utilizador não autenticado");
    boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    UUID caller = UUID.fromString(auth.getName());
    if (!isAdmin && !caller.equals(authUserId)) {
        throw new BusinessException(ErrorCode.FORBIDDEN, "Não podes alterar a foto de outro utilizador");
    }

    if (file == null || file.isEmpty()) {
        throw FileUploadException.empty("foto");
    }

    String mime = Optional.ofNullable(file.getContentType())
            .orElse("application/octet-stream")
            .toLowerCase();
    if (!ALLOWED_MIME.contains(mime)) {
        throw FileUploadException.invalidImageFormat(file.getOriginalFilename());
    }

    String ext = switch (mime) {
        case "image/jpeg", "image/jpg" -> "jpg";
        case "image/png" -> "png";
        case "image/webp" -> "webp";
        default -> "bin";
    };

    Profile profile = profileRepository.findByAuthUserId(authUserId)
            .orElseThrow(() -> ResourceNotFoundException.profile(authUserId.toString()));

    // 1) apaga foto antiga, se existir
    String oldBucket = profile.getPhotoBucket();
    String oldKey = profile.getPhotoKey();
    if (oldBucket != null && oldKey != null) {
        try {
            storage.delete(oldBucket, oldKey);
        } catch (Exception e) {
            // não falha o upload por causa disto; apenas regista
            // (se tiveres logger: log.warn("Falha ao apagar foto antiga: {}", e.getMessage());
        }
    }

    // 2) faz upload da nova
    String bucket = Optional.ofNullable(profile.getPhotoBucket()).orElse("private");
    String path = "profilephoto/%s/avatar/%s.%s".formatted(authUserId, UUID.randomUUID(), ext);

    try (InputStream in = file.getInputStream()) {
        storage.upload(bucket, path, mime, in);
    }

    // 3) atualiza ponteiros no perfil
    profile.setPhotoBucket(bucket);
    profile.setPhotoKey(path);
    profileRepository.save(profile);

    // 4) responde com os novos dados + URL estável do proxy
    return ResponseEntity.ok(Map.of(
            "photoBucket", bucket,
            "photoKey", path,
            "photoUrl", "/profile/%s/photo".formatted(authUserId)
    ));
}


    /**
     * GET /profile/{authUserId}/photo
     * Devolve a foto via proxy (stream do Supabase) — Método C.
     */
    @GetMapping("/{authUserId}/photo")
    public void getPhotoByAuthUserId(@PathVariable UUID authUserId, HttpServletResponse res) throws IOException {
        Profile profile = profileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> ResourceNotFoundException.profile(authUserId.toString()));

        if (profile.getPhotoKey() == null) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND, "Este perfil não tem foto");
        }

        storage.streamDownload(profile.getPhotoBucket(), profile.getPhotoKey(), res);
    }

    /**
     * DELETE /profile/{authUserId}/photo
     * Limpa ponteiros de foto (não apaga do storage; posso adicionar delete no
     * service se quiseres).
     */
    @DeleteMapping("/{authUserId}/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePhotoByAuthUserId(@PathVariable UUID authUserId, Authentication auth) {
        if (auth == null) throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED, "Utilizador não autenticado");
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        UUID caller = UUID.fromString(auth.getName());
        if (!isAdmin && !caller.equals(authUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Não podes remover a foto de outro utilizador");
        }

        Profile profile = profileRepository.findByAuthUserId(authUserId)
                .orElseThrow(() -> ResourceNotFoundException.profile(authUserId.toString()));

        profile.setPhotoKey(null);
        // se quiseres, também: profile.setPhotoBucket(null);
        profileRepository.save(profile);

        return ResponseEntity.noContent().build();
    }

    // gera URL assinado para a foto, com autorização:
    // - ADMIN pode qualquer bucket/key
    // - caso contrário, o UUID do token tem de coincidir com o UUID presente na
    // photoKey: "profilephoto/{uuid}/..."
    @PostMapping("/photo-url")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<Map<String, Object>> getSignedPhotoUrl(
        @RequestBody PhotoUrlRequestDTO req,
        Authentication auth) {

    if (req.getBucket() == null || req.getBucket().isBlank()
        || req.getPhotoKey() == null || req.getPhotoKey().isBlank()) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "bucket e photoKey são obrigatórios");
    }

    final String PROFILE_PREFIX = "profilephoto/";
    if (auth == null) throw new BusinessException(ErrorCode.USER_NOT_AUTHENTICATED, "Utilizador não autenticado");
    boolean isAdmin = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

    UUID caller = UUID.fromString(auth.getName());

    if (!isAdmin) {
        if (!req.getPhotoKey().startsWith(PROFILE_PREFIX)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Chave de ficheiro inválida");
        }
        String rest = req.getPhotoKey().substring(PROFILE_PREFIX.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) throw new BusinessException(ErrorCode.FORBIDDEN, "Chave de ficheiro inválida");
        UUID ownerId = UUID.fromString(rest.substring(0, slash));
        if (!caller.equals(ownerId)) throw new BusinessException(ErrorCode.FORBIDDEN, "Não autorizado a aceder a esta foto");
    }

    // limpar path: sem "/" inicial
    String key = req.getPhotoKey().startsWith("/") ? req.getPhotoKey().substring(1) : req.getPhotoKey();
    String bucket = req.getBucket(); // "media"

    int expires = (req.getExpiresIn() == null || req.getExpiresIn() <= 0) ? 600 : req.getExpiresIn();

    try {
        // >>> o teu client DEVE receber (bucket, key) separados (sem codificar as "/")
        String urlAssinada = storage.createSignedUrl(bucket, key, expires);

        return ResponseEntity.ok(Map.of(
            "url", urlAssinada,
            "expiresIn", expires,
            "expiresAt", OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expires).toString()
        ));
    } catch (IOException e) {
        throw StorageException.signedUrlError(key, e);
    }
}

}
