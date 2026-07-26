package com.management.managementapi.repository.email;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.email.EmailProvider;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailProviderRepository extends JpaRepository<EmailProvider, UUID> {
    
    @Query("SELECT e FROM EmailProvider e WHERE e.isDefault = true AND e.isActive = true")
    Optional<EmailProvider> findDefaultProvider();
}