package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.ConstructionExpense;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConstructionExpenseRepository extends JpaRepository<ConstructionExpense, UUID> {

    List<ConstructionExpense> findBySubStageId(UUID subStageId);
}
