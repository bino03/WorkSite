package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionSubStage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConstructionSubStageRepository extends JpaRepository<ConstructionSubStage, UUID> {

    List<ConstructionSubStage> findByStageId(UUID stageId);
}
