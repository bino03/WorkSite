package com.management.managementapi.enterprises.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.management.managementapi.dto.common.media.MediaResponseDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.enterprises.dto.enterprise.CreateEnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.EnterpriseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseBasicDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseFullResponseDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseListDTO;
import com.management.managementapi.enterprises.dto.enterprise.response.EnterpriseResponseDTO;
import com.management.managementapi.enterprises.dto.media.EnterpriseMediaDTO;
import com.management.managementapi.enterprises.service.EnterpriseService;

import jakarta.validation.Valid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.NonNull;

@Slf4j
@RestController
@RequestMapping("/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    @GetMapping
    public ResponseEntity<Page<EnterpriseListDTO>> getAllEnterprises(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size);
        Page<EnterpriseListDTO> enterprises = enterpriseService.findAll(pageable, q);
        return ResponseEntity.ok(enterprises);
    }

    @GetMapping("/active")
    public ResponseEntity<List<EnterpriseResponseDTO>> getActiveEnterprises() {
        List<EnterpriseResponseDTO> enterprises = enterpriseService.findAllActive();
        return ResponseEntity.ok(enterprises);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnterpriseFullResponseDTO> getEnterpriseById(@PathVariable @NonNull UUID id) {
        EnterpriseFullResponseDTO enterprise = enterpriseService.findById(id);
        return ResponseEntity.ok(enterprise);
    }

    /**
     * Obter dados básicos de um projeto por ID
     * Útil para dropdowns e seleção de projeto
     */
    @GetMapping("/{id}/basic")
    public ResponseEntity<EnterpriseBasicDTO> getEnterpriseBasicById(@PathVariable @NonNull UUID id) {
        EnterpriseBasicDTO enterprise = enterpriseService.findBasicById(id);
        return ResponseEntity.ok(enterprise);
    }

    /**
     * Listar dados básicos de todos os projetos ativos
     * Útil para dropdowns e seleção de projeto
     */
    @GetMapping("/basic")
    public ResponseEntity<List<EnterpriseBasicDTO>> getAllBasicEnterprises() {
        List<EnterpriseBasicDTO> enterprises = enterpriseService.findAllBasic();
        return ResponseEntity.ok(enterprises);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EnterpriseDTO> createEnterprise(
            @RequestPart("enterprise") String enterpriseJson,
            @RequestPart(value = "banner", required = false) MultipartFile bannerFile,
            @RequestPart(value = "gallery", required = false) List<MultipartFile> galleryFiles,
            @RequestPart(value = "galleryTypes", required = false) List<String> galleryTypes,
            @RequestPart(value = "galleryAltTexts", required = false) List<String> galleryAltTexts,
            @RequestPart(value = "gallerySortOrders", required = false) List<Integer> gallerySortOrders) {

        CreateEnterpriseDTO createEnterpriseDTO;
        try {
            // Converter JSON string para CreateEnterpriseDTO com suporte para Java 8 dates
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            createEnterpriseDTO = objectMapper.readValue(enterpriseJson, CreateEnterpriseDTO.class);
        } catch (Exception e) {
            log.error("Error parsing enterprise JSON", e);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "JSON da enterprise inválido: " + e.getMessage());
        }

        // Processar ficheiros e adicionar ao DTO
        processMediaFiles(createEnterpriseDTO, bannerFile, galleryFiles, galleryTypes, galleryAltTexts,
                gallerySortOrders);

        EnterpriseDTO createdEnterprise = enterpriseService.create(createEnterpriseDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEnterprise);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnterpriseDTO> updateEnterprise(
            @PathVariable @NonNull UUID id,
            @Valid @RequestBody EnterpriseDTO enterpriseDTO) {
        EnterpriseDTO updatedEnterprise = enterpriseService.update(id, enterpriseDTO);
        return ResponseEntity.ok(updatedEnterprise);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEnterprise(@PathVariable @NonNull UUID id) {
        enterpriseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<EnterpriseDTO>> searchEnterprisesByName(@RequestParam @NonNull String name) {
        List<EnterpriseDTO> enterprises = enterpriseService.findByName(name);
        return ResponseEntity.ok(enterprises);
    }

    private void processMediaFiles(CreateEnterpriseDTO createEnterpriseDTO,
            MultipartFile bannerFile,
            List<MultipartFile> galleryFiles,
            List<String> galleryTypes,
            List<String> galleryAltTexts,
            List<Integer> gallerySortOrders) {

        List<EnterpriseMediaDTO> mediaList = new ArrayList<>();

        // Processar banner
        if (bannerFile != null && !bannerFile.isEmpty()) {
            EnterpriseMediaDTO bannerMedia = new EnterpriseMediaDTO();
            bannerMedia.setType(com.management.managementapi.model.enums.MediaType.banner);
            bannerMedia.setMimeType(bannerFile.getContentType());
            bannerMedia.setFileSizeBytes(bannerFile.getSize());
            try {
                bannerMedia.setFileContent(bannerFile.getInputStream());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.FILE_READ_ERROR, "Erro ao ler ficheiro banner: " + e.getMessage());
            }
            mediaList.add(bannerMedia);
        }

        // Processar galeria
        if (galleryFiles != null) {
            for (int i = 0; i < galleryFiles.size(); i++) {
                MultipartFile file = galleryFiles.get(i);
                if (file != null && !file.isEmpty()) {
                    EnterpriseMediaDTO galleryMedia = new EnterpriseMediaDTO();
                    galleryMedia
                            .setType(com.management.managementapi.model.enums.MediaType.valueOf(galleryTypes.get(i)));
                    galleryMedia.setMimeType(file.getContentType());
                    galleryMedia.setFileSizeBytes(file.getSize());
                    galleryMedia.setAltText(galleryAltTexts.get(i));
                    galleryMedia.setSortOrder(gallerySortOrders.get(i));
                    try {
                        galleryMedia.setFileContent(file.getInputStream());
                    } catch (IOException e) {
                        throw new BusinessException(ErrorCode.FILE_READ_ERROR, "Erro ao ler ficheiro de galeria: " + e.getMessage());
                    }
                    mediaList.add(galleryMedia);
                }
            }
        }

        createEnterpriseDTO.setMedia(mediaList);
    }

    // =================== ENDPOINTS PARA GALERIA ===================

    /**
     * Adicionar múltiplas fotos à galeria
     */
    @PostMapping("/{id}/addPhotos")
    public ResponseEntity<List<MediaResponseDTO>> addPhotosToGallery(
            @PathVariable @NonNull UUID id,
            @RequestParam("photos") List<MultipartFile> photos) {
        List<MediaResponseDTO> uploadedPhotos = enterpriseService.addPhotosToGallery(id, photos);
        return ResponseEntity.ok(uploadedPhotos);
    }

    /**
     * Upload ou atualizar banner
     */
    @PostMapping("/{id}/photos/banner")
    public ResponseEntity<EnterpriseFullResponseDTO> uploadOrUpdateBanner(
            @PathVariable @NonNull UUID id,
            @RequestParam("banner") MultipartFile bannerFile) {
        EnterpriseFullResponseDTO updatedEnterprise = enterpriseService.uploadOrUpdateBanner(id, bannerFile);
        return ResponseEntity.ok(updatedEnterprise);
    }

    /**
     * Eliminar banner
     */
    @DeleteMapping("/{id}/photos/banner")
    public ResponseEntity<EnterpriseFullResponseDTO> deleteBanner(@PathVariable @NonNull UUID id) {
        EnterpriseFullResponseDTO updatedEnterprise = enterpriseService.deleteBanner(id);
        return ResponseEntity.ok(updatedEnterprise);
    }

    /**
     * Atualizar altText de uma media
     */
    @PatchMapping("/{id}/media/{mediaId}")
    public ResponseEntity<MediaResponseDTO> updateMediaAltText(
            @PathVariable @NonNull UUID id,
            @PathVariable @NonNull UUID mediaId,
            @RequestBody Map<String, String> body) {
        String altText = body.get("altText");
        MediaResponseDTO updatedMedia = enterpriseService.updateMediaAltText(id, mediaId, altText);
        return ResponseEntity.ok(updatedMedia);
    }

    /**
     * Eliminar uma media específica
     */
    @DeleteMapping("/{id}/media/{mediaId}")
    public ResponseEntity<Void> deleteMedia(
            @PathVariable @NonNull UUID id,
            @PathVariable @NonNull UUID mediaId) {
        enterpriseService.deleteMedia(id, mediaId);
        return ResponseEntity.noContent().build();
    }

    // =================== FIM DOS ENDPOINTS PARA GALERIA ===================

}
