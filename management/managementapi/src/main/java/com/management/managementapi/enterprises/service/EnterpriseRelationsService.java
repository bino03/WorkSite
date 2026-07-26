package com.management.managementapi.enterprises.service;
// TRATAMENTOS DE ERROS ✅✅

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.enterprises.dto.enterprise.EnterpriseLocationDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.DatesAndAreasCardDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.EditOverViewCardDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.FinanceCardDTO;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.EnterprisesLocation;
import com.management.managementapi.enterprises.model.EnterprisesMedia;
import com.management.managementapi.enterprises.repository.EnterpriseLocationRepository;
import com.management.managementapi.enterprises.repository.EnterpriseMediaRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Location;
import com.management.managementapi.repository.LocationRepository;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.dto.error.ErrorCode;
import java.util.Objects;
import org.springframework.lang.NonNull;

@Service
@Transactional
@Slf4j
public class EnterpriseRelationsService {

    private final EnterpriseRepository enterpriseRepository;
    private final EnterpriseLocationRepository enterpriseLocationRepository;
    private LocationRepository locationRepository;
    private final EnterpriseMediaRepository enterprisesMediaRepository;
    private final SupabaseStorageService storageService;

    public EnterpriseRelationsService(EnterpriseRepository enterpriseRepository,
            EnterpriseLocationRepository enterpriseLocationRepository,
            LocationRepository locationRepository,
            EnterpriseMediaRepository enterprisesMediaRepository,
            SupabaseStorageService storageService) {
        this.enterpriseLocationRepository = enterpriseLocationRepository;
        this.enterpriseRepository = enterpriseRepository;
        this.locationRepository = locationRepository;
        this.enterprisesMediaRepository = enterprisesMediaRepository;
        this.storageService = storageService;

    }

    @SuppressWarnings("null")
    public EnterprisesLocation addOrUpdateEnterpriseLocation(Enterprise enterprise, EnterprisesLocation location) {
        var existingLocationOpt = enterpriseLocationRepository.findByEnterpriseId(enterprise.getId());

        if (existingLocationOpt.isEmpty()) {
            // Cria nova localização
            EnterprisesLocation newLocation = EnterprisesLocation.builder()
                    .enterprise(enterprise)
                    .location(location.getLocation())
                    .isPrimary(location.getIsPrimary())
                    .sortOrder(location.getSortOrder())
                    .notes(location.getNotes())
                    .build();
            return Objects.requireNonNull(enterpriseLocationRepository.save(newLocation), "saved enterpriseLocation");
        } else {
            // Atualiza localização existente
            EnterprisesLocation existingLocation = existingLocationOpt.get();
            existingLocation.setLocation(location.getLocation());
            existingLocation.setIsPrimary(location.getIsPrimary());
            existingLocation.setSortOrder(location.getSortOrder());
            existingLocation.setNotes(location.getNotes());
            return Objects.requireNonNull(enterpriseLocationRepository.save(existingLocation), "saved enterpriseLocation");
        }
    }

     private Location createLocationFromDTO(LocationUpsertDTO dto) {
        Location location = new Location();
        location.setAddressLine1(dto.getAddressLine1());
        location.setAddressLine2(dto.getAddressLine2());
        location.setCity(dto.getCity());
        location.setPostalCode(dto.getPostalCode());
        location.setCountry(dto.getCountry());
        location.setLatitude(dto.getLatitude());
        location.setLongitude(dto.getLongitude());
        location.setParish(dto.getParish());
        location.setMunicipality(dto.getMunicipality());
        return location;
    }

    // MÉTODO UPSERT LOCATION - ATUALIZADO E CORRIGIDO
    @Transactional
    @SuppressWarnings("null")
public EnterpriseLocationDTO upsertLocation(@NonNull UUID enterpriseId, LocationUpsertDTO dto) {
    log.info("Upserting location for enterprise: {}", enterpriseId);

    // Buscar a empresa
    Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
            .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

    // Verificar se já existe localização para esta enterprise
    Optional<EnterprisesLocation> existingEnterpriseLocation = enterpriseLocationRepository
            .findByEnterpriseId(enterpriseId);

    Location locationToUse;

    // CASO 1: DTO tem ID - usar localização existente do sistema
    if (dto.getId() != null) {
        log.info("Using existing location with id: {}", dto.getId());
        locationToUse = locationRepository.findById(Objects.requireNonNull(dto.getId(), "location id"))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Location not found with id: " + dto.getId()));
    } 
    // CASO 2: DTO não tem ID - criar nova localização
    else {
        log.info("Creating new location");
        locationToUse = createLocationFromDTO(dto);
        locationToUse = Objects.requireNonNull(locationRepository.save(locationToUse), "saved location");
    }

    EnterprisesLocation enterpriseLocation;

    if (existingEnterpriseLocation.isPresent()) {
        // Atualizar associação existente
        enterpriseLocation = existingEnterpriseLocation.get();
        enterpriseLocation.setLocation(locationToUse);
        log.info("Updated existing enterprise-location association for enterprise: {}", enterpriseId);
    } else {
        // Criar nova associação
        enterpriseLocation = EnterprisesLocation.builder()
                .enterprise(enterprise)
                .location(locationToUse)
                .isPrimary(true)
                .sortOrder(0)
                .build();
        log.info("Created new enterprise-location association for enterprise: {}", enterpriseId);
    }

    EnterprisesLocation saved = Objects.requireNonNull(enterpriseLocationRepository.save(enterpriseLocation), "saved enterpriseLocation");
    
    // Converter para DTO e retornar
    return convertToEnterpriseLocationDTO(saved);
}

// Método helper para converter EnterprisesLocation para DTO
private EnterpriseLocationDTO convertToEnterpriseLocationDTO(EnterprisesLocation enterpriseLocation) {
    return EnterpriseLocationDTO.builder()
            .id(enterpriseLocation.getId())
            .enterpriseId(enterpriseLocation.getEnterprise().getId())
            .location(enterpriseLocation.getLocation())
            .isPrimary(enterpriseLocation.getIsPrimary())
            .sortOrder(enterpriseLocation.getSortOrder())
            .notes(enterpriseLocation.getNotes())
            .createdAt(enterpriseLocation.getCreatedAt())
            .updatedAt(enterpriseLocation.getUpdatedAt())
            .build();
}


    // Método adicional útil - buscar localização por empresa
    public EnterprisesLocation getLocationByEnterpriseId(@NonNull UUID enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        return enterpriseLocationRepository.findByEnterpriseId(enterprise.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Location not found for enterprise id: " + enterpriseId));
    }

    // Método para remover localização
    public void removeLocationFromEnterprise(@NonNull UUID enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        var existingLocationOpt = enterpriseLocationRepository.findByEnterpriseId(enterprise.getId());
        existingLocationOpt.ifPresent(location -> enterpriseLocationRepository.deleteById(java.util.Objects.requireNonNull(location.getId(), "locationId")));
    }

    // REMOVER A ASSOCIAÇÃO ENTRE UMA ENTERPRISE E UMA LOCALIZAÇÃO
    public void removeLocation(@NonNull UUID enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> ResourceNotFoundException.enterprise(enterpriseId.toString()));

        enterpriseLocationRepository.findByEnterpriseId(enterprise.getId())
                .ifPresent(enterpriseLocationRepository::delete);
    }



    // EDITOVERVIEW CARD
    @SuppressWarnings("null")
public EditOverViewCardDTO updateOverview(@NonNull UUID id, EditOverViewCardDTO dto) {
        // Buscar a empresa pelo ID
        Enterprise enterprise = enterpriseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Enterprise not found with id: " + id));

        // Aplicar as alterações parciais (PATCH)
        if (dto.getName() != null) {
            enterprise.setName(dto.getName());
        }
        if (dto.getInternalReference() != null) {
            enterprise.setInternalReference(dto.getInternalReference());
        }
        if (dto.getType() != null) {
            enterprise.setType(dto.getType());
        }
        if (dto.getStatus() != null) {
            enterprise.setStatus(dto.getStatus());
        }

        // Salvar a entidade atualizada
        Enterprise updatedEnterprise = Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise");

        // Converter a entidade para DTO e retornar
        return convertToDTO(updatedEnterprise);
    }

    private EditOverViewCardDTO convertToDTO(Enterprise enterprise) {
        EditOverViewCardDTO dto = new EditOverViewCardDTO();
        dto.setId(enterprise.getId());
        dto.setName(enterprise.getName());
        dto.setInternalReference(enterprise.getInternalReference());
        dto.setType(enterprise.getType());
        dto.setStatus(enterprise.getStatus());
        return dto;
    }


    @SuppressWarnings("null")
    public DatesAndAreasCardDTO updateDatesAndAreas(@NonNull UUID id, DatesAndAreasCardDTO dto) {
    // Buscar a empresa pelo ID
    Enterprise enterprise = enterpriseRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Enterprise not found with id: " + id));

    // Aplicar as alterações parciais (PATCH)
    if (dto.getLandArea() != null) {
        enterprise.setLandArea(dto.getLandArea());
    }
    if (dto.getCompletionDate() != null) {
        enterprise.setCompletionDate(dto.getCompletionDate());
    }
    if (dto.getStartDate() != null) {
        enterprise.setStartDate(dto.getStartDate());
    }
    if(dto.getTotalArea() != null)
    {
        enterprise.setTotalArea(dto.getTotalArea());
    }

    // Salvar a entidade atualizada
    Enterprise updatedEnterprise = Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise");

    // Converter a entidade para DTO e retornar
    return convertToDatesAndAreasDTO(updatedEnterprise);
}

private DatesAndAreasCardDTO convertToDatesAndAreasDTO(Enterprise enterprise) {
    DatesAndAreasCardDTO dto = new DatesAndAreasCardDTO();
    dto.setLandArea(enterprise.getLandArea());
    dto.setTotalArea(enterprise.getTotalArea());
    dto.setCompletionDate(enterprise.getCompletionDate());
    dto.setStartDate(enterprise.getStartDate());
    return dto;
} 

@SuppressWarnings("null")
public FinanceCardDTO updateFinance(@NonNull UUID id, FinanceCardDTO dto) {
    // Buscar a empresa pelo ID
    Enterprise enterprise = enterpriseRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Enterprise not found with id: " + id));

    // Aplicar as alterações parciais (PATCH)
    if (dto.getTotalInvestment() != null) {
        enterprise.setTotalInvestment(dto.getTotalInvestment());
    }
    if (dto.getCurrentValue() != null) {
        enterprise.setCurrentValue(dto.getCurrentValue());
    }
    if (dto.getCurrency() != null) {
        enterprise.setCurrency(dto.getCurrency());
    }
    if (dto.getConstructionCompany() != null) {
        enterprise.setConstructionCompany(dto.getConstructionCompany());
    }
    if (dto.getArchitect() != null) {
        enterprise.setArchitect(dto.getArchitect());
    }

    // Salvar a entidade atualizada
    Enterprise updatedEnterprise = Objects.requireNonNull(enterpriseRepository.save(enterprise), "saved enterprise");

    // Converter a entidade para DTO e retornar
    return convertToFinanceCardDTO(updatedEnterprise);
}

private FinanceCardDTO convertToFinanceCardDTO(Enterprise enterprise) {
    FinanceCardDTO dto = new FinanceCardDTO();
    dto.setTotalInvestment(enterprise.getTotalInvestment());
    dto.setCurrentValue(enterprise.getCurrentValue());
    dto.setCurrency(enterprise.getCurrency());
    dto.setConstructionCompany(enterprise.getConstructionCompany());
    dto.setArchitect(enterprise.getArchitect());
    return dto;
}

@Transactional
public void removeMedia(@NonNull UUID id, @NonNull UUID mediaId) {
    // Buscar o media
    EnterprisesMedia media = enterprisesMediaRepository.findById(Objects.requireNonNull(mediaId, "mediaId"))
            .orElseThrow(() -> new EntityNotFoundException("Media not found with id: " + mediaId));

    try {
        // 1. Remover ficheiro do storage
        storageService.delete(media.getBucket(), media.getStorageKey());

        // 2. Remover o registo de media
        enterprisesMediaRepository.delete(media);

    } catch (BusinessException e) {
        throw e;
    } catch (Exception e) {
        throw new BusinessException(ErrorCode.MEDIA_DELETE_ERROR, "Failed to remove media: " + e.getMessage());
    }
}
}
