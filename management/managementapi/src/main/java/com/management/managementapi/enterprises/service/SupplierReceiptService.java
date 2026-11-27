package com.management.managementapi.enterprises.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.receipt.InvoiceReceiptSummaryDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptCandidateInvoiceDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptInvoiceDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptInvoiceLinkDTO;
import com.management.managementapi.enterprises.dto.receipt.ReceiptPaymentRequestDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptCreateDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptPreviewDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptResponseDTO;
import com.management.managementapi.enterprises.dto.receipt.SupplierReceiptUpdateDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.InvoiceReceipt;
import com.management.managementapi.enterprises.model.Payment;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.model.SupplierReceipt;
import com.management.managementapi.enterprises.model.SupplierReceiptPayment;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoiceReceiptRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptPaymentRepository;
import com.management.managementapi.enterprises.repository.SupplierReceiptRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.enterprises.service.AtInvoiceQrService.AtInvoiceData;
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
 * Recibos de fornecedor: um documento ligado às N faturas que liquida.
 *
 * O recibo nunca mexe no estado de pagamento — esse deriva só de
 * {@code invoice_payment}. Ver notes/roadmap/recibos.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SupplierReceiptService {

    private static final Set<String> FILE_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png");
    private static final long MAX_FILE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final String BUCKET = "documents";

    /**
     * O NIF da própria Vilatro. Há faturas importadas com ele no campo do
     * fornecedor (erro de leitura do NIF do adquirente); tratá-lo como emissor
     * bloquearia recibos legítimos por um "conflito" que não existe.
     */
    static final String OWN_COMPANY_NIF = "518849651";

    /** Quanto para trás vão as faturas já pagas no preview. */
    static final int PAID_CANDIDATES_MONTHS = 12;

    /** Os tipos de documento da AT que são recibos. */
    private static final Set<String> RECEIPT_TYPES = Set.of("RC", "RG");

    private final SupplierReceiptRepository receiptRepository;
    private final InvoiceReceiptRepository invoiceReceiptRepository;
    private final SupplierReceiptPaymentRepository receiptPaymentRepository;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final SupplierRepository supplierRepository;
    private final ProfileRepository profileRepository;
    private final PaymentService paymentService;
    private final AtInvoiceQrService qrService;
    private final SupabaseStorageService storageService;
    private final SignedUrlService signedUrls;
    private final AuthContext authContext;

    // ── escrita ─────────────────────────────────────────────────

    /**
     * Grava o recibo, as ligações e — com {@code createPayment} — os pagamentos
     * das faturas por liquidar, tudo numa transação: uma falha no pagamento
     * desfaz o recibo (e tira o ficheiro do bucket).
     */
    public SupplierReceiptResponseDTO create(SupplierReceiptCreateDTO dto, MultipartFile file) {
        Map<UUID, BigDecimal> links = mergeLinks(dto.invoiceLinks());
        List<ConstructionInvoice> invoices = loadInvoices(links.keySet());

        SupplierReceipt receipt = new SupplierReceipt();
        applyFields(receipt, dto.receiptNumber(), dto.receiptDate(), dto.totalAmount(),
                dto.supplierName(), dto.supplierNif(), dto.notes(), invoices);
        rejectIfDuplicate(receipt, null);

        // o duplicado verifica-se antes de subir, para não deixar órfãos no bucket
        storeFile(receipt, file, invoices.get(0));
        receipt.setRegisteredAt(OffsetDateTime.now());
        authContext.currentProfileId().ifPresent(receipt::setRegisteredBy);
        receiptRepository.save(receipt);

        saveLinks(receipt, invoices, links);

        List<String> warnings = dto.createPayment() == null
                ? List.of()
                : payOutstanding(receipt, invoices, links, dto.createPayment());
        return toResponseDTO(receipt, true, warnings);
    }

    /**
     * Paga as faturas ligadas que ainda têm valor em falta, cada uma com
     * {@code min(valor da ligação, em falta)}. Um pagamento por obra (ou por
     * âmbito, fora de obra): o agregado nunca atravessa obras. Sem nada por
     * liquidar, não paga nada e avisa.
     */
    private List<String> payOutstanding(SupplierReceipt receipt, List<ConstructionInvoice> invoices,
                                        Map<UUID, BigDecimal> links, ReceiptPaymentRequestDTO request) {
        Map<UUID, BigDecimal> net = paymentService.netAmounts(invoices);
        Map<UUID, BigDecimal> paid = paymentService.paidSums(links.keySet());

        Map<String, LinkedHashMap<ConstructionInvoice, BigDecimal>> byGroup = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (ConstructionInvoice invoice : invoices) {
            BigDecimal invoiceNet = net.get(invoice.getId());
            if (invoiceNet == null) {
                warnings.add("A fatura " + label(invoice) + " não tem total — não foi paga");
                continue;
            }
            BigDecimal remaining = invoiceNet.subtract(paid.getOrDefault(invoice.getId(), BigDecimal.ZERO));
            BigDecimal amount = links.get(invoice.getId()).min(remaining);
            if (amount.signum() > 0) {
                byGroup.computeIfAbsent(groupKey(invoice), key -> new LinkedHashMap<>()).put(invoice, amount);
            }
        }
        if (byGroup.isEmpty()) {
            warnings.add("Nenhuma das faturas estava por liquidar — não foi registado nenhum pagamento");
            return warnings;
        }

        LocalDate paidOn = request.paidOn() != null ? request.paidOn() : receipt.getReceiptDate();
        String notes = "Pago pelo recibo " + (receipt.getReceiptNumber() != null ? receipt.getReceiptNumber() : "sem nº");
        for (LinkedHashMap<ConstructionInvoice, BigDecimal> group : byGroup.values()) {
            Payment payment = paymentService.registerAllocated(group, paidOn, request.method(),
                    request.reference(), notes);
            receiptPaymentRepository.save(new SupplierReceiptPayment(receipt, payment));
        }
        return warnings;
    }

    /** A mesma chave que o agregado usa para "mesmo grupo": a obra, ou o âmbito fora de obra. */
    private static String groupKey(ConstructionInvoice invoice) {
        return invoice.getEnterpriseId() != null ? invoice.getEnterpriseId().toString() : invoice.getScope().name();
    }

    public SupplierReceiptResponseDTO update(UUID id, SupplierReceiptUpdateDTO dto) {
        SupplierReceipt receipt = findReceipt(id);
        Map<UUID, BigDecimal> links = mergeLinks(dto.invoiceLinks());
        List<ConstructionInvoice> invoices = loadInvoices(links.keySet());

        applyFields(receipt, dto.receiptNumber(), dto.receiptDate(), dto.totalAmount(),
                dto.supplierName(), dto.supplierNif(), dto.notes(), invoices);
        rejectIfDuplicate(receipt, receipt.getId());
        receiptRepository.save(receipt);

        invoiceReceiptRepository.deleteByReceiptId(receipt.getId());
        saveLinks(receipt, invoices, links);
        return toResponseDTO(receipt);
    }

    /** Substitui o ficheiro; o antigo sai do Storage só se a transação vingar. */
    public SupplierReceiptResponseDTO replaceFile(UUID id, MultipartFile file) {
        SupplierReceipt receipt = findReceipt(id);
        String previousBucket = receipt.getFileBucket();
        String previousKey = receipt.getFileKey();

        ConstructionInvoice context = invoiceReceiptRepository.findByReceiptId(id).stream()
                .map(InvoiceReceipt::getInvoice)
                .findFirst()
                .orElse(null);
        storeFile(receipt, file, context);
        receiptRepository.save(receipt);

        deleteAfterCommit(previousBucket, previousKey);
        return toResponseDTO(receipt);
    }

    /**
     * Apaga o recibo, as ligações e o ficheiro. Os pagamentos que ele originou
     * ficam: o dinheiro saiu na mesma, e anula-se à parte.
     */
    public SupplierReceiptResponseDTO delete(UUID id) {
        SupplierReceipt receipt = findReceipt(id);
        SupplierReceiptResponseDTO removed = toResponseDTO(receipt, false, List.of());
        receiptRepository.delete(receipt);
        deleteAfterCommit(receipt.getFileBucket(), receipt.getFileKey());
        return removed;
    }

    // ── leitura ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SupplierReceiptResponseDTO get(UUID id) {
        return toResponseDTO(findReceipt(id));
    }

    /**
     * Lê o QR do recibo sem gravar nada, e devolve as faturas do fornecedor que
     * ele pode cobrir: as por liquidar (de qualquer data) e as pagas dos últimos
     * {@value #PAID_CANDIDATES_MONTHS} meses.
     *
     * {@code linkedInvoiceIds} são as faturas já escolhidas no drawer (a de onde
     * se abriu "Adicionar recibo"). Servem para duas coisas: dar o NIF quando o
     * QR não se lê, e deixar entrar as obras de teste só quando uma delas já é
     * de teste — em uso real nunca aparecem, e a verificação continua possível.
     */
    @Transactional(readOnly = true)
    public SupplierReceiptPreviewDTO preview(MultipartFile file, List<UUID> linkedInvoiceIds) {
        ValidFile valid = readValidFile(file);
        List<ConstructionInvoice> linked = linkedInvoiceIds == null || linkedInvoiceIds.isEmpty()
                ? List.of()
                : invoiceRepository.findAllById(linkedInvoiceIds);

        List<String> warnings = new ArrayList<>();
        Optional<AtInvoiceData> qr = qrService.read(valid.bytes(), valid.mime());
        String nif = qr.map(AtInvoiceData::issuerNif).map(SupplierReceiptService::cleanNif).orElse(null);
        if (OWN_COMPANY_NIF.equals(nif)) {
            nif = null;
        }
        if (qr.isEmpty()) {
            warnings.add("Não foi possível ler o QR do recibo — preencha os campos à mão");
        } else {
            AtInvoiceData data = qr.get();
            if (data.documentType() != null && !RECEIPT_TYPES.contains(data.documentType())) {
                warnings.add("Este documento não é um recibo (tipo " + data.documentType()
                        + ") — uma fatura ou fatura-recibo carrega-se como fatura");
            }
            if (data.warnings() != null) {
                warnings.addAll(data.warnings());
            }
        }
        if (nif == null) {
            nif = linked.stream().map(SupplierReceiptService::effectiveNif)
                    .filter(value -> value != null).findFirst().orElse(null);
        }

        String receiptNumber = qr.map(AtInvoiceData::documentNumber).orElse(null);
        if (nif != null && receiptNumber != null) {
            String normalized = DocumentNumbers.normalize(receiptNumber);
            receiptRepository.findDuplicate(nif, normalized, null).ifPresent(other ->
                    warnings.add("Já existe um recibo deste fornecedor com o número " + other.getReceiptNumber()));
        }

        String supplierName = nif == null ? null
                : supplierRepository.findByNif(nif).map(Supplier::getName).orElse(null);
        boolean includeTest = linked.stream().anyMatch(SupplierReceiptService::isTestInvoice);

        return new SupplierReceiptPreviewDTO(
                qr.isPresent(),
                qr.map(AtInvoiceData::documentType).orElse(null),
                nif,
                supplierName,
                receiptNumber,
                qr.map(AtInvoiceData::invoiceDate).orElse(null),
                qr.map(AtInvoiceData::totalAmount).orElse(null),
                warnings,
                nif == null ? List.of() : candidates(nif, includeTest));
    }

    /** Por liquidar primeiro (mais antigas antes), depois as pagas recentes (mais recentes antes). */
    private List<ReceiptCandidateInvoiceDTO> candidates(String nif, boolean includeTest) {
        List<ConstructionInvoice> invoices = invoiceRepository.findReceiptCandidates(nif, includeTest);
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<UUID, BigDecimal> net = paymentService.netAmounts(invoices);
        Map<UUID, BigDecimal> paid = paymentService.paidSums(invoices.stream().map(ConstructionInvoice::getId).toList());
        LocalDate paidSince = LocalDate.now().minusMonths(PAID_CANDIDATES_MONTHS);

        List<ReceiptCandidateInvoiceDTO> outstanding = new ArrayList<>();
        List<ReceiptCandidateInvoiceDTO> recentlyPaid = new ArrayList<>();
        for (ConstructionInvoice invoice : invoices) {
            BigDecimal invoiceNet = net.get(invoice.getId());
            BigDecimal invoicePaid = paid.getOrDefault(invoice.getId(), BigDecimal.ZERO);
            BigDecimal remaining = invoiceNet == null ? null : invoiceNet.subtract(invoicePaid).max(BigDecimal.ZERO);
            String status = PaymentService.deriveStatus(invoiceNet, invoicePaid).name();
            ReceiptCandidateInvoiceDTO dto = new ReceiptCandidateInvoiceDTO(
                    invoice.getId(), invoice.getInvoiceNumber(), invoice.getInvoiceDate(),
                    invoice.getScope().name(), invoice.getEnterpriseId(), enterpriseName(invoice),
                    invoiceNet, invoicePaid, remaining, status);
            if (!"PAID".equals(status)) {
                outstanding.add(dto);
            } else if (invoice.getInvoiceDate() == null || !invoice.getInvoiceDate().isBefore(paidSince)) {
                recentlyPaid.add(dto);
            }
        }
        // a query vem da mais recente para a mais antiga
        List<ReceiptCandidateInvoiceDTO> out = new ArrayList<>(outstanding.reversed());
        out.addAll(recentlyPaid);
        return out;
    }

    private static boolean isTestInvoice(ConstructionInvoice invoice) {
        Enterprise enterprise = invoice.getEnterprise();
        return enterprise != null && Boolean.TRUE.equals(enterprise.getIsTest());
    }

    /**
     * Os recibos de uma fatura, cada um do ponto de vista dela (quanto lhe toca
     * e que outras cobre). Só no detalhe da fatura: assina o URL do ficheiro.
     */
    @Transactional(readOnly = true)
    public List<InvoiceReceiptSummaryDTO> receiptsForInvoice(UUID invoiceId) {
        List<InvoiceReceipt> links = invoiceReceiptRepository.findByInvoiceId(invoiceId);
        if (links.isEmpty()) {
            return List.of();
        }
        Set<UUID> receiptIds = links.stream().map(link -> link.getReceipt().getId()).collect(Collectors.toSet());
        Map<UUID, List<InvoiceReceipt>> linksByReceipt = invoiceReceiptRepository.findByReceiptIdIn(receiptIds).stream()
                .collect(Collectors.groupingBy(link -> link.getReceipt().getId()));

        return links.stream()
                .map(link -> {
                    SupplierReceipt receipt = link.getReceipt();
                    List<InvoiceReceipt> all = linksByReceipt.getOrDefault(receipt.getId(), List.of());
                    List<InvoiceReceiptSummaryDTO.CoveredInvoice> alsoCovers = all.stream()
                            .filter(other -> !other.getInvoice().getId().equals(invoiceId))
                            .map(other -> new InvoiceReceiptSummaryDTO.CoveredInvoice(
                                    other.getInvoice().getId(),
                                    other.getInvoice().getInvoiceNumber(),
                                    enterpriseName(other.getInvoice())))
                            .toList();
                    return new InvoiceReceiptSummaryDTO(
                            receipt.getId(),
                            receipt.getReceiptNumber(),
                            receipt.getReceiptDate(),
                            receipt.getTotalAmount(),
                            link.getAmount(),
                            fileUrl(receipt),
                            receipt.getFileFilename(),
                            alsoCovers,
                            receipt.getTotalAmount().subtract(sumLinks(all)));
                })
                .sorted(Comparator.comparing(InvoiceReceiptSummaryDTO::receiptDate))
                .toList();
    }

    // ── regras ──────────────────────────────────────────────────

    /** Ligações repetidas no pedido juntam-se (soma-se o valor); a ordem do pedido mantém-se. */
    static Map<UUID, BigDecimal> mergeLinks(List<ReceiptInvoiceLinkDTO> links) {
        if (links == null || links.isEmpty()) {
            throw new BusinessException(ErrorCode.RECEIPT_NO_INVOICES);
        }
        Map<UUID, BigDecimal> merged = new LinkedHashMap<>();
        for (ReceiptInvoiceLinkDTO link : links) {
            merged.merge(link.invoiceId(), link.amount(), BigDecimal::add);
        }
        return merged;
    }

    /** As faturas pela ordem pedida; todas têm de existir e nenhuma pode ser NC. */
    private List<ConstructionInvoice> loadInvoices(Collection<UUID> ids) {
        Map<UUID, ConstructionInvoice> found = invoiceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ConstructionInvoice::getId, Function.identity()));
        List<ConstructionInvoice> ordered = new ArrayList<>();
        for (UUID id : ids) {
            ConstructionInvoice invoice = found.get(id);
            if (invoice == null) {
                throw ResourceNotFoundException.constructionInvoice(id.toString());
            }
            if (invoice.getDocumentType() == ConstructionInvoice.DocumentType.CREDIT_NOTE) {
                throw new BusinessException(ErrorCode.RECEIPT_CREDIT_NOTE);
            }
            ordered.add(invoice);
        }
        return ordered;
    }

    private void applyFields(SupplierReceipt receipt, String number, LocalDate date,
                             BigDecimal total, String supplierName, String supplierNif, String notes,
                             List<ConstructionInvoice> invoices) {
        rejectIfMixedSuppliers(supplierNif, supplierName, invoices);

        receipt.setReceiptNumber(trimToNull(number));
        String normalized = DocumentNumbers.normalize(number);
        receipt.setReceiptNumberNormalized(normalized.isEmpty() ? null : normalized);
        receipt.setReceiptDate(date);
        receipt.setTotalAmount(total);
        receipt.setNotes(trimToNull(notes));

        // sem fornecedor no pedido, herda-se da primeira fatura que o tenha
        String nif = trimToNull(supplierNif);
        if (nif == null) {
            nif = invoices.stream().map(SupplierReceiptService::effectiveNif)
                    .filter(value -> value != null).findFirst().orElse(null);
        }
        String name = trimToNull(supplierName);
        if (name == null) {
            name = invoices.stream().map(invoice -> trimToNull(invoice.getSupplierName()))
                    .filter(value -> value != null).findFirst().orElse(null);
        }
        receipt.setSupplierNif(nif);
        receipt.setSupplierName(name);
    }

    /**
     * Um recibo é de um só fornecedor. Só se bloqueia o conflito real: dois NIFs
     * diferentes, ou — quando um dos lados não tem NIF — dois nomes diferentes.
     * Quem não tem NIF nem nome é compatível com tudo (dados por completar do
     * import), e o NIF da própria Vilatro não conta como emissor.
     */
    static void rejectIfMixedSuppliers(String receiptNif, String receiptName, List<ConstructionInvoice> invoices) {
        List<Party> parties = new ArrayList<>();
        parties.add(new Party(cleanNif(receiptNif), normalizeName(receiptName)));
        for (ConstructionInvoice invoice : invoices) {
            parties.add(new Party(effectiveNif(invoice), normalizeName(invoice.getSupplierName())));
        }
        for (int i = 0; i < parties.size(); i++) {
            for (int j = i + 1; j < parties.size(); j++) {
                if (parties.get(i).conflictsWith(parties.get(j))) {
                    throw new BusinessException(ErrorCode.RECEIPT_MIXED_SUPPLIERS);
                }
            }
        }
    }

    private record Party(String nif, String name) {
        boolean conflictsWith(Party other) {
            if (nif != null && other.nif != null) {
                return !nif.equals(other.nif);
            }
            return name != null && other.name != null && !name.equals(other.name);
        }
    }

    private void rejectIfDuplicate(SupplierReceipt receipt, UUID excludeId) {
        if (receipt.getSupplierNif() == null || receipt.getReceiptNumberNormalized() == null) {
            return;
        }
        receiptRepository.findDuplicate(receipt.getSupplierNif(), receipt.getReceiptNumberNormalized(), excludeId)
                .ifPresent(other -> {
                    throw new BusinessException(ErrorCode.RECEIPT_DUPLICATE, String.format(
                            "Já existe um recibo deste fornecedor com o número %s (de %s)",
                            other.getReceiptNumber(), other.getReceiptDate()));
                });
    }

    private void saveLinks(SupplierReceipt receipt, List<ConstructionInvoice> invoices, Map<UUID, BigDecimal> amounts) {
        List<InvoiceReceipt> links = invoices.stream()
                .map(invoice -> new InvoiceReceipt(receipt, invoice, amounts.get(invoice.getId())))
                .toList();
        invoiceReceiptRepository.saveAll(links);
    }

    // ── ficheiro ────────────────────────────────────────────────

    /**
     * Valida e sobe o ficheiro, e põe-no no recibo. Se a transação for desfeita
     * depois de subir (uma ligação inválida, o pagamento que falha), o ficheiro
     * sai do bucket — senão ficava órfão, sem linha que o alcance.
     */
    private void storeFile(SupplierReceipt receipt, MultipartFile file, ConstructionInvoice context) {
        ValidFile valid = readValidFile(file);
        String key = String.format("receipts/%s/%s_%s",
                storageSegment(context),
                UUID.randomUUID().toString().substring(0, 8),
                storageService.sanitizeFileName(valid.filename()));
        try (InputStream in = new ByteArrayInputStream(valid.bytes())) {
            storageService.upload(BUCKET, key, valid.mime(), in);
        } catch (IOException e) {
            throw StorageException.uploadError(valid.filename(), e);
        }
        deleteOnRollback(BUCKET, key);

        receipt.setFileBucket(BUCKET);
        receipt.setFileKey(key);
        receipt.setFileFilename(valid.filename());
        receipt.setFileMime(valid.mime());
    }

    private record ValidFile(byte[] bytes, String mime, String filename) {}

    /** Ficheiro obrigatório, PDF/JPG/PNG, até 25 MB — lido uma vez. */
    private static ValidFile readValidFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw FileUploadException.empty(file == null ? "recibo" : file.getOriginalFilename());
        }
        String mime = Optional.ofNullable(file.getContentType()).orElse("");
        if (!FILE_MIME.contains(mime)) {
            throw FileUploadException.invalidType(file.getOriginalFilename(), mime, "PDF, JPG ou PNG");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw FileUploadException.sizeExceeded(file.getOriginalFilename(), file.getSize(), MAX_FILE_BYTES);
        }
        try {
            return new ValidFile(file.getBytes(), mime, file.getOriginalFilename());
        } catch (IOException e) {
            throw StorageException.uploadError(file.getOriginalFilename(), e);
        }
    }

    /** Obra da 1.ª fatura ligada; sem obra, o âmbito em minúsculas (como a prova de pagamento). */
    private static String storageSegment(ConstructionInvoice context) {
        if (context == null) {
            return "unlinked";
        }
        return context.getEnterpriseId() != null
                ? context.getEnterpriseId().toString()
                : context.getScope().name().toLowerCase(Locale.ROOT);
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

    /** Apaga o ficheiro só se a transação vingar. Fora de uma transação (testes) apaga já. */
    private void deleteAfterCommit(String bucket, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(bucket, key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(bucket, key);
            }
        });
    }

    /** Apaga o ficheiro acabado de subir se a transação for desfeita. */
    private void deleteOnRollback(String bucket, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(bucket, key);
                }
            }
        });
    }

    private String fileUrl(SupplierReceipt receipt) {
        return signedUrls.resolve(receipt.getFileBucket(), receipt.getFileKey());
    }

    // ── DTOs ────────────────────────────────────────────────────

    private SupplierReceiptResponseDTO toResponseDTO(SupplierReceipt receipt) {
        return toResponseDTO(receipt, true, List.of());
    }

    private SupplierReceiptResponseDTO toResponseDTO(SupplierReceipt receipt, boolean includeFileUrl,
                                                     List<String> warnings) {
        List<InvoiceReceipt> links = invoiceReceiptRepository.findByReceiptId(receipt.getId());
        List<ReceiptInvoiceDTO> invoices = links.stream()
                .map(link -> {
                    ConstructionInvoice invoice = link.getInvoice();
                    return new ReceiptInvoiceDTO(
                            invoice.getId(),
                            invoice.getInvoiceNumber(),
                            invoice.getInvoiceDate(),
                            invoice.getScope().name(),
                            invoice.getEnterpriseId(),
                            enterpriseName(invoice),
                            link.getAmount());
                })
                .toList();
        BigDecimal linkedSum = sumLinks(links);
        List<UUID> paymentIds = receiptPaymentRepository.findByReceiptId(receipt.getId()).stream()
                .map(SupplierReceiptPayment::getPayment)
                .map(payment -> payment.getId())
                .toList();
        return new SupplierReceiptResponseDTO(
                receipt.getId(),
                receipt.getSupplierName(),
                receipt.getSupplierNif(),
                receipt.getReceiptNumber(),
                receipt.getReceiptDate(),
                receipt.getTotalAmount(),
                receipt.getNotes(),
                includeFileUrl ? fileUrl(receipt) : null,
                receipt.getFileFilename(),
                receipt.getFileMime(),
                invoices,
                linkedSum,
                receipt.getTotalAmount().subtract(linkedSum),
                paymentIds,
                receipt.getRegisteredBy(),
                resolveProfileName(receipt.getRegisteredBy()),
                receipt.getRegisteredAt(),
                warnings);
    }

    // ── auxiliares ──────────────────────────────────────────────

    private SupplierReceipt findReceipt(UUID id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RECEIPT_NOT_FOUND));
    }

    private static BigDecimal sumLinks(List<InvoiceReceipt> links) {
        return links.stream().map(InvoiceReceipt::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String label(ConstructionInvoice invoice) {
        return invoice.getInvoiceNumber() != null && !invoice.getInvoiceNumber().isBlank()
                ? invoice.getInvoiceNumber()
                : "sem número";
    }

    private static String enterpriseName(ConstructionInvoice invoice) {
        Enterprise enterprise = invoice.getEnterprise();
        return enterprise == null ? null : enterprise.getName();
    }

    /** O NIF do fornecedor da fatura, sem espaços, e null se for o da própria Vilatro. */
    private static String effectiveNif(ConstructionInvoice invoice) {
        String nif = cleanNif(invoice.getSupplierNif());
        return OWN_COMPANY_NIF.equals(nif) ? null : nif;
    }

    private static String cleanNif(String nif) {
        if (nif == null) {
            return null;
        }
        String digits = nif.replaceAll("\\s", "");
        return digits.isEmpty() ? null : digits;
    }

    /** Nome para comparar: sem espaços a mais, minúsculas, sem acentos. */
    static String normalizeName(String name) {
        String trimmed = trimToNull(name);
        if (trimmed == null) {
            return null;
        }
        String noAccents = Normalizer.normalize(trimmed, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noAccents.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String resolveProfileName(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId).map(Profile::getName).orElse(null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
