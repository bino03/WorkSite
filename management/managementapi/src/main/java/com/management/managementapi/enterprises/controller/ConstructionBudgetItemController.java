package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSaveResponseDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.service.BudgetExcelImportService;
import com.management.managementapi.enterprises.service.ConstructionBudgetItemService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Árvore de rubricas do orçamento de um projeto.
 */
@RestController
@RequestMapping("/construction-budget")
@RequiredArgsConstructor
public class ConstructionBudgetItemController {

    private final ConstructionBudgetItemService service;
    private final BudgetExcelImportService importService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── leitura ───────────────────────────────────────────────

    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetTreeDTO> getTree(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.getTree(enterpriseId));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetItemNodeDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getNode(id));
    }

    // ── escrita ───────────────────────────────────────────────

    @PostMapping("/items")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetItemSaveResponseDTO> create(
            @Valid @RequestBody BudgetItemUpsertDTO dto,
            HttpServletRequest request) {

        BudgetItemSaveResponseDTO saved = service.create(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.BUDGET_ITEM, saved.item().id(), saved.item().name(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetItemSaveResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody BudgetItemUpsertDTO dto,
            HttpServletRequest request) {

        BudgetItemSaveResponseDTO saved = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.BUDGET_ITEM, id, saved.item().name(), null, request));

        return ResponseEntity.ok(saved);
    }

    /** Reordena entre irmãos e/ou muda de rubrica-mãe. */
    @PatchMapping("/items/{id}/move")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetItemNodeDTO> move(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) Integer sortOrder,
            HttpServletRequest request) {

        BudgetItemNodeDTO moved = service.move(id, parentId, sortOrder);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.BUDGET_ITEM, id, moved.name(), null, request));

        return ResponseEntity.ok(moved);
    }

    // ── importação ────────────────────────────────────────────

    /**
     * Importa um orçamento em .xlsx.
     *
     * Por omissão corre em {@code dryRun}: devolve o que <i>seria</i> criado,
     * com avisos, sem gravar nada. Só com {@code dryRun=false} é que grava, e
     * aí exige {@code replace=true} se o projeto já tiver orçamento.
     */
    @PostMapping(value = "/enterprise/{enterpriseId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetImportResultDTO> importBudget(
            @PathVariable UUID enterpriseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean replace,
            HttpServletRequest request) {

        BudgetImportResultDTO result = importService.importBudget(enterpriseId, file, dryRun, replace);

        if (!dryRun) {
            authContext.currentProfileId().ifPresent(uid ->
                    activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                            EntityType.BUDGET_ITEM, enterpriseId,
                            "Importação de orçamento (" + result.itemCount() + " rubricas)", request));
        }

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionBudgetItem item = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.BUDGET_ITEM, id, item.getName(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
