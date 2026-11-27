package com.management.managementapi.enterprises.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptCreateDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptPreviewDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptResponseDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptUpdateDTO;
import com.management.managementapi.enterprises.service.SupplierReceiptService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Recibos de fornecedor. Mesmo acesso que os pagamentos ({@code ADMIN},
 * {@code ACCOUNTANT}): o recibo vive ao lado deles, no ciclo financeiro da
 * fatura. Ver notes/roadmap/recibos.md.
 */
@RestController
@RequestMapping("/supplier-receipts")
@RequiredArgsConstructor
public class SupplierReceiptController {

    private final SupplierReceiptService receiptService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /** Multipart: {@code receipt} (JSON) + {@code file} (PDF/JPG/PNG, obrigatório). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<SupplierReceiptResponseDTO> create(
            @Valid @RequestPart("receipt") SupplierReceiptCreateDTO dto,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        SupplierReceiptResponseDTO receipt = receiptService.create(dto, file);

        authContext.currentProfileId().ifPresent(uid -> {
            String userName = authContext.currentUserName().orElse("unknown");
            activityLogger.logCreate(uid, userName, EntityType.SUPPLIER_RECEIPT, receipt.id(),
                    receiptLabel(receipt), request);
            // os pagamentos que o recibo criou ficam no log como qualquer outro pagamento
            receipt.paymentIds().forEach(paymentId ->
                    activityLogger.logCreate(uid, userName, EntityType.PAYMENT, paymentId,
                            "Pago pelo recibo " + receiptLabel(receipt), request));
        });

        return ResponseEntity.status(HttpStatus.CREATED).body(receipt);
    }

    /**
     * Lê o QR do recibo sem gravar nada e devolve as faturas do fornecedor que
     * ele pode cobrir. {@code linkedInvoiceIds}: as faturas já escolhidas.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<SupplierReceiptPreviewDTO> preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "linkedInvoiceIds", required = false) List<UUID> linkedInvoiceIds) {
        return ResponseEntity.ok(receiptService.preview(file, linkedInvoiceIds));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<SupplierReceiptResponseDTO> get(@PathVariable UUID id) {
        return ResponseEntity.ok(receiptService.get(id));
    }

    /** Metadados + as ligações inteiras (substituem as anteriores). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<SupplierReceiptResponseDTO> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierReceiptUpdateDTO dto,
            HttpServletRequest request) {
        SupplierReceiptResponseDTO receipt = receiptService.update(id, dto);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER_RECEIPT, id, receiptLabel(receipt), null, request));

        return ResponseEntity.ok(receipt);
    }

    @PutMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<SupplierReceiptResponseDTO> replaceFile(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        SupplierReceiptResponseDTO receipt = receiptService.replaceFile(id, file);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logEdit(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER_RECEIPT, id, receiptLabel(receipt), null, request));

        return ResponseEntity.ok(receipt);
    }

    /** Apaga o recibo e o ficheiro; os pagamentos que originou ficam. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        SupplierReceiptResponseDTO removed = receiptService.delete(id);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.SUPPLIER_RECEIPT, id, receiptLabel(removed), request));

        return ResponseEntity.noContent().build();
    }

    private static String receiptLabel(SupplierReceiptResponseDTO receipt) {
        String number = receipt.receiptNumber() != null ? receipt.receiptNumber() : "recibo sem nº";
        return receipt.supplierName() != null ? number + " — " + receipt.supplierName() : number;
    }
}
