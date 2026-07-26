package com.management.managementapi.controller;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.dto.common.location.LocationResponseDTO;
import com.management.managementapi.dto.common.location.LocationUpdateDTO;
import com.management.managementapi.dto.common.location.LocationDetailsResponseDTO;
import com.management.managementapi.mapper.common.LocationMapper;
import com.management.managementapi.repository.LocationRepository;
import com.management.managementapi.service.LocationService;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/locations")
public class LocationController {

  private final LocationRepository locationRepo;
  private final LocationMapper locationMapper;
  private final LocationService locationService;

  public LocationController(LocationRepository locationRepo, LocationMapper locationMapper, LocationService locationService) {
    this.locationRepo = locationRepo;
    this.locationMapper = locationMapper;
    this.locationService = locationService;
  }

  /** Lista paginada de localizações. Suporta pesquisa livre por ?q= */
  @GetMapping
  public Page<LocationResponseDTO> list(
      @RequestParam(required = false) String q,
      @NonNull Pageable pageable
  ) {
    var page = (q == null || q.isBlank())
        ? locationRepo.findAll(pageable)
        : locationRepo.search(q, pageable);

    return page.map(locationMapper::toResponse);
  }

  /**
   * Criar uma nova localização
   * POST /locations
   */
  @PostMapping
  public LocationDetailsResponseDTO createLocation(
      @Valid @RequestBody LocationUpsertDTO createDTO) {
      var createdLocation = locationService.createLocation(createDTO);
      return locationMapper.toDetailsResponse(createdLocation);
  }

  @GetMapping("/{id}")
  public LocationDetailsResponseDTO getById(@PathVariable("id") @NonNull UUID id) {
    var location = locationService.getByIdOrThrow(id);
    return locationMapper.toDetailsResponse(location);
  }

  // Atualiza uma localização existente
  @PatchMapping("/{id}")
  public LocationDetailsResponseDTO updateLocation(
      @PathVariable("id") @NonNull UUID id,
      @RequestBody LocationUpdateDTO updateDTO) {
    var updatedLocation = locationService.updateLocation(id, updateDTO);
    return locationMapper.toDetailsResponse(updatedLocation);
  }

  /**
   * Retorna lista única de países
   */
  @GetMapping("/countries")
  public List<String> getCountries() {
    return locationService.getDistinctCountries();
  }

  /**
   * Retorna cidades (opcionalmente filtradas por país)
   */
  @GetMapping("/cities")
  public List<String> getCities(@RequestParam(required = false) String country) {
    return locationService.getDistinctCities(country);
  }

  /**
   * Retorna municípios (opcionalmente filtrados por país e/ou cidade)
   */
  @GetMapping("/municipalities")
  public List<String> getMunicipalities(
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String city) {
    return locationService.getDistinctMunicipalities(country, city);
  }

  /**
   * Retorna freguesias (opcionalmente filtradas por país, cidade e/ou município)
   */
  @GetMapping("/parishes")
  public List<String> getParishes(
      @RequestParam(required = false) String country,
      @RequestParam(required = false) String city,
      @RequestParam(required = false) String municipality) {
    return locationService.getDistinctParishes(country, city, municipality);
  }
}
