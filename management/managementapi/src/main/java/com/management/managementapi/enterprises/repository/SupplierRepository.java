package com.management.managementapi.enterprises.repository;

import com.management.managementapi.enterprises.model.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Optional<Supplier> findByNif(String nif);

    /**
     * O catálogo por ordem alfabética, opcionalmente filtrado por nome ou NIF.
     * Sem paginação de propósito: são dezenas de fornecedores, não milhares, e a
     * drawer mostra-os todos de uma vez.
     */
    @Query("""
            select s from Supplier s
            where (:q is null
                   or lower(s.name) like lower(concat('%', :q, '%'))
                   or lower(s.nif)  like lower(concat('%', :q, '%')))
            order by lower(s.name)
            """)
    List<Supplier> search(@Param("q") String q);

    /** Para a verificação de NIF repetido numa renomeação não acusar o próprio. */
    @Query("""
            select count(s) > 0 from Supplier s
            where s.nif = :nif and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean existsByNif(@Param("nif") String nif, @Param("excludeId") UUID excludeId);
}
