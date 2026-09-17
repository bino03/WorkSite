package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetExportSummaryDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemDeletedDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSearchResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemSaveResponseDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.service.BudgetExcelExportService;
import com.management.managementapi.enterprises.service.BudgetExcelImportService;
import com.management.managementapi.enterprises.service.ConstructionBudgetItemService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
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
    private final BudgetExcelExportService exportService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    // ── leitura ───────────────────────────────────────────────

    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetTreeDTO> getTree(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.getTree(enterpriseId));
    }

    // ── exportação para Excel ─────────────────────────────────

    /** O que a exportação vai escrever (contagens e avisos) — o passo 2 do modal, antes do download. */
    @GetMapping("/enterprise/{enterpriseId}/export/summary")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetExportSummaryDTO> exportSummary(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(exportService.summary(enterpriseId));
    }

    /**
     * O {@code Despesas - <Obra>.xlsx} do vault, com as folhas pedidas
     * ({@code sheets=BUDGET,EXPENSES,COMPARISON}). É leitura: quem pode ver o
     * orçamento pode levá-lo consigo.
     *
     * O nome do ficheiro vai em {@code filename*=UTF-8''…}: os slugs têm acentos
     * e espaços, e o {@code filename="…"} cru só aguenta ISO-8859-1.
     */
    @GetMapping("/enterprise/{enterpriseId}/export")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<byte[]> export(@PathVariable UUID enterpriseId,
                                         @RequestParam Set<BudgetExportSheet> sheets) {
        BudgetExcelExportService.ExportFile file = exportService.export(enterpriseId, sheets);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(BudgetExcelExportService.CONTENT_TYPE))
                .body(file.content());
    }

    /**
     * Procura uma rubrica por código ({@code 4.2}) ou por texto ({@code betão}).
     *
     * Devolve o caminho completo e o orçamentado vs. gasto de cada resultado —
     * é o campo único do ecrã de classificação, desenhado para se escolher a
     * rubrica sem sair dele. Rubricas que não aceitam despesas não aparecem.
     */
    @GetMapping("/enterprise/{enterpriseId}/search")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<BudgetItemSearchResultDTO>> search(
            @PathVariable UUID enterpriseId,
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(service.search(enterpriseId, q, limit));
    }

    @GetMapping("/items/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetItemNodeDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getNode(id));
    }

    /** A zona de recuperação: rubricas eliminadas (soft delete) desta obra, mais recente primeiro. */
    @GetMapping("/enterprise/{enterpriseId}/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BudgetItemDeletedDTO>> getDeleted(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.listDeleted(enterpriseId));
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

    /** Repõe uma rubrica eliminada (e a sub-árvore eliminada junto com ela). */
    @PatchMapping("/items/{id}/recover")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetItemNodeDTO> recover(@PathVariable UUID id, HttpServletRequest request) {
        BudgetItemNodeDTO recovered = service.recover(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.BUDGET_ITEM, id, recovered.name(), null, request));

        return ResponseEntity.ok(recovered);
    }
}
