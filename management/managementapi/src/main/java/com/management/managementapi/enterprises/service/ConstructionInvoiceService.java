package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.ConstructionInvoiceUpsertDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceRegisterDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ConstructionInvoiceResponseDTO;
import com.management.managementapi.enterprises.dto.invoice.response.DuplicateInvoiceRefDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceDocumentDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoicePreviewResultDTO;
import com.management.managementapi.enterprises.dto.invoice.response.InvoiceUploadResultDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.FileUploadException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.exeption.StorageException;
import com.management.managementapi.integrations.supabase.SignedUrlService;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.model.Profile;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Faturas de obra: o documento e a sua afetação a uma rubrica.
 *
 * O ponto de partida desta classe é que <b>registar e classificar são momentos
 * diferentes</b>. Quem chega da obra com quinze faturas carrega-as todas de uma
 * vez, sem decidir nada; a classificação faz-se depois, na caixa de entrada.
 * Daí que {@link #upload} nunca falhe por dados em falta e que a exigência de
 * data e total só apareça em {@link #allocate}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ConstructionInvoiceService {

    private static final Set<String> INVOICE_MIME = Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png");
    private static final long MAX_INVOICE_BYTES = 25L * 1024 * 1024; // 25 MB
    private static final String BUCKET = "documents";

    private final ConstructionInvoiceRepository repository;
    private final ConstructionInvoiceDocumentRepository documentRepository;
    private final ConstructionExpenseRepository expenseRepository;
    private final ConstructionBudgetItemRepository budgetItemRepository;
    private final EnterpriseRepository enterpriseRepository;
    private final SupplierRepository supplierRepository;
    private final ProfileRepository profileRepository;
    private final SupabaseStorageService storageService;
    private final SignedUrlService signedUrls;
    private final AtInvoiceQrService qrService;
    private final InvoiceThumbnailService thumbnailService;
    private final InvoiceCompressionService compressionService;
    private final AuthContext authContext;
    private final NotificationService notifications;
    private final PaymentService paymentService;

    // ── carregar ──────────────────────────────────────────────

    /**
     * Lê o QR e verifica duplicados sem gravar nada — o "Enviar" do
     * carregamento em duas fases. Primeiro mostra-se o que cada ficheiro
     * trouxe (e se colide com uma fatura já registada), só depois é que
     * {@link #upload} grava de facto. Não toca no Storage nem na base de
     * dados: por isso corre em transação só de leitura, e pode chamar-se
     * quantas vezes for preciso sem custar nada.
     */
    @Transactional(readOnly = true)
    public InvoicePreviewResultDTO preview(UUID enterpriseId, MultipartFile file) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_ENTERPRISE_NOT_FOUND));

        validateFile(file);
        byte[] original = readBytes(file);
        String mime = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");

        // Fatura de trabalho, nunca gravada — só para reaproveitar a mesma
        // lógica de leitura do QR e de deteção de duplicado que o upload usa.
        ConstructionInvoice draft = new ConstructionInvoice();
        draft.setEnterprise(enterprise);
        String checksum = sha256Hex(original);

        Optional<AtInvoiceQrService.AtInvoiceData> qr = qrService.read(original, mime);
        qr.ifPresent(data -> applyQrData(draft, data));

        boolean duplicate = false;
        String duplicateMessage = null;
        try {
            rejectIfDuplicate(draft, checksum);
        } catch (BusinessException e) {
            duplicate = true;
            duplicateMessage = e.getMessage();
        }

        return new InvoicePreviewResultDTO(
                qr.isPresent(),
                duplicate,
                duplicateMessage,
                draft.getSupplierName(),
                draft.getSupplierNif(),
                draft.getInvoiceNumber(),
                draft.getInvoiceDate(),
                draft.getTotalAmount(),
                draft.needsReview(),
                qr.map(AtInvoiceQrService.AtInvoiceData::warnings).orElse(List.of()));
    }

    /**
     * Cria uma fatura a partir do ficheiro: lê o QR da AT, gera a miniatura e
     * grava. É o "Guardar" do carregamento em duas fases — o cliente já
     * mostrou o resultado de {@link #preview} antes de chegar aqui, mas este
     * método relê e revalida tudo de novo: nunca confia cegamente no que foi
     * mostrado, porque outra fatura pode ter entrado entretanto.
     *
     * Nunca rejeita por falta de dados. Sem QR legível a fatura entra na mesma,
     * marcada como "por rever", e alguém completa os campos mais tarde.
     *
     * O QR lê-se sempre do ficheiro <b>original</b>, antes de qualquer
     * compressão — ver {@link InvoiceCompressionService}. Comprimir primeiro
     * já custou faturas que liam bem em qualidade total.
     */
    public InvoiceUploadResultDTO upload(UUID enterpriseId, MultipartFile file) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_ENTERPRISE_NOT_FOUND));

        validateFile(file);
        byte[] original = readBytes(file);
        String mime = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setEnterprise(enterprise);
        String checksum = sha256Hex(original);
        authContext.currentProfileId().ifPresent(invoice::setCreatedBy);

        Optional<AtInvoiceQrService.AtInvoiceData> qr = qrService.read(original, mime);
        qr.ifPresent(data -> applyQrData(invoice, data));

        rejectIfDuplicate(invoice, checksum);

        StoredContent stored = compressIfReadable(original, mime, file.getOriginalFilename(), qr.isPresent());
        // A fatura grava-se primeiro: o documento precisa do id dela para a FK.
        ConstructionInvoice saved = repository.save(invoice);
        addDocument(saved, enterpriseId, stored, checksum, (long) original.length);

        // Uma fatura acabada de carregar está sempre por classificar — registar e
        // classificar são momentos diferentes, é o princípio desta classe. Avisa-se
        // quem carregou, para não ficar esquecida na caixa de entrada.
        authContext.currentProfileId().ifPresent(uploader -> notifications.notify(
                uploader,
                Notification.TYPE_INVOICE_PENDING,
                "Fatura por classificar",
                descreveFatura(saved),
                "/backoffice/empreendimentos/" + enterpriseId + "/invoices",
                saved.getId()));

        return new InvoiceUploadResultDTO(
                toResponseDTO(saved, false),
                qr.isPresent(),
                List.of(),
                qr.map(AtInvoiceQrService.AtInvoiceData::warnings).orElse(List.of()));
    }

    /**
     * Regista uma fatura <b>sem ficheiro</b>: a que está por pedir, por
     * imprimir, ou que só existe em papel. Até à V24 isto não era possível — o
     * ficheiro era obrigatório — e por isso este trabalho ficava todo no Excel.
     *
     * O documento junta-se depois, por {@link #addDocument}.
     */
    public ConstructionInvoiceResponseDTO register(InvoiceRegisterDTO dto) {
        ConstructionInvoice.Scope scope = parseScope(dto.scope());

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setScope(scope);
        invoice.setEnterprise(resolveEnterpriseFor(scope, dto.enterpriseId()));
        invoice.setSupplierName(trimToNull(dto.supplierName()));
        invoice.setSupplierNif(trimToNull(dto.supplierNif()));
        invoice.setInvoiceNumber(trimToNull(dto.invoiceNumber()));
        invoice.setInvoiceAtcud(trimToNull(dto.invoiceAtcud()));
        invoice.setInvoiceDate(dto.invoiceDate());
        invoice.setTotalAmount(dto.totalAmount());
        invoice.setDescription(trimToNull(dto.description()));
        invoice.setPossibleEnterprises(trimToNull(dto.possibleEnterprises()));
        invoice.setAskWhom(trimToNull(dto.askWhom()));
        invoice.setNotes(trimToNull(dto.notes()));
        invoice.setDocumentStatus(registerStatus(dto.documentStatus()));
        authContext.currentProfileId().ifPresent(invoice::setCreatedBy);

        // Sem ficheiro não há checksum, mas o ATCUD e o par (NIF, número) podem
        // vir escritos à mão — e são globais desde a V29.
        rejectIfDuplicate(invoice, null);

        return toResponseDTO(repository.save(invoice), false);
    }

    /**
     * Junta mais um documento a uma fatura que já existe: a foto tirada na obra
     * e o PDF que o fornecedor mandou depois são o mesmo documento fiscal.
     *
     * O QR do ficheiro novo <b>preenche o que está vazio</b> e apenas
     * <b>avisa</b> quando diverge do que já lá está — nunca sobrepõe uma
     * correção feita à mão.
     */
    public InvoiceUploadResultDTO addDocument(UUID invoiceId, MultipartFile file) {
        ConstructionInvoice invoice = getById(invoiceId);

        validateFile(file);
        byte[] original = readBytes(file);
        String mime = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        String checksum = sha256Hex(original);

        // Só o ficheiro é verificado: a identidade da fatura é a que já lá está.
        documentRepository.findByChecksum(checksum, invoiceId).stream()
                .findFirst()
                .ifPresent(document -> {
                    ConstructionInvoice other = document.getInvoice();
                    throw new BusinessException(ErrorCode.INVOICE_DUPLICATE_FILE, String.format(
                            "Este ficheiro já foi carregado em %s (%s, %s)",
                            whereItIs(other), describe(other), dateOf(other)));
                });

        Optional<AtInvoiceQrService.AtInvoiceData> qr = qrService.read(original, mime);
        List<String> warnings = qr.map(data -> {
            List<String> divergences = qrDivergences(invoice, data);
            applyQrData(invoice, data);
            return divergences;
        }).orElse(List.of());

        StoredContent stored = compressIfReadable(original, mime, file.getOriginalFilename(), qr.isPresent());
        addDocument(invoice, invoice.getEnterpriseId(), stored, checksum, (long) original.length);
        ConstructionInvoice saved = repository.save(invoice);

        return new InvoiceUploadResultDTO(
                toResponseDTO(saved, true),
                qr.isPresent(),
                List.of(),
                warnings);
    }

    /**
     * O que o QR do ficheiro novo diz de diferente do que a fatura já tinha
     * preenchido. Não altera nada — quem decide é quem está a olhar.
     */
    private static List<String> qrDivergences(ConstructionInvoice invoice, AtInvoiceQrService.AtInvoiceData data) {
        List<String> divergences = new ArrayList<>();
        if (!isBlank(invoice.getSupplierNif()) && data.issuerNif() != null
                && !invoice.getSupplierNif().equals(data.issuerNif())) {
            divergences.add("O QR deste ficheiro traz o NIF " + data.issuerNif()
                    + ", mas a fatura tem " + invoice.getSupplierNif() + ".");
        }
        if (!isBlank(invoice.getInvoiceNumber()) && data.documentNumber() != null
                && !normalizeDocumentNumber(invoice.getInvoiceNumber())
                        .equals(normalizeDocumentNumber(data.documentNumber()))) {
            divergences.add("O QR deste ficheiro traz o número " + data.documentNumber()
                    + ", mas a fatura tem " + invoice.getInvoiceNumber() + ".");
        }
        if (invoice.getTotalAmount() != null && data.totalAmount() != null
                && invoice.getTotalAmount().compareTo(data.totalAmount()) != 0) {
            divergences.add("O QR deste ficheiro traz o total " + data.totalAmount()
                    + ", mas a fatura tem " + invoice.getTotalAmount() + ".");
        }
        return divergences;
    }

    private static ConstructionInvoice.Scope parseScope(String value) {
        try {
            return ConstructionInvoice.Scope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVOICE_SCOPE_UNKNOWN, "Âmbito desconhecido: " + value);
        }
    }

    /**
     * O par scope/obra é o mesmo que o check da V26 impõe na base de dados —
     * verificado aqui para o erro sair com uma mensagem em vez de uma violação
     * de constraint.
     */
    private Enterprise resolveEnterpriseFor(ConstructionInvoice.Scope scope, UUID enterpriseId) {
        if (scope == ConstructionInvoice.Scope.PROJECT) {
            if (enterpriseId == null) {
                throw new BusinessException(ErrorCode.INVOICE_SCOPE_REQUIRES_ENTERPRISE);
            }
            return enterpriseRepository.findById(enterpriseId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_ENTERPRISE_NOT_FOUND));
        }
        if (enterpriseId != null) {
            throw new BusinessException(ErrorCode.INVOICE_SCOPE_FORBIDS_ENTERPRISE);
        }
        return null;
    }

    private static ConstructionInvoice.DocumentStatus registerStatus(String value) {
        if (isBlank(value)) {
            return ConstructionInvoice.DocumentStatus.MISSING;
        }
        ConstructionInvoice.DocumentStatus status = parseDocumentStatus(value);
        // ARCHIVED quer dizer "o papel está cá"; sem ficheiro seria mentira.
        if (status == ConstructionInvoice.DocumentStatus.ARCHIVED) {
            throw new BusinessException(ErrorCode.INVOICE_STATUS_REQUIRES_NO_DOCUMENT);
        }
        return status;
    }

    /**
     * As listas que não são de obra nenhuma: a quarentena ("Por identificar") e
     * as despesas da empresa. A quarentena vê-se da mais antiga para a mais
     * recente — quanto mais tempo lá está, mais urgente é.
     */
    @Transactional(readOnly = true)
    public Page<ConstructionInvoiceResponseDTO> searchByScope(ConstructionInvoice.Scope scope,
                                                              Boolean outstanding, String q, Pageable pageable) {
        Page<ConstructionInvoice> page = repository.searchByScope(
                scope, outstanding, isBlank(q) ? null : q.trim(), pageable);

        List<UUID> ids = page.getContent().stream().map(ConstructionInvoice::getId).toList();
        Map<UUID, ConstructionExpense> allocations = loadAllocations(ids);
        Map<UUID, List<ConstructionInvoiceDocument>> documents = loadDocuments(ids);
        Map<UUID, List<InvoicePaymentSummaryDTO>> payments = paymentService.paymentsForInvoices(ids, false);

        return page.map(invoice -> toResponseDTO(invoice,
                allocations.get(invoice.getId()),
                documents.getOrDefault(invoice.getId(), List.of()),
                payments.getOrDefault(invoice.getId(), List.of()),
                false));
    }

    /** Substitui o ficheiro de uma fatura já registada, relendo o QR e a miniatura. */
    public InvoiceUploadResultDTO replaceFile(UUID id, MultipartFile file) {
        ConstructionInvoice invoice = getById(id);

        validateFile(file);
        byte[] original = readBytes(file);
        String mime = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");

        // Substituir o ficheiro é largar todos os documentos e pôr um só no lugar.
        // Acrescentar sem apagar é o que faz POST /{id}/documents.
        deleteDocuments(invoice);
        String checksum = sha256Hex(original);

        Optional<AtInvoiceQrService.AtInvoiceData> qr = qrService.read(original, mime);
        // Só preenche o que está vazio: correções feitas à mão não são deitadas
        // fora por se ter substituído a digitalização.
        qr.ifPresent(data -> applyQrData(invoice, data));

        UUID enterpriseId = invoice.getEnterpriseId();
        rejectIfDuplicate(invoice, checksum);

        StoredContent stored = compressIfReadable(original, mime, file.getOriginalFilename(), qr.isPresent());
        ConstructionInvoice saved = repository.save(invoice);
        addDocument(saved, enterpriseId, stored, checksum, (long) original.length);

        return new InvoiceUploadResultDTO(
                toResponseDTO(saved, true),
                qr.isPresent(),
                List.of(),
                qr.map(AtInvoiceQrService.AtInvoiceData::warnings).orElse(List.of()));
    }

    /**
     * O que se lê na notificação. Sem QR legível não há fornecedor nem número, e
     * nesse caso é mais honesto dizê-lo do que mostrar uma linha vazia.
     */
    private String descreveFatura(ConstructionInvoice invoice) {
        String fornecedor = invoice.getSupplierName() != null ? invoice.getSupplierName()
                : invoice.getSupplierNif() != null ? "NIF " + invoice.getSupplierNif()
                : null;
        String numero = invoice.getInvoiceNumber();

        if (fornecedor == null && numero == null) {
            return "Sem dados lidos do QR — é preciso preencher à mão.";
        }
        if (numero == null) return fornecedor;
        if (fornecedor == null) return numero;
        return fornecedor + " · " + numero;
    }

    private record StoredContent(byte[] content, String mimeType, String filename) {}

    /**
     * Comprime para guardar — mas só quando o QR já foi lido com sucesso.
     * Sem QR legível o original fica intacto: é a melhor hipótese para
     * revisão manual ou para um {@link #rescan} futuro.
     */
    private StoredContent compressIfReadable(byte[] original, String mime, String originalFilename, boolean qrRead) {
        if (!qrRead) {
            return new StoredContent(original, mime, originalFilename);
        }
        return compressionService.compress(original, mime)
                .map(result -> new StoredContent(result.content(), result.mimeType(), toJpegName(originalFilename)))
                .orElseGet(() -> new StoredContent(original, mime, originalFilename));
    }

    /** O conteúdo passou a JPEG; a extensão tem de acompanhar. */
    private static String toJpegName(String name) {
        if (name == null) {
            return "fatura.jpg";
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + ".jpg";
    }

    /**
     * Relê o QR do documento já arquivado e repõe os campos fiscais.
     *
     * É o desfazer de uma correção manual errada. Ao contrário de
     * {@link #applyQrData}, aqui o QR <b>sobrepõe-se</b> ao que está gravado —
     * é exatamente isso que se está a pedir. O nome do fornecedor e as notas
     * ficam intactos: não vêm do QR, são escritos por gente.
     *
     * Não toca no ficheiro nem na miniatura; para trocar a digitalização é
     * {@link #replaceFile}.
     */
    public InvoiceUploadResultDTO rescan(UUID id) {
        ConstructionInvoice invoice = getById(id);

        ConstructionInvoiceDocument document = primaryDocument(invoice.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_FILE_UNAVAILABLE));
        byte[] content = downloadStoredDocument(document);
        String mime = Optional.ofNullable(document.getMimeType()).orElse("application/octet-stream");

        // Sem QR legível não há nada por onde repor. Recusar é melhor do que
        // apagar o que lá está e deixar a fatura pior do que estava.
        AtInvoiceQrService.AtInvoiceData data = qrService.read(content, mime)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_QR_UNREADABLE));

        invoice.setSupplierNif(data.issuerNif());
        invoice.setInvoiceNumber(data.documentNumber());
        invoice.setInvoiceAtcud(data.atcud());
        invoice.setInvoiceDate(data.invoiceDate());
        invoice.setTotalAmount(data.totalAmount());
        invoice.setTaxableAmount(data.taxableAmount());
        invoice.setTaxAmount(data.taxAmount());

        Optional<ConstructionExpense> allocation = findAllocation(invoice.getId());

        // Mesma regra do update(): um lançamento não pode ficar sem data ou
        // total. Acontece quando o QR traz esses campos ilegíveis (ver os
        // warnings de AtInvoiceQrService) — a transação reverte e a fatura fica
        // como estava.
        if (allocation.isPresent() && invoice.needsReview()) {
            throw new BusinessException(ErrorCode.INVOICE_INCOMPLETE);
        }

        ConstructionInvoice saved = repository.save(invoice);
        allocation.ifPresent(expense -> syncExpenseFromInvoice(expense, saved));

        return new InvoiceUploadResultDTO(
                toResponseDTO(saved, true),
                true,
                findDuplicates(saved.getInvoiceAtcud(), saved.getId()),
                data.warnings());
    }

    /** O ficheiro que está no Storage, de volta em memória para ser relido. */
    private byte[] downloadStoredDocument(ConstructionInvoiceDocument document) {
        if (document.getBucket() == null || isBlank(document.getStorageKey())) {
            throw new BusinessException(ErrorCode.INVOICE_FILE_UNAVAILABLE);
        }
        try {
            return storageService.download(document.getBucket(), document.getStorageKey());
        } catch (IOException e) {
            log.warn("Não foi possível obter o ficheiro do documento {}: {}", document.getId(), e.getMessage());
            throw new BusinessException(ErrorCode.INVOICE_FILE_UNAVAILABLE);
        }
    }

    /**
     * O documento que representa a fatura quando é preciso um só: o mais antigo,
     * que é o que foi carregado no registo. É o que a lista mostra e o que o
     * {@link #rescan} relê.
     */
    private Optional<ConstructionInvoiceDocument> primaryDocument(UUID invoiceId) {
        return documentRepository.findFirstByInvoiceIdOrderByUploadedAtAsc(invoiceId);
    }

    /**
     * Preenche a partir do QR apenas os campos ainda vazios — nunca sobrepõe o
     * que alguém corrigiu à mão.
     */
    private void applyQrData(ConstructionInvoice invoice, AtInvoiceQrService.AtInvoiceData data) {
        if (isBlank(invoice.getSupplierNif()))   invoice.setSupplierNif(data.issuerNif());
        if (isBlank(invoice.getInvoiceNumber())) invoice.setInvoiceNumber(data.documentNumber());
        if (isBlank(invoice.getInvoiceAtcud()))  invoice.setInvoiceAtcud(data.atcud());
        if (invoice.getInvoiceDate() == null)    invoice.setInvoiceDate(data.invoiceDate());
        if (invoice.getTotalAmount() == null)    invoice.setTotalAmount(data.totalAmount());
        if (invoice.getTaxableAmount() == null)  invoice.setTaxableAmount(data.taxableAmount());
        if (invoice.getTaxAmount() == null)      invoice.setTaxAmount(data.taxAmount());

        applyKnownSupplierName(invoice);
    }

    /**
     * O QR da AT identifica o emitente <b>só pelo NIF</b> — não existe campo
     * para o nome. Se esse NIF já estiver no catálogo, a fatura nasce logo com
     * o nome da empresa em vez de ficar anónima à espera de alguém o escrever.
     *
     * Ver {@link Supplier} e {@link SupplierService}.
     */
    private void applyKnownSupplierName(ConstructionInvoice invoice) {
        if (!isBlank(invoice.getSupplierName()) || isBlank(invoice.getSupplierNif())) {
            return;
        }
        supplierRepository.findByNif(invoice.getSupplierNif().trim())
                .ifPresent(supplier -> invoice.setSupplierName(supplier.getName()));
    }

    // ── leitura ───────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConstructionInvoice getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.constructionInvoice(id.toString()));
    }

    @Transactional(readOnly = true)
    public ConstructionInvoiceResponseDTO getDetail(UUID id) {
        return toResponseDTO(getById(id), true);
    }

    /**
     * A caixa de entrada. O cliente abre-a com {@code allocated = false}, que é
     * o que interessa no dia-a-dia: o que entrou e ainda não foi classificado.
     */
    @Transactional(readOnly = true)
    public Page<ConstructionInvoiceResponseDTO> search(UUID enterpriseId, Boolean allocated, Boolean needsReview,
                                                       Boolean outstanding, Boolean sentToAccountant,
                                                       LocalDate from, LocalDate to,
                                                       String q, Pageable pageable) {
        String query = isBlank(q) ? null : q.trim();
        Page<ConstructionInvoice> page = repository.search(
                enterpriseId, allocated, needsReview, outstanding, sentToAccountant, from, to, query, pageable);

        // Uma query para as afetações da página toda, em vez de uma por linha.
        List<UUID> ids = page.getContent().stream().map(ConstructionInvoice::getId).toList();
        Map<UUID, ConstructionExpense> allocations = loadAllocations(ids);
        Map<UUID, List<ConstructionInvoiceDocument>> documents = loadDocuments(ids);
        Map<UUID, List<InvoicePaymentSummaryDTO>> payments = paymentService.paymentsForInvoices(ids, false);

        return page.map(invoice -> toResponseDTO(invoice,
                allocations.get(invoice.getId()),
                documents.getOrDefault(invoice.getId(), List.of()),
                payments.getOrDefault(invoice.getId(), List.of()),
                false));
    }

    @Transactional(readOnly = true)
    public long countPending(UUID enterpriseId) {
        return repository.countPending(enterpriseId);
    }

    /**
     * Rubrica onde as faturas deste fornecedor costumam ser lançadas neste
     * projeto. É o que transforma a associação num clique a partir da segunda
     * fatura do mesmo fornecedor.
     *
     * @return vazio quando ainda não há histórico, ou quando a rubrica sugerida
     *         entretanto desapareceu
     */
    @Transactional(readOnly = true)
    public Optional<ConstructionBudgetItem> suggestBudgetItem(UUID enterpriseId, String supplierNif) {
        if (isBlank(supplierNif)) {
            return Optional.empty();
        }
        return repository
                .findBudgetItemIdsUsedBySupplier(enterpriseId, supplierNif.trim(), PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .flatMap(budgetItemRepository::findById);
    }

    // ── edição ────────────────────────────────────────────────

    public ConstructionInvoice update(UUID id, ConstructionInvoiceUpsertDTO dto) {
        ConstructionInvoice invoice = getById(id);

        // Guardado antes de escrever por cima: a verificação de duplicado só
        // corre se a identidade do documento mudar de facto.
        String previousAtcud = invoice.getInvoiceAtcud();
        String previousNif = invoice.getSupplierNif();
        String previousNumber = invoice.getInvoiceNumber();

        // A base tributável e o total de impostos não aparecem aqui de propósito:
        // são o que o QR da AT declarou, não campos de edição — ver
        // ConstructionInvoiceUpsertDTO. Corrigir o total à mão não os recalcula,
        // porque o que lá está é o que a AT recebeu.
        invoice.setSupplierName(trimToNull(dto.supplierName()));
        invoice.setSupplierNif(trimToNull(dto.supplierNif()));
        invoice.setInvoiceNumber(trimToNull(dto.invoiceNumber()));
        invoice.setInvoiceAtcud(trimToNull(dto.invoiceAtcud()));
        invoice.setInvoiceDate(dto.invoiceDate());
        invoice.setTotalAmount(dto.totalAmount());
        invoice.setNotes(trimToNull(dto.notes()));
        invoice.setDescription(trimToNull(dto.description()));
        invoice.setPossibleEnterprises(trimToNull(dto.possibleEnterprises()));
        invoice.setAskWhom(trimToNull(dto.askWhom()));
        // Null não é "limpar": o estado do papel também é mexido por quem junta
        // ou larga um documento, e um PUT que o omita não pode desfazer isso.
        if (!isBlank(dto.documentStatus())) {
            invoice.setDocumentStatus(parseDocumentStatus(dto.documentStatus()));
        }

        // Agora — e não antes — é que os campos são os corrigidos. Este é o
        // único momento em que uma fatura sem QR legível ganha identidade: até
        // aqui não havia ATCUD nem número por onde a comparar, e por isso ela
        // entrou sem passar por verificação nenhuma. Sem isto, completar duas
        // fotos da mesma fatura à mão criava dois lançamentos iguais.
        //
        // Só quando a identidade muda: de outro modo, um duplicado que já
        // esteja na base — carregado antes de isto existir — passava a
        // bloquear qualquer edição, incluindo mexer só nas notas. Quem não
        // toca no número não pode ser travado por uma colisão que já lá estava.
        boolean identityChanged =
                !Objects.equals(previousAtcud, invoice.getInvoiceAtcud())
                        || !Objects.equals(previousNif, invoice.getSupplierNif())
                        || !Objects.equals(previousNumber, invoice.getInvoiceNumber());
        if (identityChanged) {
            // Editar campos não traz ficheiro novo: só as chaves de identidade
            // (ATCUD e NIF+número) são reverificadas, o checksum não se aplica.
            rejectIfDuplicate(invoice, null);
        }

        Optional<ConstructionExpense> allocation = findAllocation(invoice.getId());

        // Apagar a data ou o total de uma fatura já lançada deixaria o
        // lançamento sem os campos que a despesa exige. Vale mais recusar aqui,
        // com uma mensagem que se percebe, do que rebentar na constraint.
        if (allocation.isPresent() && invoice.needsReview()) {
            throw new BusinessException(ErrorCode.INVOICE_INCOMPLETE);
        }

        ConstructionInvoice saved = repository.save(invoice);

        // A despesa que nasceu desta fatura tem de acompanhar a correção, senão
        // o orçamento continua a somar o valor errado.
        allocation.ifPresent(expense -> syncExpenseFromInvoice(expense, saved));

        return saved;
    }

    /** Marca (ou desmarca) a fatura como enviada para a contabilidade. */
    public ConstructionInvoice setSentToAccountant(UUID id, boolean sent) {
        ConstructionInvoice invoice = getById(id);

        invoice.setSentToAccountant(sent);
        if (sent) {
            invoice.setSentToAccountantAt(OffsetDateTime.now());
            authContext.currentProfileId().ifPresent(invoice::setSentToAccountantBy);
        } else {
            invoice.setSentToAccountantAt(null);
            invoice.setSentToAccountantBy(null);
        }

        return repository.save(invoice);
    }


    /**
     * Remove <b>um</b> documento da fatura — o ficheiro e a miniatura no Storage,
     * e a linha. A fatura fica; se este era o último documento, o estado do papel
     * volta a {@code MISSING}, porque {@code ARCHIVED} sem ficheiro seria mentira.
     *
     * <p>Distinto de {@code POST /{id}/file}, que substitui e larga todos.
     */
    public void deleteDocument(UUID invoiceId, UUID documentId) {
        ConstructionInvoice invoice = getById(invoiceId);
        ConstructionInvoiceDocument document = documentRepository
                .findByIdAndInvoiceId(documentId, invoiceId)
                .orElseThrow(() -> ResourceNotFoundException.invoiceDocument(documentId.toString()));

        deleteQuietly(document.getBucket(), document.getStorageKey());
        deleteQuietly(document.getBucket(), document.getThumbnailKey());
        documentRepository.delete(document);
        documentRepository.flush();

        if (documentRepository.countByInvoiceId(invoiceId) == 0) {
            invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.MISSING);
        }
    }
    public void delete(UUID id) {
        ConstructionInvoice invoice = getById(id);
        deleteDocuments(invoice);
        // A despesa vai atrás por FK cascade: um valor no orçamento sem
        // documento que o justifique não serviria a ninguém.
        repository.delete(invoice);
    }

    // ── afetação à rubrica ────────────────────────────────────

    /**
     * Liga a fatura a uma rubrica, criando o lançamento correspondente.
     *
     * É aqui — e só aqui — que a data e o total passam a ser obrigatórios: a
     * despesa exige ambos, e é dela que sai o gasto real da rubrica.
     */
    public ConstructionExpense allocate(UUID invoiceId, UUID budgetItemId) {
        ConstructionInvoice invoice = getById(invoiceId);

        if (findAllocation(invoiceId).isPresent()) {
            throw new BusinessException(ErrorCode.INVOICE_ALREADY_ALLOCATED);
        }
        if (invoice.needsReview()) {
            throw new BusinessException(ErrorCode.INVOICE_INCOMPLETE);
        }

        // Uma rubrica pertence sempre a uma obra: uma fatura da quarentena ou da
        // empresa não tem onde ser lançada enquanto não for identificada.
        if (invoice.getScope() != ConstructionInvoice.Scope.PROJECT) {
            throw new BusinessException(ErrorCode.INVOICE_SCOPE_NOT_ALLOCATABLE);
        }

        ConstructionBudgetItem item = budgetItemRepository.findById(budgetItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_BUDGET_ITEM_NOT_FOUND));
        if (!item.getEnterprise().getId().equals(invoice.getEnterpriseId())) {
            throw new BusinessException(ErrorCode.INVOICE_ITEM_OTHER_ENTERPRISE);
        }
        if (!item.getRowKind().acceptsExpenses()) {
            throw new BusinessException(ErrorCode.EXPENSE_ITEM_NOT_EXPENSABLE);
        }

        ConstructionExpense expense = new ConstructionExpense();
        expense.setInvoice(invoice);
        expense.setBudgetItem(item);
        expense.setDescription(invoice.getNotes());
        authContext.currentProfileId().ifPresent(expense::setCreatedBy);
        syncExpenseFromInvoice(expense, invoice);

        return expenseRepository.save(expense);
    }

    /** Desfaz a associação: apaga o lançamento e devolve a fatura à caixa de entrada. */
    public ConstructionInvoice deallocate(UUID invoiceId) {
        ConstructionInvoice invoice = getById(invoiceId);
        ConstructionExpense expense = findAllocation(invoiceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVOICE_NOT_ALLOCATED));

        expenseRepository.delete(expense);
        return invoice;
    }

    /**
     * Nome e valores do lançamento saem da fatura. O nome tenta o fornecedor, o
     * número do documento e por fim o ficheiro — a despesa tem de aparecer na
     * lista da rubrica com alguma coisa que se reconheça.
     */
    private void syncExpenseFromInvoice(ConstructionExpense expense, ConstructionInvoice invoice) {
        expense.setName(firstNonBlank(
                invoice.getSupplierName(),
                invoice.getInvoiceNumber(),
                primaryDocument(invoice.getId()).map(ConstructionInvoiceDocument::getOriginalFilename).orElse(null),
                "Fatura"));
        expense.setExpenseDate(invoice.getInvoiceDate());
        expense.setTotalPrice(invoice.getTotalAmount());
    }

    // ── DTOs ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ConstructionInvoiceResponseDTO toResponseDTO(ConstructionInvoice invoice, boolean includeFileUrl) {
        return toResponseDTO(invoice,
                findAllocation(invoice.getId()).orElse(null),
                documentRepository.findByInvoiceIdOrderByUploadedAtAsc(invoice.getId()),
                paymentService.paymentsForInvoice(invoice.getId(), includeFileUrl),
                includeFileUrl);
    }

    private ConstructionInvoiceResponseDTO toResponseDTO(ConstructionInvoice invoice,
                                                         ConstructionExpense allocation,
                                                         List<ConstructionInvoiceDocument> documents,
                                                         List<InvoicePaymentSummaryDTO> payments,
                                                         boolean includeFileUrl) {
        ConstructionBudgetItem item = allocation == null ? null : allocation.getBudgetItem();
        // Os campos soltos de ficheiro descrevem o documento principal e são o
        // que a UI ainda lê hoje; a lista completa vem em `documents`. Uma fatura
        // sem documento nenhum é legal desde a V24 e traz tudo isto a null.
        ConstructionInvoiceDocument primary = documents.isEmpty() ? null : documents.get(0);

        // Estado de pagamento é derivado: soma das ligações vs. líquido da fatura.
        java.math.BigDecimal netAmount = paymentService.netAmount(invoice);
        java.math.BigDecimal paidAmount = payments.stream()
                .map(InvoicePaymentSummaryDTO::amountOnThisInvoice)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        String paymentStatus = PaymentService.deriveStatus(netAmount, paidAmount).name();

        return new ConstructionInvoiceResponseDTO(
                invoice.getId(),
                invoice.getEnterpriseId(),
                invoice.getScope().name(),
                invoice.getDocumentType().name(),
                invoice.getRelatedInvoiceId(),
                invoice.getDocumentStatus().name(),
                invoice.getDescription(),

                invoice.getSupplierName(),
                invoice.getSupplierNif(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceAtcud(),
                invoice.getInvoiceDate(),
                invoice.getTotalAmount(),
                invoice.getTaxableAmount(),
                invoice.getTaxAmount(),
                invoice.getNotes(),
                invoice.getPossibleEnterprises(),
                invoice.getAskWhom(),
                invoice.needsReview(),

                allocation != null,
                allocation == null ? null : allocation.getId(),
                item == null ? null : item.getId(),
                item == null ? null : item.getCode(),
                item == null ? null : item.getName(),

                includeFileUrl && primary != null
                        ? signedUrls.resolve(primary.getBucket(), primary.getStorageKey()) : null,
                primary == null ? null : signedUrls.resolve(primary.getBucket(), primary.getThumbnailKey()),
                primary == null ? null : primary.getOriginalFilename(),
                primary == null ? null : primary.getMimeType(),
                primary == null ? null : primary.getSizeBytes(),
                primary == null ? null : primary.getOriginalSizeBytes(),
                primary == null ? null : primary.getUploadedBy(),
                primary == null ? null : resolveProfileName(primary.getUploadedBy()),
                primary == null ? null : primary.getUploadedAt(),
                documents.stream().map(document -> toDocumentDTO(document, includeFileUrl)).toList(),

                paymentStatus,
                paidAmount,
                netAmount,
                payments,

                invoice.isSentToAccountant(),
                invoice.getSentToAccountantBy(),
                resolveProfileName(invoice.getSentToAccountantBy()),
                resolveProfileRole(invoice.getSentToAccountantBy()),
                invoice.getSentToAccountantAt(),

                invoice.getCreatedBy(),
                resolveProfileName(invoice.getCreatedBy()),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }

    private InvoiceDocumentDTO toDocumentDTO(ConstructionInvoiceDocument document, boolean includeFileUrl) {
        return new InvoiceDocumentDTO(
                document.getId(),
                includeFileUrl ? signedUrls.resolve(document.getBucket(), document.getStorageKey()) : null,
                signedUrls.resolve(document.getBucket(), document.getThumbnailKey()),
                document.getOriginalFilename(),
                document.getMimeType(),
                document.getSizeBytes(),
                document.getOriginalSizeBytes(),
                document.getKind().name(),
                document.getPageNumber(),
                document.getUploadedBy(),
                resolveProfileName(document.getUploadedBy()),
                document.getUploadedAt());
    }

    // ── auxiliares ────────────────────────────────────────────

    private Optional<ConstructionExpense> findAllocation(UUID invoiceId) {
        return expenseRepository.findByInvoiceId(invoiceId);
    }

    /** Os documentos de uma página inteira de faturas numa query, em vez de uma por linha. */
    private Map<UUID, List<ConstructionInvoiceDocument>> loadDocuments(List<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(invoiceIds).stream()
                .collect(Collectors.groupingBy(document -> document.getInvoice().getId()));
    }

    private Map<UUID, ConstructionExpense> loadAllocations(List<UUID> invoiceIds) {
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }
        return expenseRepository.findByInvoiceIdIn(invoiceIds).stream()
                .collect(Collectors.toMap(e -> e.getInvoice().getId(), Function.identity(),
                        (a, b) -> a, HashMap::new));
    }

    /**
     * Recusa se a fatura colidir com outra do mesmo projeto.
     *
     * Três chaves, verificadas por ordem de certeza — a mais forte primeiro:
     *
     * <ul>
     *   <li><b>checksum</b> — o ficheiro, byte a byte. Não depende do QR nem de
     *       nada escrito à mão, por isso é a única que apanha duas cópias do
     *       mesmo ficheiro quando nenhuma tem QR legível (ver notes/bugs.md,
     *       caso 3).</li>
     *   <li><b>ATCUD</b> — o identificador que a AT atribui ao documento. Vem do
     *       QR, e é o que apanha o mesmo papel fotografado outra vez — bytes
     *       diferentes, mesmo documento.</li>
     *   <li><b>(NIF do fornecedor, número do documento)</b> — a chave de negócio.
     *       É esta que apanha o duplicado quando o QR falhou e alguém completou
     *       os campos à mão: nesse momento o ATCUD continua vazio e a chave
     *       anterior não serve de nada. O mesmo fornecedor não emite dois
     *       documentos com o mesmo número — mas o número é escrito à mão dos
     *       dois lados, e cada software de faturação formata-o à sua maneira
     *       ("FT 2024/123", "FT2024-123", …). Por isso a comparação é feita
     *       normalizada ({@link #normalizeDocumentNumber}), não por igualdade
     *       exata.</li>
     * </ul>
     *
     * Sem nenhuma das três não há como comparar, e a fatura segue para revisão
     * manual como sempre.
     */
    private void rejectIfDuplicate(ConstructionInvoice invoice, String checksum) {
        // No carregamento a fatura ainda não tem id; a query trata o null.
        UUID excludeId = invoice.getId();

        // O checksum é a única das três chaves que já é global (V24): o mesmo
        // ficheiro não entra duas vezes em obra nenhuma. Por isso a mensagem tem
        // de dizer onde está o primeiro, que pode nem ser deste projeto.
        if (!isBlank(checksum)) {
            documentRepository.findByChecksum(checksum, excludeId).stream()
                    .findFirst()
                    .ifPresent(document -> {
                        ConstructionInvoice other = document.getInvoice();
                        throw new BusinessException(ErrorCode.INVOICE_DUPLICATE_FILE, String.format(
                                "Este ficheiro já foi carregado em %s (%s, %s)",
                                whereItIs(other), describe(other), dateOf(other)));
                    });
        }

        if (!isBlank(invoice.getInvoiceAtcud())) {
            repository.findByAtcud(invoice.getInvoiceAtcud(), excludeId).stream()
                    .findFirst()
                    .ifPresent(other -> {
                        throw new BusinessException(ErrorCode.INVOICE_DUPLICATE_ATCUD, String.format(
                                "Já existe uma fatura com este ATCUD em %s (%s, %s)",
                                whereItIs(other), describe(other), dateOf(other)));
                    });
        }

        if (!isBlank(invoice.getSupplierNif()) && !isBlank(invoice.getInvoiceNumber())) {
            String normalizedNumber = normalizeDocumentNumber(invoice.getInvoiceNumber());
            repository.findBySupplierNif(invoice.getSupplierNif(), excludeId)
                    .stream()
                    .filter(other -> normalizedNumber.equals(normalizeDocumentNumber(other.getInvoiceNumber())))
                    .findFirst()
                    .ifPresent(other -> {
                        throw new BusinessException(ErrorCode.INVOICE_DUPLICATE_DOCUMENT, String.format(
                                "Já existe uma fatura deste fornecedor com o número %s em %s (%s, %s)",
                                invoice.getInvoiceNumber(), whereItIs(other), describe(other), dateOf(other)));
                    });
        }
    }

    /**
     * Onde está a fatura com que se colidiu. Só o nome do projeto por agora — a
     * quarentena e as despesas da empresa entram aqui quando o {@code scope}
     * existir (V26).
     */
    private static String whereItIs(ConstructionInvoice invoice) {
        Enterprise enterprise = invoice.getEnterprise();
        if (enterprise != null) {
            return enterprise.getName();
        }
        return invoice.getScope() == ConstructionInvoice.Scope.COMPANY
                ? "despesas da empresa"
                : "faturas por identificar";
    }

    /** Algo que identifique a outra fatura na mensagem, mesmo sem QR lido. */
    private static String describe(ConstructionInvoice invoice) {
        return firstNonBlank(invoice.getSupplierName(), invoice.getInvoiceNumber(), "sem fornecedor");
    }

    private static String dateOf(ConstructionInvoice invoice) {
        return invoice.getInvoiceDate() != null ? invoice.getInvoiceDate().toString() : "sem data";
    }

    private List<DuplicateInvoiceRefDTO> findDuplicates(String atcud, UUID excludeId) {
        if (isBlank(atcud)) {
            return List.of();
        }
        List<ConstructionInvoice> found = repository.findByAtcud(atcud, excludeId);
        Map<UUID, ConstructionExpense> allocations = loadAllocations(
                found.stream().map(ConstructionInvoice::getId).toList());

        return found.stream()
                .map(other -> {
                    ConstructionExpense expense = allocations.get(other.getId());
                    ConstructionBudgetItem item = expense == null ? null : expense.getBudgetItem();
                    return new DuplicateInvoiceRefDTO(
                            other.getId(),
                            other.getSupplierName(),
                            other.getInvoiceNumber(),
                            other.getInvoiceDate(),
                            other.getTotalAmount(),
                            item == null ? null : item.getCode(),
                            item == null ? null : item.getName());
                })
                .toList();
    }

    /**
     * A chave assenta no projeto e num UUID novo, não no id da fatura: assim o
     * ficheiro sobe antes do {@code save}, e uma única gravação chega. Também
     * mantém as faturas do mesmo projeto juntas no bucket, que é como se olha
     * para elas quando é preciso ir lá ver à mão.
     */
    private ConstructionInvoiceDocument addDocument(ConstructionInvoice invoice, UUID enterpriseId,
                                                    StoredContent stored, String checksum,
                                                    Long originalSizeBytes) {
        ConstructionInvoiceDocument document = new ConstructionInvoiceDocument();
        document.setInvoice(invoice);
        document.setChecksumSha256(checksum);
        document.setOriginalSizeBytes(originalSizeBytes);

        storeDocument(document, enterpriseId, stored.filename(), stored.content(), stored.mimeType());
        storeThumbnail(document, enterpriseId, stored.content(), stored.mimeType());

        // Ter papel é o que faz o estado ARCHIVED. "Pedir" e "imprimir" deixam
        // de fazer sentido a partir do momento em que o documento chega.
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.ARCHIVED);
        return documentRepository.save(document);
    }

    private void storeDocument(ConstructionInvoiceDocument document, UUID enterpriseId,
                               String originalFilename, byte[] content, String mime) {
        String safeName = storageService.sanitizeFileName(originalFilename);
        String key = String.format("construction-invoices/%s/%s_%s",
                enterpriseId, UUID.randomUUID().toString().substring(0, 8), safeName);

        try (InputStream in = new ByteArrayInputStream(content)) {
            storageService.upload(BUCKET, key, mime, in);
        } catch (IOException e) {
            throw StorageException.uploadError(originalFilename, e);
        }

        document.setBucket(BUCKET);
        document.setStorageKey(key);
        document.setOriginalFilename(originalFilename);
        document.setMimeType(mime);
        // O tamanho é o do que realmente foi para o Storage — `content` pode já
        // vir comprimido por InvoiceCompressionService, diferente do upload recebido.
        document.setSizeBytes((long) content.length);
        document.setUploadedAt(OffsetDateTime.now());
        authContext.currentProfileId().ifPresent(document::setUploadedBy);
    }

    /**
     * A miniatura é um extra. Falhar aqui não pode custar a fatura, que já está
     * carregada e é o que interessa — a lista cai num ícone de ficheiro.
     */
    private void storeThumbnail(ConstructionInvoiceDocument document, UUID enterpriseId, byte[] content, String mime) {
        Optional<byte[]> thumbnail = thumbnailService.render(content, mime);
        if (thumbnail.isEmpty()) {
            document.setThumbnailKey(null);
            document.setThumbnailMime(null);
            return;
        }

        String key = String.format("construction-invoices/%s/thumb_%s.jpg",
                enterpriseId, UUID.randomUUID().toString().substring(0, 8));
        try (InputStream in = new ByteArrayInputStream(thumbnail.get())) {
            storageService.upload(BUCKET, key, InvoiceThumbnailService.THUMBNAIL_MIME, in);
            document.setThumbnailKey(key);
            document.setThumbnailMime(InvoiceThumbnailService.THUMBNAIL_MIME);
        } catch (IOException e) {
            log.warn("Não foi possível guardar a miniatura da fatura: {}", e.getMessage());
            document.setThumbnailKey(null);
            document.setThumbnailMime(null);
        }
    }

    /**
     * Larga todos os documentos da fatura — os ficheiros no Storage e as linhas.
     * A fatura em si fica: sem documento é um estado legal desde a V24.
     */
    private void deleteDocuments(ConstructionInvoice invoice) {
        List<ConstructionInvoiceDocument> documents =
                documentRepository.findByInvoiceIdOrderByUploadedAtAsc(invoice.getId());
        documents.forEach(document -> {
            deleteQuietly(document.getBucket(), document.getStorageKey());
            deleteQuietly(document.getBucket(), document.getThumbnailKey());
        });
        documentRepository.deleteAll(documents);
        invoice.setDocumentStatus(ConstructionInvoice.DocumentStatus.MISSING);
    }

    /** O estado do papel vem do cliente como texto; um valor que não exista é erro de pedido. */
    private static ConstructionInvoice.DocumentStatus parseDocumentStatus(String value) {
        try {
            return ConstructionInvoice.DocumentStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "Estado de documento desconhecido: " + value);
        }
    }

    private void deleteQuietly(String bucket, String key) {
        if (bucket == null || isBlank(key)) {
            return;
        }
        try {
            storageService.delete(bucket, key);
        } catch (IOException e) {
            log.warn("Não foi possível eliminar {}/{} do storage: {}", bucket, key, e.getMessage());
        }
    }

    /** SHA-256 do conteúdo, em hexadecimal minúsculo — a chave de duplicado mais forte que há. */
    private static String sha256Hex(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é garantido pela JVM (java.security.MessageDigestSpi
            // standard algorithm names) — nunca acontece na prática.
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw StorageException.uploadError(file.getOriginalFilename(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw FileUploadException.empty(file != null ? file.getOriginalFilename() : "fatura");
        }

        String mime = Optional.ofNullable(file.getContentType()).orElse("");
        if (!INVOICE_MIME.contains(mime)) {
            throw FileUploadException.invalidType(file.getOriginalFilename(), mime, "PDF, JPEG, PNG");
        }

        if (file.getSize() > MAX_INVOICE_BYTES) {
            throw FileUploadException.sizeExceeded(file.getOriginalFilename(), file.getSize(), MAX_INVOICE_BYTES);
        }
    }

    private String resolveProfileName(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId).map(Profile::getName).orElse(null);
    }

    private String resolveProfileRole(UUID profileId) {
        if (profileId == null) {
            return null;
        }
        return profileRepository.findById(profileId)
                .map(profile -> profile.getRole() == null ? null : profile.getRole().name())
                .orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Normaliza um número de documento para comparar duplicados: maiúsculas e só
     * letras/dígitos. Cada software de faturação escreve o mesmo número de
     * forma diferente ("FT 2024/123", "FT2024-123", "ft.2024.123") — sem isto,
     * a mesma fatura escrita à mão duas vezes de forma ligeiramente diferente
     * escapava à verificação por (NIF, número). Não se aplica ao ATCUD nem ao
     * checksum: nenhum dos dois é escrito à mão.
     */
    private static String normalizeDocumentNumber(String value) {
        return isBlank(value) ? "" : value.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
    }
}
