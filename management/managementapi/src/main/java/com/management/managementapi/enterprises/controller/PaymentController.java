package com.management.managementapi.enterprises.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.enterprises.dto.payment.AggregatePaymentRequestDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentResultDTO;
import com.management.managementapi.enterprises.dto.payment.MarkPaidRequestDTO;
import com.management.managementapi.enterprises.dto.payment.PaymentResponseDTO;
import com.management.managementapi.enterprises.service.PaymentService;
import com.management.managementapi.model.enums.EntityType;
import com.management.managementapi.security.AuthContext;
import com.management.managementapi.service.ActivityLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Pagamentos de faturas de obra.
 *
 * Tudo restrito a {@code ADMIN}: o ciclo financeiro da fatura (registar,
 * associar, pagar, anular) é decisão de gestão. O estado de pagamento da fatura
 * não vive aqui — é derivado das ligações e sai nos DTOs de fatura.
 */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ActivityLogger activityLogger;
    private final AuthContext authContext;

    /**
     * Marca <b>uma</b> fatura como paga: cria um movimento e liga-o à fatura.
     * Multipart — a prova ({@code proof}) é opcional.
     */
    @PostMapping(value = "/construction-invoices/{invoiceId}/payments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PaymentResponseDTO> markAsPaid(
            @PathVariable UUID invoiceId,
            @Valid @RequestPart("payment") MarkPaidRequestDTO dto,
            @RequestPart(value = "proof", required = false) MultipartFile proof,
            HttpServletRequest request) {
        PaymentResponseDTO payment = paymentService.markAsPaid(invoiceId, dto, proof);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.PAYMENT, payment.id(), paymentLabel(payment), request));

        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    /**
     * Um movimento que liquida várias faturas de uma vez (raro). Se o valor do
     * movimento não bater certo com a soma do que falta pagar nas faturas
     * escolhidas, nada é gravado e a resposta ({@code created = false}) diz
     * quais ficam de fora.
     */
    @PostMapping(value = "/construction-invoices/payments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AggregatePaymentResultDTO> registerAggregate(
            @Valid @RequestPart("payment") AggregatePaymentRequestDTO dto,
            @RequestPart(value = "proof", required = false) MultipartFile proof,
            HttpServletRequest request) {
        AggregatePaymentResultDTO result = paymentService.registerAggregate(dto, proof);

        if (result.created()) {
            authContext.currentProfileId().ifPresent(uid ->
                    activityLogger.logCreate(uid, authContext.currentUserName().orElse("unknown"),
                            EntityType.PAYMENT, result.payment().id(), paymentLabel(result.payment()), request));
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /** Anula um pagamento e repõe as faturas por liquidar. Fica em {@code activity_log}. */
    @DeleteMapping("/construction-invoices/payments/{paymentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID paymentId, HttpServletRequest request) {
        PaymentResponseDTO removed = paymentService.delete(paymentId);

        authContext.currentProfileId().ifPresent(uid ->
                activityLogger.logDelete(uid, authContext.currentUserName().orElse("unknown"),
                        EntityType.PAYMENT, removed.id(), paymentLabel(removed), request));

        return ResponseEntity.noContent().build();
    }

    private static String paymentLabel(PaymentResponseDTO payment) {
        return "Pagamento de " + payment.amount() + " € em " + payment.paidOn();
    }
}
