package com.management.managementapi.enterprises.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.enterprises.model.SupplierReceipt;

@Repository
public interface SupplierReceiptRepository extends JpaRepository<SupplierReceipt, UUID> {

    /** O recibo com o mesmo emissor e nº normalizado, tirando {@code excludeId} (o próprio, ao editar). */
    @Query("""
            select r from SupplierReceipt r
            where r.supplierNif = :supplierNif
              and r.receiptNumberNormalized = :numberNormalized
              and (:excludeId is null or r.id <> :excludeId)
            """)
    Optional<SupplierReceipt> findDuplicate(@Param("supplierNif") String supplierNif,
                                            @Param("numberNormalized") String numberNormalized,
                                            @Param("excludeId") UUID excludeId);
}
