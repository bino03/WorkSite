package com.management.managementapi.enterprises.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentRequestDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentResultDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.dto.payment.LeftOutInvoiceDTO;
import com.management.managementapi.enterprises.dto.payment.MarkPaidRequestDTO;
import com.management.managementapi.enterprises.dto.payment.PaymentAllocationDTO;
import com.management.managementapi.enterprises.dto.payment.PaymentResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.InvoicePayment;
import com.management.managementapi.enterprises.model.Payment;
import com.management.managementapi.enterprises.model.enums.PaymentMethod;
import com.management.managementapi.enterprises.model.enums.PaymentStatus;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.enterprises.repository.PaymentRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pagamentos de faturas de obra.
 *
 * Uma fatura não tem coluna de estado de pagamento: o estado
 * (UNPAID / PARTIAL / PAID) deriva-se sempre de Σ({@link InvoicePayment}) vs. o
 * líquido da fatura. {@link #netAmount(ConstructionInvoice)} é o único sítio
 * onde "líquido" se calcula — a fase 3 (notas de crédito) só tem de mudar aí.
 *
 * Ver docs/faturas-modelo-alvo.md §2.3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private static final Set<String> PROOF_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png");
    private static final long MAX_PROOF_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final String BUCKET = "documents";

    private final PaymentRepository paymentRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storageService;
    private final SignedUrlService signedUrls;
    private final AuthContext authContext;

    // ── líquido e estado ─────────────────────────────────────────

    /**
     * O líquido de uma fatura — o que há a pagar: {@code total − Σ notas de
     * crédito}. É o único ponto onde "líquido" se calcula.
     */
    public BigDecimal netAmount(ConstructionInvoice invoice) {
        if (invoice.getTotalAmount() == null) {
            return null;
        }
        BigDecimal credited = invoiceRepository.sumCreditNotesFor(invoice.getId());
        return invoice.getTotalAmount().subtract(credited == null ? BigDecimal.ZERO : credited);
    }

    /**
     * O estado de pagamento a partir do líquido e do que já foi pago.
     * {@code null} de líquido (fatura ainda por rever) conta como não pago.
     */
    public static PaymentStatus deriveStatus(BigDecimal net, BigDecimal paid) {
        BigDecimal p = paid == null ? BigDecimal.ZERO : paid;
        if (p.signum() == 0) {
            return PaymentStatus.UNPAID;
        }
        if (net == null) {
            return PaymentStatus.PARTIAL;
        }
        return p.compareTo(net) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL;
    }

    @Transactional(readOnly = true)
    public BigDecimal paidAmount(UUID invoiceId) {
        BigDecimal sum = invoicePaymentRepository.sumPaidByInvoice(invoiceId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** Soma paga por várias faturas de uma vez — para o selo de estado nas listas. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> paidSums(Collection<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, BigDecimal> out = new HashMap<>();
        for (InvoicePaymentRepository.InvoicePaidSum row : invoicePaymentRepository.sumPaidByInvoices(invoiceIds)) {
            out.put(row.getInvoiceId(), row.getPaid() == null ? BigDecimal.ZERO : row.getPaid());
        }
        return out;
    }

    // ── marcar uma fatura como paga ──────────────────────────────

    public PaymentResponseDTO markAsPaid(UUID invoiceId, MarkPaidRequestDTO dto, MultipartFile proof) {
        ConstructionInvoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ResourceNotFoundException.constructionInvoice(invoiceId.toString()));
        rejectIfCreditNote(invoice);

        BigDecimal net = requireNet(invoice);
        BigDecimal remaining = net.subtract(paidAmount(invoiceId));
        if (remaining.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVOICE_ALREADY_PAID);
        }

        BigDecimal amount = dto.amount() != null ? dto.amount() : remaining;
        if (amount.signum() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "O valor a pagar tem de ser positivo");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_EXCEEDS_NET);
        }

        Payment payment = newPayment(dto.paidOn(), dto.method(), amount, dto.reference(), dto.notes());
        attachProof(payment, proof, invoice);
        payment = paymentRepository.save(payment);

        invoicePaymentRepository.save(new InvoicePayment(payment, invoice, amount));
        return toResponseDTO(payment);
    }

    // ── pagamento agregado (N faturas, 1 movimento) ──────────────

    public AggregatePaymentResultDTO registerAggregate(AggregatePaymentRequestDTO dto, MultipartFile proof) {
        List<UUID> ids = dto.invoiceIds() == null ? List.of()
                : dto.invoiceIds().stream().distinct().toList();
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_NO_INVOICES);
        }

        Map<UUID, ConstructionInvoice> byId = invoiceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ConstructionInvoice::getId, i -> i));
        List<ConstructionInvoice> invoices = new ArrayList<>();
        for (UUID id : ids) {
            ConstructionInvoice invoice = byId.get(id);
            if (invoice == null) {
                throw ResourceNotFoundException.constructionInvoice(id.toString());
            }
            rejectIfCreditNote(invoice);
            invoices.add(invoice);
        }

        if (!sameGroup(invoices)) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_CROSS_ENTERPRISE);
        }

        Map<UUID, BigDecimal> paidByInvoice = paidSums(ids);
        LinkedHashMap<ConstructionInvoice, BigDecimal> remainingByInvoice = new LinkedHashMap<>();
        for (ConstructionInvoice invoice : invoices) {
            BigDecimal net = requireNet(invoice);
            BigDecimal remaining = net.subtract(paidByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO));
            if (remaining.signum() <= 0) {
                throw new BusinessException(ErrorCode.INVOICE_ALREADY_PAID,
                        "A fatura " + describe(invoice) + " já está paga — tire-a da seleção");
            }
            remainingByInvoice.put(invoice, remaining);
        }

        BigDecimal selectedTotal = remainingByInvoice.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal movement = dto.amount();

        // Movimento acima da soma é erro que o utilizador não resolve tirando faturas.
        if (movement.compareTo(selectedTotal) > 0) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_SUM_MISMATCH,
                    "O valor do movimento (" + movement + " €) é superior à soma do que falta pagar"
                            + " nas faturas selecionadas (" + selectedTotal + " €).");
        }
        // Movimento abaixo da soma: nada é gravado; devolve-se as que ficam de fora (greedy pela
        // ordem dada) para o utilizador as tirar da seleção. Nunca se decide sozinho.
        if (movement.compareTo(selectedTotal) < 0) {
            List<LeftOutInvoiceDTO> leftOut = new ArrayList<>();
            BigDecimal budget = movement;
            for (Map.Entry<ConstructionInvoice, BigDecimal> entry : remainingByInvoice.entrySet()) {
                if (budget.compareTo(entry.getValue()) >= 0) {
                    budget = budget.subtract(entry.getValue());
                } else {
                    leftOut.add(leftOutDTO(entry.getKey(), entry.getValue()));
                }
            }
            return new AggregatePaymentResultDTO(false, null, leftOut, selectedTotal, movement);
        }

        Payment payment = newPayment(dto.paidOn(), dto.method(), movement, dto.reference(), dto.notes());
        attachProof(payment, proof, invoices.get(0));
        payment = paymentRepository.save(payment);
        for (Map.Entry<ConstructionInvoice, BigDecimal> entry : remainingByInvoice.entrySet()) {
            invoicePaymentRepository.save(new InvoicePayment(payment, entry.getKey(), entry.getValue()));
        }
        return new AggregatePaymentResultDTO(true, toResponseDTO(payment), List.of(), selectedTotal, movement);
    }

    // ── anular ──────────────────────────────────────────────────

    /** Apaga o pagamento e repõe as faturas. Devolve o que foi apagado, para o log. */
    public PaymentResponseDTO delete(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_PAYMENT_NOT_FOUND));

        PaymentResponseDTO snapshot = toResponseDTO(payment);
        invoicePaymentRepository.deleteAll(invoicePaymentRepository.findByPaymentId(paymentId));
        deleteQuietly(payment.getProofBucket(), payment.getProofKey());
        paymentRepository.delete(payment);
        return snapshot;
    }

    // ── vistas para os DTOs de fatura ───────────────────────────

    /**
     * Os pagamentos de várias faturas, cada um já do ponto de vista da fatura
     * (quanto lhe tocou, e "junto com" que outras). Uma query pelas ligações
     * das faturas e outra pelas ligações dos pagamentos encontrados.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<InvoicePaymentSummaryDTO>> paymentsForInvoices(Collection<UUID> invoiceIds,
                                                                         boolean includeProofUrl) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        List<InvoicePayment> links = invoicePaymentRepository.findByInvoiceIdIn(invoiceIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        Set<UUID> paymentIds = links.stream()
                .map(link -> link.getPayment().getId())
                .collect(Collectors.toSet());
        Map<UUID, List<InvoicePayment>> linksByPayment = invoicePaymentRepository.findByPaymentIdIn(paymentIds).stream()
                .collect(Collectors.groupingBy(link -> link.getPayment().getId()));

        Map<UUID, List<InvoicePaymentSummaryDTO>> out = new HashMap<>();
        for (InvoicePayment link : links) {
            Payment payment = link.getPayment();
            UUID thisInvoiceId = link.getInvoice().getId();
            List<String> alsoCovers = linksByPayment.getOrDefault(payment.getId(), List.of()).stream()
                    .filter(other -> !other.getInvoice().getId().equals(thisInvoiceId))
                    .map(other -> invoiceLabel(other.getInvoice()))
                    .toList();
            out.computeIfAbsent(thisInvoiceId, key -> new ArrayList<>())
                    .add(new InvoicePaymentSummaryDTO(
                            payment.getId(),
                            payment.getPaidOn(),
                            payment.getMethod().name(),
                            link.getAmount(),
                            payment.getAmount(),
                            payment.getReference(),
                            payment.getNotes(),
                            includeProofUrl ? proofUrl(payment) : null,
                            payment.getProofFilename(),
                            payment.getRegisteredBy(),
                            resolveProfileName(payment.getRegisteredBy()),
                            payment.getRegisteredAt(),
                            alsoCovers));
        }
        out.values().forEach(list -> list.sort(Comparator.comparing(InvoicePaymentSummaryDTO::paidOn)));
        return out;
    }

    @Transactional(readOnly = true)
    public List<InvoicePaymentSummaryDTO> paymentsForInvoice(UUID invoiceId, boolean includeProofUrl) {
        return paymentsForInvoices(List.of(invoiceId), includeProofUrl)
                .getOrDefault(invoiceId, List.of());
    }

    // ── auxiliares ──────────────────────────────────────────────

    /** As notas de crédito não se pagam — reduzem a fatura a que pertencem (§6). */
    private static void rejectIfCreditNote(ConstructionInvoice invoice) {
        if (invoice.getDocumentType() == ConstructionInvoice.DocumentType.CREDIT_NOTE) {
            throw new BusinessException(ErrorCode.INVOICE_IS_CREDIT_NOTE);
        }
    }

    private BigDecimal requireNet(ConstructionInvoice invoice) {
        BigDecimal net = netAmount(invoice);
        if (net == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Preencha o total da fatura antes de a marcar como paga");
        }
        return net;
    }

    private static boolean sameGroup(List<ConstructionInvoice> invoices) {
        ConstructionInvoice first = invoices.get(0);
        for (ConstructionInvoice invoice : invoices) {
            if (invoice.getScope() != first.getScope()) {
                return false;
            }
            if (first.getScope() == ConstructionInvoice.Scope.PROJECT
                    && !Objects.equals(invoice.getEnterpriseId(), first.getEnterpriseId())) {
                return false;
            }
        }
        return true;
    }

    private Payment newPayment(LocalDate paidOn, String method, BigDecimal amount,
                               String reference, String notes) {
        Payment payment = new Payment();
        payment.setPaidOn(paidOn);
        payment.setMethod(parseMethod(method));
        payment.setAmount(amount);
        payment.setReference(trimToNull(reference));
        payment.setNotes(trimToNull(notes));
        payment.setRegisteredAt(OffsetDateTime.now());
        authContext.currentProfileId().ifPresent(payment::setRegisteredBy);
        return payment;
    }

    private static PaymentMethod parseMethod(String value) {
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Método de pagamento desconhecido: " + value);
        }
    }

    private void attachProof(Payment payment, MultipartFile proof, ConstructionInvoice context) {
        if (proof == null || proof.isEmpty()) {
            return;
        }
        String mime = Optional.ofNullable(proof.getContentType()).orElse("");
        if (!PROOF_MIME.contains(mime)) {
            throw new BusinessException(ErrorCode.INVOICE_PAYMENT_PROOF_TYPE);
        }
        if (proof.getSize() > MAX_PROOF_BYTES) {
            throw FileUploadException.sizeExceeded(proof.getOriginalFilename(), proof.getSize(), MAX_PROOF_BYTES);
        }

        byte[] bytes;
        try {
            bytes = proof.getBytes();
        } catch (IOException e) {
            throw StorageException.uploadError(proof.getOriginalFilename(), e);
        }

        String segment = context.getEnterpriseId() != null
                ? context.getEnterpriseId().toString()
                : context.getScope().name().toLowerCase();
        String safeName = storageService.sanitizeFileName(proof.getOriginalFilename());
        String key = String.format("construction-invoices/%s/payments/%s_%s",
                segment, UUID.randomUUID().toString().substring(0, 8), safeName);

        try (InputStream in = new ByteArrayInputStream(bytes)) {
            storageService.upload(BUCKET, key, mime, in);
        } catch (IOException e) {
            throw StorageException.uploadError(proof.getOriginalFilename(), e);
        }

        payment.setProofBucket(BUCKET);
        payment.setProofKey(key);
        payment.setProofFilename(proof.getOriginalFilename());
        payment.setProofMime(mime);
    }

    private void deleteQuietly(String bucket, String key) {
        if (bucket == null || key == null || key.isBlank()) {
            return;
        }
        try {
            storageService.delete(bucket, key);
        } catch (IOException e) {
            log.warn("Não foi possível eliminar {}/{} do storage: {}", bucket, key, e.getMessage());
        }
    }

    private String proofUrl(Payment payment) {
        if (payment.getProofBucket() == null || payment.getProofKey() == null) {
            return null;
        }
        return signedUrls.resolve(payment.getProofBucket(), payment.getProofKey());
    }

    private PaymentResponseDTO toResponseDTO(Payment payment) {
        List<PaymentAllocationDTO> allocations = invoicePaymentRepository.findByPaymentId(payment.getId()).stream()
                .map(link -> new PaymentAllocationDTO(
                        link.getInvoice().getId(),
                        link.getInvoice().getInvoiceNumber(),
                        link.getInvoice().getSupplierName(),
                        link.getAmount()))
                .toList();
        return new PaymentResponseDTO(
                payment.getId(),
                payment.getPaidOn(),
                payment.getMethod().name(),
                payment.getAmount(),
                payment.getReference(),
                payment.getNotes(),
                proofUrl(payment),
                payment.getProofFilename(),
                payment.getRegisteredBy(),
                resolveProfileName(payment.getRegisteredBy()),
                payment.getRegisteredAt(),
                allocations);
    }

    private LeftOutInvoiceDTO leftOutDTO(ConstructionInvoice invoice, BigDecimal remaining) {
        return new LeftOutInvoiceDTO(invoice.getId(), invoice.getInvoiceNumber(),
                invoice.getSupplierName(), remaining);
    }

    private String resolveProfileName(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId).map(Profile::getName).orElse(null);
    }

    private static String invoiceLabel(ConstructionInvoice invoice) {
        if (invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()) {
            return invoice.getInvoiceNumber();
        }
        if (invoice.getSupplierName() != null && !invoice.getSupplierName().isBlank()) {
            return invoice.getSupplierName();
        }
        return "fatura";
    }

    private static String describe(ConstructionInvoice invoice) {
        String number = invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()
                ? "nº " + invoice.getInvoiceNumber()
                : "sem número";
        String supplier = invoice.getSupplierName() != null && !invoice.getSupplierName().isBlank()
                ? " de " + invoice.getSupplierName()
                : "";
        return number + supplier;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
