package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.DocumentsExportSummaryDTO;
import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceDocumentsScope;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoiceDocument;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceDocumentRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Os documentos das faturas de uma obra como a pasta {@code Faturas\Lançadas\}
 * do vault da Vilatro os guarda (docs/excel-parity.md §7): uma pasta só, sem
 * subpastas de data, cada ficheiro com o nome
 * {@code <aaaammdd>_<nº sanitizado>_<FornecedorCamelCase>[_pN].<ext>}.
 *
 * Duas fases, separadas de propósito: o {@link #plan} decide os nomes dentro da
 * transação (precisa das faturas e dos documentos), e o {@link #writeZip} só
 * descarrega do Storage e escreve — fora da transação, um ficheiro em memória de
 * cada vez, porque as três obras somam ~80 MB de documentos.
 *
 * Os nomes: os documentos que vieram do vault já têm o nome certo em
 * {@code original_filename} (é o que a migração de 2026-09-18 guardou), e esse
 * mantém-se tal e qual — gerar de novo podia divergir ({@code FTFAC2026-134}
 * vs {@code FAC2026/134}). Só os carregados na app (nome da foto, do PDF do
 * fornecedor) recebem o nome gerado. Colisões ficam com {@code _2}, {@code _3},
 * nunca se sobrepõem, e vão para os avisos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceDocumentsExportService {

    static final String FOLDER = "Faturas/Lançadas/";
    static final String MISSING_REPORT = "_EM-FALTA.txt";
    static final String NO_NUMBER = "SEM-N";
    static final String NO_SUPPLIER = "Fornecedor";
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MIN_SUPPLIER_LENGTH = 8;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** {@code 20260617_FR2026A24-1412_Inoxtubo.pdf}, com ou sem {@code _pN}/{@code _2} — e nunca um espaço. */
    private static final Pattern VAULT_NAME = Pattern.compile("^\\d{8}(-\\d{8})?_[^\\s_]+_\\S+\\.\\w+$");
    private static final Pattern ILLEGAL_IN_NAME = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Map<String, String> EXTENSION_BY_MIME = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/heic", "heic");

    private final ConstructionInvoiceRepository invoiceRepository;
    private final ConstructionInvoiceDocumentRepository documentRepository;
    private final ConstructionExpenseRepository expenseRepository;
    private final SupabaseStorageService storageService;

    /** Um ficheiro do zip: o caminho lá dentro e onde está no Storage. */
    public record Entry(String path, UUID documentId, String bucket, String storageKey, boolean renamed) {}

    public record Plan(List<Entry> entries, int invoicesWithoutDocument, List<String> warnings) {
        public DocumentsExportSummaryDTO toSummary() {
            int renamed = (int) entries.stream().filter(Entry::renamed).count();
            return new DocumentsExportSummaryDTO(entries.size(), invoicesWithoutDocument, renamed, List.copyOf(warnings));
        }
    }

    // ── nomes ─────────────────────────────────────────────────

    /** Todas as faturas da obra — o zip da pasta da obra, como sempre. */
    @Transactional(readOnly = true)
    public Plan plan(UUID enterpriseId) {
        return plan(enterpriseId, InvoiceDocumentsScope.ALL, null, FOLDER);
    }

    /**
     * O download só dos documentos de um âmbito: o plano com os ficheiros na raiz
     * do zip e o nome que o zip vai ter.
     *
     * @param lotName o nome do lote, só para o nome do ficheiro quando o âmbito é
     *                {@code ASSOCIATED} com lote — o filtro é pelo {@code budgetId}.
     */
    @Transactional(readOnly = true)
    public DocumentsZip planDocumentsOnly(Enterprise enterprise, InvoiceDocumentsScope scope,
                                          UUID budgetId, String lotName, List<UUID> invoiceIds) {
        Plan plan = plan(enterprise.getId(), scope, budgetId, invoiceIds, "");
        return new DocumentsZip(documentsZipName(enterprise, scope, budgetId, lotName, plan), plan);
    }

    /** O zip dos documentos pronto a escrever: o nome e o plano. */
    public record DocumentsZip(String fileName, Plan plan) {}

    /**
     * {@code [TESTE - ]Faturas - <Obra><sufixo>.zip}.
     *
     * O nome tem de dizer o âmbito: vários zips da mesma obra na pasta de
     * downloads, todos chamados "Faturas - Vila Aleu.zip", eram indistinguíveis.
     */
    static String documentsZipName(Enterprise enterprise, InvoiceDocumentsScope scope,
                                   UUID budgetId, String lotName, Plan plan) {
        String suffix = switch (scope == null ? InvoiceDocumentsScope.ALL : scope) {
            case ALL -> "";
            case UNCLASSIFIED -> " - Por classificar";
            case SELECTED -> " - " + plan.entries().size() + " selecionadas";
            // sem lote, o âmbito são as associadas de *todos* os lotes
            case ASSOCIATED -> budgetId == null
                    ? " - Associadas"
                    : " - " + BudgetExcelExportService.safeName(lotName == null ? "Lote" : lotName);
        };
        return BudgetExcelExportService.testPrefix(enterprise)
                + "Faturas - " + BudgetExcelExportService.folderName(enterprise) + suffix + ".zip";
    }

    /**
     * O plano dos documentos de uma obra, limitado a um âmbito.
     *
     * O lote de uma fatura deduz-se das rubricas onde está classificada
     * ({@code expense → budget_item → budget}), numa só query pelas despesas de
     * todas as faturas da obra. Uma fatura repartida por rubricas de dois lotes
     * conta para os dois.
     *
     * @param folder o prefixo dentro do zip. O zip da pasta da obra usa
     *               {@code Faturas/Lançadas/}, que é a estrutura do vault (§7); um
     *               download só dos PDFs passa {@code ""} e deixa-os na raiz — o
     *               nome do zip já diz de que âmbito são, e inventar uma pasta
     *               "Por classificar" no vault não era nosso para inventar.
     */
    @Transactional(readOnly = true)
    public Plan plan(UUID enterpriseId, InvoiceDocumentsScope scope, UUID budgetId,
                     List<UUID> invoiceIds, String folder) {
        List<ConstructionInvoice> all = invoiceRepository.findAllByEnterpriseIdForExport(enterpriseId);
        return planFor(filterByScope(all, scope, budgetId, invoiceIds), folder);
    }

    /** Conveniência para os âmbitos que não escolhem faturas à mão. */
    @Transactional(readOnly = true)
    public Plan plan(UUID enterpriseId, InvoiceDocumentsScope scope, UUID budgetId, String folder) {
        return plan(enterpriseId, scope, budgetId, null, folder);
    }

    /** As faturas do âmbito pedido, pela sua classificação em rubricas. */
    private List<ConstructionInvoice> filterByScope(List<ConstructionInvoice> invoices,
                                                    InvoiceDocumentsScope scope, UUID budgetId,
                                                    List<UUID> invoiceIds) {
        if (scope == null || scope == InvoiceDocumentsScope.ALL) {
            return invoices;
        }

        if (scope == InvoiceDocumentsScope.SELECTED) {
            if (invoiceIds == null || invoiceIds.isEmpty()) {
                throw new BusinessException(ErrorCode.INVOICE_DOCUMENTS_NO_SELECTION);
            }
            // A interseção com as faturas *desta obra* é o que impede alguém de
            // puxar documentos de outra obra pondo ids à mão no query string: o
            // ponto de partida é sempre `findAllByEnterpriseIdForExport`.
            Set<UUID> wanted = new HashSet<>(invoiceIds);
            return invoices.stream().filter(i -> wanted.contains(i.getId())).toList();
        }

        List<UUID> ids = invoices.stream().map(ConstructionInvoice::getId).toList();
        Map<UUID, Set<UUID>> lotsByInvoice = new HashMap<>();
        if (!ids.isEmpty()) {
            for (ConstructionExpense expense : expenseRepository.findByInvoiceIdIn(ids)) {
                UUID lot = expense.getBudgetItem() == null ? null : expense.getBudgetItem().getBudgetId();
                if (lot != null) {
                    lotsByInvoice.computeIfAbsent(expense.getInvoice().getId(), k -> new HashSet<>()).add(lot);
                }
            }
        }

        return invoices.stream()
                .filter(invoice -> {
                    Set<UUID> lots = lotsByInvoice.get(invoice.getId());
                    boolean associated = lots != null && !lots.isEmpty();
                    if (scope == InvoiceDocumentsScope.UNCLASSIFIED) {
                        return !associated;
                    }
                    // ASSOCIATED: sem lote valem todas as associadas; com lote, só as desse
                    return associated && (budgetId == null || lots.contains(budgetId));
                })
                .toList();
    }

    private Plan planFor(List<ConstructionInvoice> invoices, String folder) {
        List<UUID> ids = invoices.stream().map(ConstructionInvoice::getId).toList();
        List<ConstructionInvoiceDocument> documents = ids.isEmpty() ? List.of()
                : documentRepository.findByInvoiceIdInOrderByUploadedAtAsc(ids);

        List<Entry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        Set<UUID> withDocument = new HashSet<>();

        for (ConstructionInvoiceDocument document : documents) {
            ConstructionInvoice invoice = document.getInvoice();
            withDocument.add(invoice.getId());
            if (document.getBucket() == null || isBlank(document.getStorageKey())) {
                warnings.add("Documento \"" + document.getOriginalFilename() + "\" da fatura " + describe(invoice)
                        + " não tem ficheiro no Storage — fica de fora.");
                continue;
            }
            boolean keepOriginal = followsVaultConvention(document.getOriginalFilename());
            String name = keepOriginal ? document.getOriginalFilename() : vaultFileName(invoice, document, warnings);
            String unique = uniqueName(name, taken);
            if (!unique.equals(name)) {
                warnings.add("Dois documentos ficariam com o nome \"" + name + "\" — o segundo fica \"" + unique + "\".");
            }
            entries.add(new Entry(folder + unique, document.getId(), document.getBucket(), document.getStorageKey(), !keepOriginal));
        }

        int withoutDocument = (int) invoices.stream().filter(invoice -> !withDocument.contains(invoice.getId())).count();
        return new Plan(entries, withoutDocument, warnings);
    }

    /** O nome já é o do vault (§7) — veio de lá pela migração, ou alguém o escreveu à mão. */
    static boolean followsVaultConvention(String originalFilename) {
        return originalFilename != null && VAULT_NAME.matcher(originalFilename).matches();
    }

    /**
     * {@code <aaaammdd>_<nº>_<Fornecedor>[_pN].<ext>} a partir da fatura. Sem nº
     * é {@code SEM-N} e a descrição curta no lugar do fornecedor, como o vault
     * faz aos talões. Sem nome de fornecedor usa-se o NIF (o QR só traz o NIF;
     * sem correspondência no catálogo o nome fica por preencher) — só cai no
     * literal {@code Fornecedor} se nem o NIF existir. Sem data (fatura por
     * rever) fica a data do upload, com aviso — o campo tem de existir para a
     * pasta se ordenar.
     */
    static String vaultFileName(ConstructionInvoice invoice, ConstructionInvoiceDocument document, List<String> warnings) {
        LocalDate date = invoice.getInvoiceDate();
        if (date == null) {
            date = document.getUploadedAt().toLocalDate();
            warnings.add("A fatura " + describe(invoice) + " não tem data — o documento leva a data do upload ("
                    + DATE.format(date) + ").");
        }
        String number = sanitizeNumber(invoice.getInvoiceNumber());
        String supplierLabel = isBlank(invoice.getSupplierName()) ? invoice.getSupplierNif() : invoice.getSupplierName();
        String third = number == null
                ? camelCase(invoice.getDescription(), NO_SUPPLIER)
                : camelCase(supplierLabel, NO_SUPPLIER);
        String page = document.getKind() == ConstructionInvoiceDocument.Kind.PAGE && document.getPageNumber() != null
                ? "_p" + document.getPageNumber() : "";
        String extension = extensionOf(document);

        String name = DATE.format(date) + "_" + (number == null ? NO_NUMBER : number) + "_" + third + page + "." + extension;
        if (name.length() > MAX_NAME_LENGTH) {
            // o nº nunca se corta (§7); encurta-se o terceiro campo até caber
            int excess = name.length() - MAX_NAME_LENGTH;
            int keep = Math.max(MIN_SUPPLIER_LENGTH, third.length() - excess);
            name = DATE.format(date) + "_" + (number == null ? NO_NUMBER : number) + "_" + third.substring(0, keep) + page + "." + extension;
        }
        return name;
    }

    /** {@code FR 2026A24/1412} → {@code FR2026A24-1412}: ilegais → {@code -}, traços colapsados, sem espaços. */
    static String sanitizeNumber(String number) {
        if (isBlank(number)) return null;
        String safe = ILLEGAL_IN_NAME.matcher(number.trim()).replaceAll("-")
                .replaceAll("-{2,}", "-")
                .replaceAll("\\s+", "");
        return safe.isEmpty() ? null : safe;
    }

    /** {@code Casa Vilas Boas} → {@code CasaVilasBoas}; sem acentos, sem {@code &} nem {@code ª}: só letras ASCII e algarismos. */
    static String camelCase(String text, String fallback) {
        if (isBlank(text)) return fallback;
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder();
        for (String word : plain.split("[^A-Za-z0-9]+")) {
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? fallback : out.toString();
    }

    private static String extensionOf(ConstructionInvoiceDocument document) {
        String original = document.getOriginalFilename();
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot > 0 && dot < original.length() - 1 && original.indexOf(' ', dot) < 0) {
                return original.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        String mime = document.getMimeType() == null ? "" : document.getMimeType().toLowerCase(Locale.ROOT);
        return EXTENSION_BY_MIME.getOrDefault(mime, "bin");
    }

    /** {@code nome.pdf} → {@code nome_2.pdf}, {@code nome_3.pdf}… até não colidir (o zip é case-insensitive no Windows). */
    private static String uniqueName(String name, Set<String> taken) {
        String candidate = name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int n = 2; !taken.add(candidate.toLowerCase(Locale.ROOT)); n++) {
            candidate = base + "_" + n + extension;
        }
        return candidate;
    }

    private static String describe(ConstructionInvoice invoice) {
        return invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "sem nº (" + invoice.getId() + ")";
    }

    // ── o zip ─────────────────────────────────────────────────

    /**
     * Escreve o zip em {@code out}: o livro na raiz e os documentos em
     * {@code Faturas/Lançadas/}. Um documento que o Storage não devolva não
     * aborta o download — fica de fora e listado em {@code _EM-FALTA.txt},
     * porque a esta altura os cabeçalhos já seguiram e não há como devolver um
     * erro.
     */
    public void writeZip(String workbookName, byte[] workbook, Plan plan, OutputStream out) throws IOException {
        writeEntries(workbookName, workbook, plan, out);
    }

    /**
     * Só os documentos, sem livro nenhum — o download dos PDFs de um âmbito.
     *
     * O {@code _EM-FALTA.txt} continua a ser escrito quando o Storage não devolve
     * algum: a esta altura os cabeçalhos já seguiram, não há como devolver um erro,
     * e um zip que cala uma falta é pior do que um que a declara.
     */
    public void writeDocumentsZip(Plan plan, OutputStream out) throws IOException {
        writeEntries(null, null, plan, out);
    }

    private void writeEntries(String workbookName, byte[] workbook, Plan plan, OutputStream out) throws IOException {
        List<String> missing = new ArrayList<>();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            if (workbookName != null && workbook != null) {
                zip.putNextEntry(new ZipEntry(workbookName));
                zip.write(workbook);
                zip.closeEntry();
            }

            for (Entry entry : plan.entries()) {
                byte[] content;
                try {
                    content = storageService.download(entry.bucket(), entry.storageKey());
                } catch (IOException | RuntimeException e) {
                    log.warn("Documento {} não veio do Storage para o zip: {}", entry.documentId(), e.getMessage());
                    // o prefixo depende do âmbito (pasta do vault ou raiz), por isso
                    // tira-se o que lá estiver em vez de assumir o `FOLDER`
                    int slash = entry.path().lastIndexOf('/');
                    missing.add(entry.path().substring(slash + 1) + " (documento " + entry.documentId() + ")");
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entry.path()));
                zip.write(content);
                zip.closeEntry();
            }

            if (!missing.isEmpty()) {
                // o relatório fica ao lado dos documentos, seja na pasta do vault ou na raiz
                String first = plan.entries().isEmpty() ? FOLDER : plan.entries().get(0).path();
                String folder = first.substring(0, first.lastIndexOf('/') + 1);
                zip.putNextEntry(new ZipEntry(folder + MISSING_REPORT));
                zip.write(("Documentos que o Storage não devolveu — não estão neste zip:\n"
                        + String.join("\n", missing) + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
