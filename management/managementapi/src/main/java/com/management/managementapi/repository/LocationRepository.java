package com.management.managementapi.repository;

import com.management.managementapi.model.Location;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, java.util.UUID> {

  @Query("""
    select l
      from Location l
     where (:q is null or :q = ''
        or lower(coalesce(l.addressLine1,'')) like lower(concat('%', :q, '%'))
        or lower(coalesce(l.addressLine2,'')) like lower(concat('%', :q, '%'))
        or lower(coalesce(l.city,''))        like lower(concat('%', :q, '%'))
        or lower(coalesce(l.parish,''))      like lower(concat('%', :q, '%'))
        or lower(coalesce(l.postalCode,''))  like lower(concat('%', :q, '%'))
        or lower(coalesce(l.country,''))     like lower(concat('%', :q, '%'))
     )
  """)
  Page<Location> search(@Param("q") String q, Pageable pageable);

  @Query("SELECT DISTINCT l.country FROM Location l WHERE l.country IS NOT NULL ORDER BY l.country")
  List<String> findDistinctCountries();

  @Query("""
    SELECT DISTINCT l.city FROM Location l
    WHERE l.city IS NOT NULL
    AND (:country IS NULL OR l.country = :country)
    ORDER BY l.city
  """)
  List<String> findDistinctCities(@Param("country") String country);

  @Query("""
    SELECT DISTINCT l.municipality FROM Location l
    WHERE l.municipality IS NOT NULL
    AND (:country IS NULL OR l.country = :country)
    AND (:city IS NULL OR l.city = :city)
    ORDER BY l.municipality
  """)
  List<String> findDistinctMunicipalities(
      @Param("country") String country,
      @Param("city") String city);

  @Query("""
    SELECT DISTINCT l.parish FROM Location l
    WHERE l.parish IS NOT NULL
    AND (:country IS NULL OR l.country = :country)
    AND (:city IS NULL OR l.city = :city)
    AND (:municipality IS NULL OR l.municipality = :municipality)
    ORDER BY l.parish
  """)
  List<String> findDistinctParishes(
      @Param("country") String country,
      @Param("city") String city,
      @Param("municipality") String municipality);
}
