package com.management.managementapi.enterprises.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.EnterprisesMedia;
import com.management.managementapi.model.enums.MediaType;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnterpriseMediaRepository extends JpaRepository<EnterprisesMedia, UUID> {
    List<EnterprisesMedia> findByEnterpriseId(UUID enterpriseId);
    List<EnterprisesMedia> findByEnterpriseIdAndType(UUID enterpriseId, MediaType type);
    void deleteByEnterpriseId(UUID enterpriseId);
}