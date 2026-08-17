package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.supplier.request.SupplierUpsertDTO;
import com.management.managementapi.enterprises.dto.supplier.response.SupplierResponseDTO;
import com.management.managementapi.enterprises.dto.supplier.response.SupplierSaveResultDTO;
import com.management.managementapi.enterprises.dto.supplier.response.UnknownSupplierNifDTO;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.service.SupplierService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Fornecedores de obra — o catálogo NIF → nome da empresa.
 *
 * Ler é para toda a gente que mexe em faturas; escrever é de ADMIN, como o
 * resto do que altera dados já registados (gravar um nome reescreve faturas de
 * todos os projetos — ver {@link SupplierService}).
 */
@RestController
@RequestMapping("/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /** O catálogo, por ordem alfabética. {@code q} filtra por nome ou NIF. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public List<SupplierResponseDTO> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    /**
     * Os NIFs que aparecem nas faturas e ainda não têm empresa associada, do
     * mais frequente para o menos — a lista de trabalho do ecrã.
     */
    @GetMapping("/unknown-nifs")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public List<UnknownSupplierNifDTO> unknownNifs() {
        return service.listUnknownNifs();
    }

    /** Dá nome a um NIF. Preenche também as faturas desse NIF que estejam sem nome. */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupplierSaveResultDTO> create(
            @Valid @RequestBody SupplierUpsertDTO dto,
            HttpServletRequest request) {
        SupplierSaveResultDTO result = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER, result.supplier().id(), result.supplier().name(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupplierSaveResultDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierUpsertDTO dto,
            HttpServletRequest request) {
        SupplierSaveResultDTO result = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER, id, result.supplier().name(), null, request));

        return ResponseEntity.ok(result);
    }

    /**
     * Tira o fornecedor do catálogo. O nome já escrito nas faturas fica — é um
     * dado do documento, não uma referência a esta tabela.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        Supplier supplier = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER, id, supplier.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
