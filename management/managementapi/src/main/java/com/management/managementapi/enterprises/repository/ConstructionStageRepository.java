package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionStage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConstructionStageRepository extends JpaRepository<ConstructionStage, UUID> {

    List<ConstructionStage> findByEnterpriseId(UUID enterpriseId);
}
