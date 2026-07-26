package com.management.managementapi.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.lang.NonNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.dto.error.ErrorCode;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.dto.common.location.LocationUpdateDTO;
import com.management.managementapi.mapper.common.LocationMapper;
import com.management.managementapi.model.Location;
import com.management.managementapi.repository.LocationRepository;

@Service
public class LocationService {

  private final LocationRepository locationRepository;
  private final LocationMapper locationMapper;

  public LocationService(LocationRepository locationRepository, LocationMapper locationMapper) {
    this.locationRepository = locationRepository;
    this.locationMapper = locationMapper;
  }

  @Transactional(readOnly = true)
  public Location getByIdOrThrow(@NonNull UUID id) {
    return locationRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LOCATION_NOT_FOUND, "Location not found: " + id));
  }

  @Transactional
  public Location updateLocation(@NonNull UUID id, LocationUpdateDTO updateDTO) {
    Location existingLocation = getByIdOrThrow(id);
    locationMapper.updateFromDTO(updateDTO, existingLocation);
    existingLocation.setUpdatedAt(Timestamp.from(Instant.now()));
    return locationRepository.save(existingLocation);
  }

  /**
   * Criar uma nova localização
   */
  @Transactional
  public Location createLocation(LocationUpsertDTO createDTO) {
    Location newLocation = new Location();
    locationMapper.updateEntity(createDTO, newLocation);

    Timestamp now = Timestamp.from(Instant.now());
    newLocation.setCreatedAt(now);
    newLocation.setUpdatedAt(now);

    return locationRepository.save(newLocation);
  }

  @Transactional(readOnly = true)
  public List<String> getDistinctCountries() {
    return locationRepository.findDistinctCountries();
  }

  @Transactional(readOnly = true)
  public List<String> getDistinctCities(String country) {
    return locationRepository.findDistinctCities(country);
  }

  @Transactional(readOnly = true)
  public List<String> getDistinctMunicipalities(String country, String city) {
    return locationRepository.findDistinctMunicipalities(country, city);
  }

  @Transactional(readOnly = true)
  public List<String> getDistinctParishes(String country, String city, String municipality) {
    return locationRepository.findDistinctParishes(country, city, municipality);
  }
}
