package com.management.managementapi.enterprises.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.dto.common.location.LocationResponseDTO;
import com.management.managementapi.dto.common.media.MediaResponseDTO;
import com.management.managementapi.enterprises.dto.enterprise.CreateEnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.EnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseBasicDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseFullResponseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseListDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseResponseDTO;
import com.management.managementapi.enterprises.dto.location.EnterpriseLocationListDTO;
import com.management.managementapi.enterprises.dto.media.EnterpriseMediaDTO;
import com.management.managementapi.enterprises.mapper.EnterpriseMapper;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.EnterprisesLocation;
import com.management.managementapi.enterprises.model.EnterprisesMedia;
import com.management.managementapi.enterprises.repository.EnterpriseLocationRepository;
import com.management.managementapi.enterprises.repository.EnterpriseMediaRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.mapper.common.LocationMapper;
import com.management.managementapi.model.Location;
import com.management.managementapi.repository.LocationRepository;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.CurrentAuditor;

import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.model.enums.MediaType;
import com.management.managementapi.model.enums.Visibility;
import com.management.managementapi.model.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.management.managementapi.enterprises.mapper.EnterprisesMediaMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseService {

    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseMapper enterpriseMapper;
    private final LocationRepository locationRepository;
    private final EnterpriseLocationRepository enterpriseLocationRepository;
    private final LocationMapper locationMapper;
    private final EnterpriseMediaRepository enterpriseMediaRepository;
    private final CurrentAuditor currentAuditor;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storageService;
    private final EnterprisesMediaMapper enterprisesMediaMapper;

    /**
     * Obter dados básicos de um projeto (para seleção/dropdown)
     */
    @Transactional(readOnly = true)
    public EnterpriseBasicDTO findBasicById(@NonNull UUID id) {
        log.info("Fetching basic enterprise data with id: {}", id);

        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(id.toString()));

        EnterpriseBasicDTO dto = enterpriseMapper.toBasicDTO(enterprise);

        if (dto.getBanner() != null) {
            dto.setBannerUrl("/api/media/download/media/" + dto.getBanner());
        }

        return dto;
    }

    /**
     * Listar dados básicos de todos os projetos ativos (para seleção/dropdown)
     */
    @Transactional(readOnly = true)
    public List<EnterpriseBasicDTO> findAllBasic() {
        log.info("Fetching all basic enterprise data");

        List<Enterprise> enterprises = enterpriseRepository.findByIsActiveTrue();

        if (enterprises.isEmpty()) {
            return List.of();
        }

        List<EnterpriseBasicDTO> dtos = enterpriseMapper.toBasicDTOList(enterprises);

        dtos.forEach(dto -> {
            if (dto.getBanner() != null) {
                dto.setBannerUrl("/api/media/download/media/" + dto.getBanner());
            }
        });

        log.info("Fetched {} enterprises", dtos.size());
        return dtos;
    }

    @Transactional(readOnly = true)
    public Page<EnterpriseListDTO> findAll(@NonNull Pageable pageable, @Nullable String search) {
        Page<Enterprise> enterprises;

        if (search != null && !search.trim().isEmpty()) {
            enterprises = enterpriseRepository.findByIsActiveTrueAndNameContaining(search.trim(), pageable);
        } else {
            enterprises = enterpriseRepository.findByIsActiveTrue(pageable);
        }

        // Buscar localizações para todas as enterprises da página
        List<UUID> enterpriseIds = enterprises.getContent().stream()
                .map(Enterprise::getId)
                .collect(Collectors.toList());

        // Use JOIN FETCH para evitar LazyInitializationException
        List<EnterprisesLocation> enterpriseLocations = enterpriseLocationRepository
                .findByEnterpriseIdInWithLocation(enterpriseIds);

        Map<UUID, EnterprisesLocation> locationMap = enterpriseLocations.stream()
                .collect(Collectors.toMap(
                        el -> el.getEnterprise().getId(),
                        Function.identity()));

        // Mapear para DTO com localização
        return enterprises.map(enterprise -> {
            EnterpriseListDTO dto = enterpriseMapper.toListDTO(enterprise);

            EnterprisesLocation location = locationMap.get(enterprise.getId());
            if (location != null) {
                EnterpriseLocationListDTO locationDTO = enterpriseMapper.toEnterpriseLocationListDTO(location);
                dto.setLocation(locationDTO);
            }

            return dto;
        });
    }

    // MOSTRAR TODOS OS PROJETOS ATIVOS
    @Transactional(readOnly = true)
    public List<EnterpriseResponseDTO> findAllActive() {
        log.info("Fetching all active enterprises");
        List<Enterprise> enterprises = enterpriseRepository.findByIsActiveTrue();
        return enterpriseMapper.toResponseDTOList(enterprises);
    }

    @Transactional(readOnly = true)
    public EnterpriseFullResponseDTO findById(@NonNull UUID id) {
        log.info("Fetching enterprise with id: {}", id);

        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(id.toString()));

        LocationResponseDTO locationDTO = null;
        Optional<EnterprisesLocation> enterpriseLocation = enterpriseLocationRepository.findByEnterpriseId(id);
        if (enterpriseLocation.isPresent()) {
            Location location = enterpriseLocation.get().getLocation();
            locationDTO = mapLocationToDTO(location);
        }

        List<MediaResponseDTO> mediaDTOs = enterpriseMediaRepository
                .findByEnterpriseId(id)
                .stream()
                .map(media -> {
                    MediaResponseDTO dto = enterprisesMediaMapper.toResponse(media);
                    dto.setDownloadUrl(resolveSignedUrl(media));
                    return dto;
                })
                .collect(Collectors.toList());

        String createdByName = null;
        if (enterprise.getCreatedBy() != null) {
            createdByName = profileRepository.findById(java.util.Objects.requireNonNull(enterprise.getCreatedBy(), "createdBy"))
                    .map(Profile::getName)
                    .orElse(null);
        }

        EnterpriseFullResponseDTO enterpriseDTO = enterpriseMapper.toDTOFull(enterprise);
        enterpriseDTO.setLocation(locationDTO);
        enterpriseDTO.setMedia(mediaDTOs);
        enterpriseDTO.setCreatedbyName(createdByName);

        String bannerSignedUrl = enterpriseMediaRepository
                .findByEnterpriseId(id)
                .stream()
                .filter(media -> media.getType() == MediaType.banner)
                .findFirst()
                .map(this::resolveSignedUrl)
                .orElse(null);

        enterpriseDTO.setBanner(bannerSignedUrl);

        log.info("Enterprise {} carregado com {} media files", id, mediaDTOs.size());

        return enterpriseDTO;
    }

    // CRIAR UM NOVO PROJETO
    @Transactional
    public EnterpriseDTO create(CreateEnterpriseDTO createEnterpriseDTO) {
        log.info("Creating new enterprise: {}", createEnterpriseDTO.getName());

        if (createEnterpriseDTO.getInternalReference() != null &&
                enterpriseRepository.existsByInternalReference(createEnterpriseDTO.getInternalReference())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Enterprise with internal reference already exists: " + createEnterpriseDTO.getInternalReference());
        }

        Enterprise enterprise = enterpriseMapper.toEntity(createEnterpriseDTO);

        var user = currentAuditor.getCurrentProfile()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No authenticated user found"));
        enterprise.setCreatedBy(user.getId());

        Enterprise savedEnterprise = Objects.requireNonNull(
            enterpriseRepository.save(enterprise), "saved enterprise must not be null");

        UUID savedEnterpriseId = Objects.requireNonNull(savedEnterprise.getId(), "saved enterprise id must not be null");

        // Lógica para localização
        if (createEnterpriseDTO.getExistingLocationId() != null || createEnterpriseDTO.getNewLocation() != null) {
            addLocationToEnterprise(
                    savedEnterpriseId,
                    createEnterpriseDTO.getExistingLocationId(),
                    createEnterpriseDTO.getNewLocation());
        }

        if (createEnterpriseDTO.getMedia() != null && !createEnterpriseDTO.getMedia().isEmpty()) {
            log.info("Processing {} media items for enterprise", createEnterpriseDTO.getMedia().size());
            addMedia(savedEnterpriseId, createEnterpriseDTO.getMedia());
        } else {
            log.info("No media items to process for enterprise");
        }

        log.info("Enterprise created successfully with id: {}", savedEnterpriseId);
        return enterpriseMapper.toDTO(savedEnterprise);
    }

    @Transactional
    public void addLocationToEnterprise(@NonNull UUID enterpriseId, @Nullable UUID existingLocationId,
            @Nullable LocationUpsertDTO newLocationData) {
        log.info("Adding location to enterprise: {}", enterpriseId);

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        UUID locationId;

        if (existingLocationId != null) {
            locationRepository.findById(existingLocationId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Location not found with id: " + existingLocationId));
            locationId = existingLocationId;
        } else if (newLocationData != null) {
            Location newLocation = Objects.requireNonNull(
                locationMapper.toEntity(newLocationData), "mapped location must not be null");
            Location savedLocation = locationRepository.save(newLocation);
            locationId = Objects.requireNonNull(savedLocation.getId(), "saved location id must not be null");
            log.info("New location created with id: {}", locationId);
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Either existingLocationId or newLocation must be provided");
        }

        Optional<EnterprisesLocation> existingEnterpriseLocation = enterpriseLocationRepository
                .findByEnterpriseId(enterpriseId);
        if (existingEnterpriseLocation.isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Enterprise already has a location");
        }

        EnterprisesLocation enterprisesLocation = new EnterprisesLocation();
        enterprisesLocation.setEnterprise(enterprise);
        enterprisesLocation.setLocation(new Location());
        enterprisesLocation.getLocation().setId(locationId);
        enterprisesLocation.setIsPrimary(true);

        enterpriseLocationRepository.save(enterprisesLocation);
        log.info("Location association created for enterprise: {}", enterpriseId);
    }

    @Transactional
    public void addMedia(@NonNull UUID enterpriseId, List<EnterpriseMediaDTO> mediaList) {
        log.info("Adding {} media items to enterprise: {}", mediaList.size(), enterpriseId);

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        String bannerStorageKey = null;

        UUID logedUserId = currentAuditor.getCurrentAuditor()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No authenticated user found"));
        var profile = profileRepository.findById(java.util.Objects.requireNonNull(logedUserId, "logedUserId"))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        for (EnterpriseMediaDTO mediaDTO : mediaList) {
            if (mediaDTO.getFileContent() == null) {
                log.warn("MediaDTO sem fileContent, skipping: {}", mediaDTO.getType());
                continue;
            }

            String storageKey = generateStorageKey(enterpriseId, mediaDTO.getType());

            try {
                storageService.upload(
                    "media",
                    storageKey,
                    mediaDTO.getMimeType(),
                    mediaDTO.getFileContent()
                );

                log.info("Upload realizado com sucesso para: {}", storageKey);

            } catch (IOException e) {
                log.error("Erro ao fazer upload para Supabase: {}", storageKey, e);
                throw StorageException.uploadError(storageKey, e);
            }

            EnterprisesMedia media = EnterprisesMedia.builder()
                    .enterprise(enterprise)
                    .type(mediaDTO.getType())
                    .storageKey(storageKey)
                    .altText(mediaDTO.getAltText())
                    .sortOrder(mediaDTO.getSortOrder() != null ? mediaDTO.getSortOrder() : 0)
                    .bucket("media")
                    .mimeType(mediaDTO.getMimeType())
                    .fileSizeBytes(mediaDTO.getFileSizeBytes())
                    .width(mediaDTO.getWidth())
                    .height(mediaDTO.getHeight())
                    .durationMs(mediaDTO.getDurationMs())
                    .checksumSha256(mediaDTO.getChecksumSha256())
                    .visibility(mediaDTO.getVisibility() != null ? mediaDTO.getVisibility() : Visibility.PRIVATE)
                    .uploadedBy(profile)
                    .build();

            enterpriseMediaRepository.save(Objects.requireNonNull(media, "media must not be null"));

            if (mediaDTO.getType() == MediaType.banner) {
                bannerStorageKey = storageKey;
            }
        }

        if (bannerStorageKey != null) {
            enterprise.setBanner(bannerStorageKey);
            Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise must not be null");
            log.info("Banner updated for enterprise: {} with storage key: {}", enterpriseId, bannerStorageKey);
        }

        log.info("Media added successfully to enterprise: {}", enterpriseId);
    }

    private String generateStorageKey(UUID enterpriseId, MediaType mediaType) {
        String randomId = UUID.randomUUID().toString();
        return String.format("enterprise/%s/%s/%s", enterpriseId, mediaType.toString().toLowerCase(), randomId);
    }

    @Transactional
    public EnterpriseDTO update(@NonNull UUID id, EnterpriseDTO enterpriseDTO) {
        log.info("Updating enterprise with id: {}", id);

        Enterprise existingEnterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(id.toString()));

        enterpriseMapper.updateEntityFromDTO(enterpriseDTO, existingEnterprise);
        Enterprise updatedEnterprise = enterpriseRepository.save(
            Objects.requireNonNull(existingEnterprise, "existingEnterprise must not be null"));

        log.info("Enterprise updated successfully with id: {}", id);
        return enterpriseMapper.toDTO(updatedEnterprise);
    }

    @Transactional
    public void delete(@NonNull UUID id) {
        log.info("Soft deleting enterprise with id: {}", id);

        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(id.toString()));

        enterprise.setIsActive(false);
        enterprise.setStatus(com.management.managementapi.enterprises.model.enums.EnterPriseStatus.deleted);
        enterpriseRepository.save(enterprise);

        log.info("Enterprise soft deleted successfully with id: {}", id);
    }

    @Transactional(readOnly = true)
    public List<EnterpriseDTO> findByName(@NonNull String name) {
        log.info("Searching enterprises by name: {}", name);
        List<Enterprise> enterprises = enterpriseRepository.findActiveByNameContaining(name);
        return enterpriseMapper.toDTOList(enterprises);
    }

    private LocationResponseDTO mapLocationToDTO(Location location) {
        if (location == null)
            return null;

        LocationResponseDTO dto = new LocationResponseDTO();
        dto.setId(location.getId());
        dto.setAddressLine1(location.getAddressLine1());
        dto.setAddressLine2(location.getAddressLine2());
        dto.setPostalCode(location.getPostalCode());
        dto.setCity(location.getCity());
        dto.setParish(location.getParish());
        dto.setMunicipality(location.getMunicipality());
        dto.setCountry(location.getCountry());
        dto.setLatitude(location.getLatitude());
        dto.setLongitude(location.getLongitude());
        dto.setGooglePlaceId(location.getGooglePlaceId());
        dto.setNotes(location.getNotes());

        return dto;
    }

    private MediaResponseDTO mapMediaToDTO(EnterprisesMedia media) {
        if (media == null)
            return null;

        String downloadUrl = generateDownloadUrl(media.getBucket(), media.getStorageKey());

        return new MediaResponseDTO(
                media.getId(),
                media.getType().toString(),
                media.getBucket(),
                media.getStorageKey(),
                media.getMimeType(),
                media.getFileSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getDurationMs(),
                media.getAltText(),
                media.getSortOrder(),
                downloadUrl);
    }

    private String generateDownloadUrl(String bucket, String storageKey) {
        return "/api/media/download/" + bucket + "/" + storageKey;
    }

    // =================== MÉTODOS PARA GALERIA ===================

    /**
     * Adicionar múltiplas fotos à galeria de um projeto
     */
    @Transactional
    public List<MediaResponseDTO> addPhotosToGallery(@NonNull UUID enterpriseId, List<MultipartFile> photos) {
        log.info("Adding {} photos to enterprise gallery: {}", photos.size(), enterpriseId);

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        UUID loggedUserId = currentAuditor.getCurrentAuditor()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No authenticated user found"));
        var profile = profileRepository.findById(java.util.Objects.requireNonNull(loggedUserId, "loggedUserId"))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        List<MediaResponseDTO> uploadedMedia = new ArrayList<>();

        for (int i = 0; i < photos.size(); i++) {
            MultipartFile photo = photos.get(i);

            String storageKey = generateStorageKey(enterpriseId, MediaType.image);

            try (InputStream inputStream = photo.getInputStream()) {
                String bucket = "media";
                storageService.upload(bucket, storageKey, photo.getContentType(), inputStream);
                log.info("Photo uploaded to storage: {}", storageKey);
            } catch (IOException e) {
                throw StorageException.uploadError(storageKey, e);
            }

            EnterprisesMedia media = EnterprisesMedia.builder()
                    .enterprise(enterprise)
                    .type(MediaType.image)
                    .storageKey(storageKey)
                    .altText(photo.getOriginalFilename())
                    .sortOrder(i)
                    .bucket("media")
                    .mimeType(photo.getContentType())
                    .fileSizeBytes(photo.getSize())
                    .visibility(Visibility.PUBLIC)
                    .uploadedBy(profile)
                    .build();

            EnterprisesMedia savedMedia = enterpriseMediaRepository.save(
                Objects.requireNonNull(media, "media must not be null"));

            uploadedMedia.add(mapMediaToDTO(savedMedia));
        }

        log.info("Successfully added {} photos to enterprise gallery", photos.size());
        return uploadedMedia;
    }

    /**
     * Upload ou atualizar o banner de um projeto
     */
    @Transactional
    public EnterpriseFullResponseDTO uploadOrUpdateBanner(@NonNull UUID enterpriseId, MultipartFile bannerFile) {
        log.info("Uploading/updating banner for enterprise: {}", enterpriseId);

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        UUID loggedUserId = currentAuditor.getCurrentAuditor()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No authenticated user found"));
        var profile = profileRepository.findById(java.util.Objects.requireNonNull(loggedUserId, "loggedUserId"))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));

        List<EnterprisesMedia> existingBanners = enterpriseMediaRepository
                .findByEnterpriseIdAndType(enterpriseId, MediaType.banner);

        if (!existingBanners.isEmpty()) {
            for (EnterprisesMedia oldBanner : existingBanners) {
                try {
                    storageService.delete(oldBanner.getBucket(), oldBanner.getStorageKey());
                    log.info("Deleted old banner from storage: {}", oldBanner.getStorageKey());
                } catch (IOException e) {
                    log.warn("Failed to delete old banner from storage: {}", e.getMessage());
                }
                enterpriseMediaRepository.delete(oldBanner);
            }
        }

        String storageKey = generateStorageKey(enterpriseId, MediaType.banner);

        try (InputStream inputStream = bannerFile.getInputStream()) {
            String bucket = "media";
            storageService.upload(bucket, storageKey, bannerFile.getContentType(), inputStream);
            log.info("New banner uploaded to storage: {}", storageKey);
        } catch (IOException e) {
            throw StorageException.uploadError(storageKey, e);
        }

        EnterprisesMedia bannerMedia = EnterprisesMedia.builder()
                .enterprise(enterprise)
                .type(MediaType.banner)
                .storageKey(storageKey)
                .altText("Banner image")
                .sortOrder(0)
                .bucket("media")
                .mimeType(bannerFile.getContentType())
                .fileSizeBytes(bannerFile.getSize())
                .visibility(Visibility.PUBLIC)
                .uploadedBy(profile)
                .build();

        enterpriseMediaRepository.save(Objects.requireNonNull(bannerMedia, "bannerMedia must not be null"));

        enterprise.setBanner(storageKey);
        Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise must not be null");

        log.info("Banner updated successfully for enterprise: {}", enterpriseId);

        return findById(enterpriseId);
    }

    /**
     * Eliminar o banner de um projeto
     */
    @Transactional
    public EnterpriseFullResponseDTO deleteBanner(@NonNull UUID enterpriseId) {
        log.info("Deleting banner for enterprise: {}", enterpriseId);

        enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        List<EnterprisesMedia> banners = enterpriseMediaRepository
                .findByEnterpriseIdAndType(enterpriseId, MediaType.banner);

        if (banners.isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.MEDIA_NOT_FOUND, "No banner found for enterprise: " + enterpriseId);
        }

        for (EnterprisesMedia banner : banners) {
            try {
                storageService.delete(banner.getBucket(), banner.getStorageKey());
                log.info("Deleted banner from storage: {}", banner.getStorageKey());
            } catch (IOException e) {
                log.warn("Failed to delete banner from storage: {}", e.getMessage());
            }
            enterpriseMediaRepository.delete(banner);
        }

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));
        enterprise.setBanner(null);
        Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise must not be null");

        log.info("Banner deleted successfully for enterprise: {}", enterpriseId);

        return findById(enterpriseId);
    }

    /**
     * Atualizar o altText de uma media
     */
    @Transactional
    public MediaResponseDTO updateMediaAltText(@NonNull UUID enterpriseId, @NonNull UUID mediaId, String altText) {
        log.info("Updating altText for media {} in enterprise {}", mediaId, enterpriseId);

        EnterprisesMedia media = enterpriseMediaRepository.findById(mediaId)
                .orElseThrow(() -> ResourceNotFoundException.media(mediaId.toString()));

        if (!media.getEnterprise().getId().equals(enterpriseId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Media does not belong to enterprise: " + enterpriseId);
        }

        media.setAltText(altText);
        EnterprisesMedia updatedMedia = Objects.requireNonNull(
            enterpriseMediaRepository.save(media), "saved media must not be null");

        log.info("AltText updated successfully for media: {}", mediaId);
        return mapMediaToDTO(updatedMedia);
    }

    /**
     * Eliminar uma media específica
     */
    @Transactional
    public void deleteMedia(@NonNull UUID enterpriseId, @NonNull UUID mediaId) {
        log.info("Deleting media {} from enterprise {}", mediaId, enterpriseId);

        EnterprisesMedia media = enterpriseMediaRepository.findById(mediaId)
                .orElseThrow(() -> ResourceNotFoundException.media(mediaId.toString()));

        if (!media.getEnterprise().getId().equals(enterpriseId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Media does not belong to enterprise: " + enterpriseId);
        }

        try {
            storageService.delete(media.getBucket(), media.getStorageKey());
            log.info("Deleted media from storage: {}", media.getStorageKey());
        } catch (IOException e) {
            log.warn("Failed to delete media from storage: {}", e.getMessage());
            throw StorageException.deleteError(media.getStorageKey(), e);
        }

        enterpriseMediaRepository.delete(media);

        log.info("Media deleted successfully: {}", mediaId);
    }

    // =================== FIM DOS MÉTODOS PARA GALERIA ===================

    private String resolveSignedUrl(EnterprisesMedia media) {
        try {
            if (storageService == null) {
                log.error("Storage service está NULL! Verifica injeção de dependências!");
                return null;
            }

            return storageService.createSignedUrl(media.getBucket(), media.getStorageKey(), 3600);

        } catch (IOException e) {
            log.error("Erro ao assinar media {}: {}", media.getId(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Erro inesperado ao assinar media {}: {}", media.getId(), e.getMessage(), e);
            return null;
        }
    }
}
