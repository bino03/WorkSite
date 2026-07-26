package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.enums.EnterPriseStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnterpriseRepository extends JpaRepository<Enterprise, UUID> {
    
    List<Enterprise> findByStatus(EnterPriseStatus status);
    
    List<Enterprise> findByIsActiveTrue();

    Page<Enterprise> findByIsActiveTrue(Pageable pageable);

    @Query("SELECT e FROM Enterprise e WHERE e.isActive = true AND LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Enterprise> findByIsActiveTrueAndNameContaining(@Param("search") String search, Pageable pageable);

    List<Enterprise> findByNameContainingIgnoreCase(String name);

    @Query("SELECT e FROM Enterprise e WHERE e.isActive = true AND LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Enterprise> findActiveByNameContaining(@Param("name") String name);
    
    Optional<Enterprise> findByInternalReference(String internalReference);

    @Override
    @NonNull
    Optional<Enterprise> findById(@NonNull UUID id);

    @Override
    @NonNull
    <S extends Enterprise> S save(@NonNull S entity);


    @Query("SELECT e FROM Enterprise e WHERE e.isActive = true AND e.status = :status")
    List<Enterprise> findActiveByStatus(@Param("status") EnterPriseStatus status);
    
    boolean existsByInternalReference(String internalReference);
}