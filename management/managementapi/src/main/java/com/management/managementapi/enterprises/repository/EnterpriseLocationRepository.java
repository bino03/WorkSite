package com.management.managementapi.enterprises.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.management.managementapi.enterprises.model.EnterprisesLocation;

public interface EnterpriseLocationRepository extends JpaRepository<EnterprisesLocation, UUID> {

    @Override
    @NonNull
    Optional<EnterprisesLocation> findById(@NonNull UUID id);

    @Override
    @NonNull
    <S extends EnterprisesLocation> S save(@NonNull S entity);

    Optional<EnterprisesLocation> findByEnterpriseId(UUID enterprise);

    @Override
    void deleteById(@NonNull UUID id);


@Query("SELECT el FROM EnterprisesLocation el JOIN FETCH el.location WHERE el.enterprise.id IN :enterpriseIds")
    List<EnterprisesLocation> findByEnterpriseIdInWithLocation(@Param("enterpriseIds") List<UUID> enterpriseIds);


    List<EnterprisesLocation> findByEnterpriseIdIn(List<UUID> enterpriseIds);
}
