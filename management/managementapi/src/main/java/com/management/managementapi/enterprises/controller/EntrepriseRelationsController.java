package com.management.managementapi.enterprises.controller;
// TRATAMENTOS DE ERROS ✅✅

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.management.managementapi.dto.common.location.LocationUpsertDTO;
import com.management.managementapi.enterprises.dto.enterprise.EnterpriseLocationDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.DatesAndAreasCardDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.EditOverViewCardDTO;
import com.management.managementapi.enterprises.dto.enterprise.edit.FinanceCardDTO;
import com.management.managementapi.enterprises.service.EnterpriseRelationsService;


import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/enterprise-relations/{id}")
public class EntrepriseRelationsController {

    private final EnterpriseRelationsService enterpriseRelationsService;

    public EntrepriseRelationsController(
            EnterpriseRelationsService enterpriseRelationsService) {
        this.enterpriseRelationsService = enterpriseRelationsService;
    }

    // -------------- LOCALIZAÇÃO-----------------//

     // Adicionar Localização(se já existir, elimina a existente e cria uma nova)
 // LOCALIZAÇÃO - UPSERT (corrigido para retornar dados)
@PostMapping("/location/upsert")
public ResponseEntity<EnterpriseLocationDTO> upsertLocation(
        @PathVariable @NonNull UUID id,
        @RequestBody LocationUpsertDTO dto) {
    EnterpriseLocationDTO result = enterpriseRelationsService.upsertLocation(id, dto);
    return ResponseEntity.ok(result);
}

// LOCALIZAÇÃO - REMOVER (path corrigido)
@DeleteMapping("/location")
public ResponseEntity<Void> removeLocation(@PathVariable @NonNull UUID id) {
    enterpriseRelationsService.removeLocation(id);
    return ResponseEntity.noContent().build();
}


    // OVERVIEW CARD

      @PatchMapping("/overview")
    public ResponseEntity<EditOverViewCardDTO> addOrReplaceOverView(@PathVariable @NonNull UUID id, @RequestBody EditOverViewCardDTO dto) {
        EditOverViewCardDTO result = enterpriseRelationsService.updateOverview( id , dto);
        return ResponseEntity.ok(result);
    }


    @PatchMapping("/dates-areas")
    public ResponseEntity<DatesAndAreasCardDTO> updateDatesAndAreas(@PathVariable @NonNull UUID id, @RequestBody DatesAndAreasCardDTO dto) {
        DatesAndAreasCardDTO result = enterpriseRelationsService.updateDatesAndAreas(id, dto);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/finance")
    public ResponseEntity<FinanceCardDTO> updateFinance(@PathVariable @NonNull UUID id, @RequestBody FinanceCardDTO dto) {
        FinanceCardDTO result = enterpriseRelationsService.updateFinance(id, dto);
        return ResponseEntity.ok(result);
    }


}