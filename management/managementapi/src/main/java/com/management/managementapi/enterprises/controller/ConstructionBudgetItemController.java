package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.request.BudgetItemUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.request.BudgetLotUpsertDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

    // ── lotes (V39) ───────────────────────────────────────────

    @GetMapping("/enterprise/{enterpriseId}/budgets")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<List<BudgetLotDTO>> listLots(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.listLots(enterpriseId));
    }

    @PostMapping("/enterprise/{enterpriseId}/budgets")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetLotDTO> createLot(@PathVariable UUID enterpriseId,
                                                  @Valid @RequestBody BudgetLotUpsertDTO dto,
                                                  HttpServletRequest request) {
        BudgetLotDTO lot = service.createLot(enterpriseId, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_BUDGET, lot.id(), lot.name(), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(lot);
    }

    @PatchMapping("/budgets/{budgetId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetLotDTO> updateLot(@PathVariable UUID budgetId,
                                                  @Valid @RequestBody BudgetLotUpsertDTO dto,
                                                  HttpServletRequest request) {
        BudgetLotDTO lot = service.updateLot(budgetId, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_BUDGET, budgetId, lot.name(), null, request));

        return ResponseEntity.ok(lot);
    }

    @DeleteMapping("/budgets/{budgetId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLot(@PathVariable UUID budgetId, HttpServletRequest request) {
        String name = service.getLot(budgetId).getName();
        service.deleteLot(budgetId);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_BUDGET, budgetId, name, request));

        return ResponseEntity.noContent().build();
    }

    // ── leitura ───────────────────────────────────────────────

    /** O orçamento da vila inteira — todos os lotes lado a lado. */
    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetTreeDTO> getTree(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.getTree(enterpriseId));
    }

    /** A árvore de um lote — a página do orçamento. */
    @GetMapping("/budgets/{budgetId}/tree")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetTreeDTO> getBudgetTree(@PathVariable UUID budgetId) {
        return ResponseEntity.ok(service.getBudgetTree(budgetId));
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
     * A pasta da obra inteira: {@code <slug>.zip} com o {@code Despesas - <slug>.xlsx}
     * e {@code Faturas/Lançadas/*} (os documentos das faturas com o nome do
     * vault, §7) na raiz — extrai-se em {@code Empreendimentos\<slug>\}.
     *
     * O livro e os nomes decidem-se dentro da transação; os documentos vêm do
     * Storage um a um enquanto a resposta se escreve ({@link StreamingResponseBody}),
     * porque uma obra tem dezenas de MB deles. A mesma permissão do {@code /export}:
     * é leitura.
     */
    @GetMapping("/enterprise/{enterpriseId}/export/zip")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<StreamingResponseBody> exportZip(@PathVariable UUID enterpriseId,
                                                           @RequestParam Set<BudgetExportSheet> sheets) {
        BudgetExcelExportService.ZipExport zip = exportService.exportZip(enterpriseId, sheets);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(zip.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out -> exportService.writeZip(zip, out));
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

    /** A zona de recuperação: rubricas eliminadas (soft delete) deste lote, mais recente primeiro. */
    @GetMapping("/budgets/{budgetId}/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BudgetItemDeletedDTO>> getDeleted(@PathVariable UUID budgetId) {
        return ResponseEntity.ok(service.listDeleted(budgetId));
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
     * aí exige {@code replace=true} se o lote já tiver orçamento. O
     * {@code replace} só apaga este lote — os outros do projeto ficam.
     */
    @PostMapping(value = "/budgets/{budgetId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BudgetImportResultDTO> importBudget(
            @PathVariable UUID budgetId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "false") boolean replace,
            HttpServletRequest request) {

        BudgetImportResultDTO result = importService.importBudget(budgetId, file, dryRun, replace);

        if (!dryRun) {
            authContext.currentProfileId().ifPresent(uid ->
                    activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                            EntityType.CONSTRUCTION_BUDGET, budgetId,
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
