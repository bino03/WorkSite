package com.management.managementapi.repository.email;



import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.email.EmailProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailProviderRepository extends JpaRepository<EmailProvider, UUID> {
    
    /**
     * Só o predefinido — sem filtrar por ativo, de propósito. Quem envia é que decide
     * o que fazer com um predefinido desligado, e assim consegue dizer "está desativado"
     * em vez de "não existe nenhum".
     */
    @Query("SELECT e FROM EmailProvider e WHERE e.isDefault = true")
    Optional<EmailProvider> findDefaultProvider();

    /** O predefinido primeiro — é o que interessa ver ao abrir a lista. */
    @Query("SELECT e FROM EmailProvider e ORDER BY e.isDefault DESC NULLS LAST, e.providerName ASC")
    List<EmailProvider> findAllOrdered();

    /**
     * Só pode haver um predefinido. Corre <b>antes</b> de marcar o novo, porque o
     * índice único parcial da V21 é verificado a cada statement e não no fim da
     * transação.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EmailProvider e SET e.isDefault = false WHERE e.id <> :keepId AND e.isDefault = true")
    void clearDefaultExcept(@Param("keepId") UUID keepId);
}
