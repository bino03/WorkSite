package com.management.managementapi.enterprises.controller;

import com.management.managementapi.enterprises.dto.invoice.request.ConstructionInvoiceUpsertDTO;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteCreateDTO;
import com.management.managementapi.enterprises.dto.invoice.request.BatchAllocateDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSplitDTO;
import com.management.managementapi.enterprises.dto.invoice.response.BatchAllocateResultDTO;
import com.management.managementapi.enterprises.dto.invoice.response.RubricSuggestionDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceRegisterDTO;
import com.management.managementapi.enterprises.dto.invoice.response.BudgetItemSuggestionDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.CreditNoteSplitPreviewDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoicePreviewResultDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceUploadResultDTO;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.service.ConstructionInvoiceService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Faturas de obra — a caixa de entrada do projeto.
 *
 * Registar e classificar são momentos separados de propósito: {@code POST /}
 * aceita o ficheiro sozinho e {@code PATCH /{id}/allocate} é que decide a
 * rubrica, mais tarde. Ver {@link ConstructionInvoiceService}.
 */
@RestController
@RequestMapping("/construction-invoices")
@RequiredArgsConstructor
public class ConstructionInvoiceController {

    private final ConstructionInvoiceService service;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /**
     * Lê o QR e verifica duplicados sem gravar nada — o "Enviar" do
     * carregamento em duas fases. Devolve o que se mostra ao utilizador antes
     * de ele decidir "Guardar" (que é o {@code POST /} abaixo).
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoicePreviewResultDTO> preview(
            @RequestParam UUID enterpriseId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(service.preview(enterpriseId, file));
    }

    /**
     * Carrega uma fatura. O cliente chama isto uma vez por ficheiro largado, em
     * paralelo — não há endpoint de lote de propósito: assim cada ficheiro tem o
     * seu resultado e um QR ilegível não estraga os restantes.
     *
     * O ficheiro chega sempre por comprimir: o servidor lê o QR a partir do
     * original e só comprime depois, quando a leitura teve sucesso — ver
     * {@link ConstructionInvoiceService}.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> upload(
            @RequestParam UUID enterpriseId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.upload(enterpriseId, file);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, result.invoice().id(),
                        invoiceLabel(result.invoice()), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * A caixa de entrada. Sem filtros devolve tudo; o cliente abre-a com
     * {@code allocated=false}, que é o trabalho por fazer.
     */
    /**
     * Regista uma fatura <b>sem ficheiro</b> — a que está por pedir ou por
     * imprimir. É também por aqui que entram as despesas da empresa e as
     * faturas por identificar, que não têm obra.
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> register(
            @Valid @RequestBody InvoiceRegisterDTO dto,
            HttpServletRequest request) {
        ConstructionInvoiceResponseDTO created = service.register(dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, created.id(),
                        invoiceLabel(created), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Junta mais um ficheiro a uma fatura já registada. Ao contrário de
     * {@code POST /{id}/file}, que substitui, este acrescenta: a foto tirada na
     * obra e o PDF do fornecedor são o mesmo documento fiscal.
     */
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> addDocument(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.addDocument(id, file);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id,
                        invoiceLabel(result.invoice()), null, request));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * A proposta de repartição negativa de uma nota de crédito sobre esta
     * fatura, na proporção das suas despesas. Nada é gravado — o utilizador
     * confirma ou altera as linhas e só depois é que cria a NC com elas.
     */
    @GetMapping("/{originId}/credit-notes/split-preview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreditNoteSplitPreviewDTO> creditNoteSplitPreview(
            @PathVariable UUID originId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(service.previewCreditNoteSplit(originId, amount));
    }

    /**
     * Regista uma nota de crédito a partir desta fatura. A NC herda o âmbito e a
     * obra da origem; as despesas negativas vêm já confirmadas no corpo.
     */
    @PostMapping("/{originId}/credit-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> createCreditNote(
            @PathVariable UUID originId,
            @Valid @RequestBody CreditNoteCreateDTO dto,
            HttpServletRequest request) {
        ConstructionInvoiceResponseDTO created = service.createCreditNote(originId, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, created.id(), invoiceLabel(created), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * A quarentena: faturas que ainda não se sabe de quem são. Ordenadas da
     * mais antiga para a mais recente por omissão — quanto mais tempo lá está,
     * mais urgente é.
     */
    @GetMapping("/unidentified")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ConstructionInvoiceResponseDTO> listUnidentified(
            @RequestParam(required = false) Boolean outstanding,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.searchByScope(ConstructionInvoice.Scope.UNIDENTIFIED, outstanding, q, pageable);
    }

    /** Despesas da empresa: faturas sem obra, que não entram em orçamento nenhum. */
    @GetMapping("/company")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ConstructionInvoiceResponseDTO> listCompany(
            @RequestParam(required = false) Boolean outstanding,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.searchByScope(ConstructionInvoice.Scope.COMPANY, outstanding, q, pageable);
    }

    @GetMapping("/enterprise/{enterpriseId}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public Page<ConstructionInvoiceResponseDTO> listByEnterprise(
            @PathVariable UUID enterpriseId,
            @RequestParam(required = false) Boolean allocated,
            @RequestParam(required = false) Boolean needsReview,
            @RequestParam(required = false) Boolean outstanding,
            @RequestParam(required = false) Boolean sentToAccountant,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(enterpriseId, allocated, needsReview, outstanding, sentToAccountant, from, to, q, pageable);
    }

    /** Quantas faturas estão por associar — alimenta o aviso no ecrã do orçamento. */
    @GetMapping("/enterprise/{enterpriseId}/pending-count")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<Long> countPending(@PathVariable UUID enterpriseId) {
        return ResponseEntity.ok(service.countPending(enterpriseId));
    }

    /**
     * Rubrica sugerida para as faturas deste fornecedor, com base no que já foi
     * lançado neste projeto. Devolve 204 quando ainda não há histórico.
     */
    @GetMapping("/enterprise/{enterpriseId}/suggestion")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<BudgetItemSuggestionDTO> suggestion(
            @PathVariable UUID enterpriseId,
            @RequestParam String supplierNif) {
        return service.suggestBudgetItem(enterpriseId, supplierNif)
                .map(item -> new BudgetItemSuggestionDTO(item.getId(), item.getCode(), item.getName()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Detalhe. É a única resposta que traz o link assinado do documento completo. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getDetail(id));
    }

    /** Corrige à mão o que o QR não trouxe ou trouxe errado. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody ConstructionInvoiceUpsertDTO dto,
            HttpServletRequest request) {
        ConstructionInvoice updated = service.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, updated.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated, true));
    }

    /** Substitui o ficheiro — relê o QR e regenera a miniatura. */
    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> replaceFile(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.replaceFile(id, file);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoiceLabel(result.invoice()), null, request));

        return ResponseEntity.ok(result);
    }

    /**
     * Relê o QR do documento já arquivado e repõe os campos fiscais como a AT os
     * declarou — o desfazer de uma correção manual feita por engano.
     *
     * Não recebe ficheiro: usa o que já está no Storage. Para trocar a
     * digitalização é {@code POST /{id}/file}. O nome do fornecedor e as notas
     * não são tocados, porque não vêm do QR.
     */
    @PostMapping("/{id}/rescan")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<InvoiceUploadResultDTO> rescan(
            @PathVariable UUID id,
            HttpServletRequest request) {
        InvoiceUploadResultDTO result = service.rescan(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoiceLabel(result.invoice()), null, request));

        return ResponseEntity.ok(result);
    }

    /** Liga a fatura a uma rubrica, criando o lançamento. */
    @PatchMapping("/{id}/allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> allocate(
            @PathVariable UUID id,
            @RequestParam UUID budgetItemId,
            HttpServletRequest request) {
        ConstructionExpense expense = service.allocate(id, budgetItemId);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, expense.getId(), expense.getName(), request));

        return ResponseEntity.ok(service.getDetail(id));
    }

    /**
     * A rubrica que esta fatura provavelmente merece, com a origem declarada —
     * histórico nesta obra, ou histórico noutra obra traduzido por código.
     *
     * {@code 204} quando não há nada a sugerir: sem histórico, sem NIF, ou a
     * fatura não é de obra. Nunca grava nada — quem decide é quem classifica.
     */
    @GetMapping("/{id}/rubric-suggestion")
    @PreAuthorize("hasAnyRole('ADMIN','EMPLOYEE')")
    public ResponseEntity<RubricSuggestionDTO> rubricSuggestion(@PathVariable UUID id) {
        return service.suggestRubric(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Reparte a fatura por várias rubricas, substituindo a repartição atual.
     *
     * O {@code allocate} acima continua a servir o caso normal (uma rubrica);
     * este é o caso da folha que traz coisas de sítios diferentes. A soma das
     * linhas tem de esgotar o total da fatura — {@code INVOICE_028}.
     */
    @PostMapping("/{id}/expenses/split")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> split(
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceSplitDTO dto,
            HttpServletRequest request) {
        int lineCount = service.split(id, dto.lines()).size();

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id,
                        "repartida por " + lineCount + " rubrica(s)", null, request));

        return ResponseEntity.ok(service.getDetail(id));
    }

    /**
     * Classifica várias faturas para a mesma rubrica.
     *
     * Melhor esforço: devolve {@code 200} com o que passou e o que falhou, em
     * vez de derrubar o lote inteiro por causa de uma fatura. Ver
     * {@link BatchAllocateResultDTO}.
     */
    @PostMapping("/batch-allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchAllocateResultDTO> batchAllocate(
            @Valid @RequestBody BatchAllocateDTO dto,
            HttpServletRequest request) {
        BatchAllocateResultDTO result = service.batchAllocate(dto.invoiceIds(), dto.budgetItemId());

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_EXPENSE, dto.budgetItemId(),
                        result.succeeded() + " fatura(s) classificadas em lote", null, request));

        return ResponseEntity.ok(result);
    }

    /** Devolve a fatura à caixa de entrada, apagando o lançamento. */
    @DeleteMapping("/{id}/allocate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> deallocate(
            @PathVariable UUID id,
            HttpServletRequest request) {
        ConstructionInvoice invoice = service.deallocate(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoice.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.getDetail(id));
    }

    /** Marca/desmarca a fatura como enviada para o contabilista. */
    @PatchMapping("/{id}/accountant")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ConstructionInvoiceResponseDTO> setSentToAccountant(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean sent,
            HttpServletRequest request) {
        ConstructionInvoice updated = service.setSentToAccountant(id, sent);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, updated.getInvoiceNumber(), null, request));

        return ResponseEntity.ok(service.toResponseDTO(updated, false));
    }

    /**
     * Remove <b>um</b> documento da fatura. A fatura fica; se era o último
     * documento, volta ao estado "sem ficheiro".
     */
    @DeleteMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id,
                                               @PathVariable UUID documentId,
                                               HttpServletRequest request) {
        ConstructionInvoice invoice = service.getById(id);

        service.deleteDocument(id, documentId);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoice.getInvoiceNumber(), null, request));

        return ResponseEntity.noContent().build();
    }

    /** Apaga a fatura, o ficheiro, a miniatura e o lançamento que dela nasceu. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ConstructionInvoice invoice = service.getById(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.CONSTRUCTION_INVOICE, id, invoice.getInvoiceNumber(), request));

        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** O registo de atividade precisa de algo legível mesmo quando o QR falhou. */
    private String invoiceLabel(ConstructionInvoiceResponseDTO invoice) {
        if (invoice.invoiceNumber() != null && !invoice.invoiceNumber().isBlank()) {
            return invoice.invoiceNumber();
        }
        return invoice.originalFilename();
    }
}
