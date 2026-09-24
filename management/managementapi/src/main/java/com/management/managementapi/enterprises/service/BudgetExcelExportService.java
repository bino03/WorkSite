package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetExportSummaryDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetLotDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.dto.budget.response.DocumentsExportSummaryDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.enums.PaymentMethod;
import com.management.managementapi.enterprises.model.enums.PaymentStatus;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.enterprises.repository.InvoicePaymentRepository;
import com.management.managementapi.exeption.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.ComparisonOperator;
import org.apache.poi.ss.usermodel.ConditionalFormattingThreshold;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFConditionalFormattingRule;
import org.apache.poi.xssf.usermodel.XSSFDataBarFormatting;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFFontFormatting;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFSheetConditionalFormatting;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTable;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableColumn;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STTotalsRowFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exporta uma obra para o {@code Despesas - <Obra>.xlsx} do vault da Vilatro.
 *
 * O livro reproduz o que o vault já tem — as mesmas folhas, tabelas, cabeçalhos
 * e formatos que os scripts {@code gerar-vista-despesas.ps1} e
 * {@code gerar-orcamento-vs-gasto.ps1} esperam (contrato em
 * {@code docs/excel-parity.md} §3, §4, §6 e §9):
 * <ul>
 *   <li>"Orçamento inicial" — a árvore de rubricas, no formato que o
 *       {@link BudgetExcelImportService} volta a ler (round-trip);</li>
 *   <li>"Despesas" — a {@code TabelaDespesas}, uma linha por despesa (uma fatura
 *       repartida por N rubricas dá N linhas com o mesmo nº; uma fatura por
 *       classificar dá uma linha com a Rubrica vazia; uma despesa lançada à mão
 *       dá uma linha sem nº), com linha de totais;</li>
 *   <li>"Orçamento vs Gasto" e "Rubricas" — o painel por capítulo e a árvore
 *       achatada, tudo em fórmulas sobre as duas tabelas, para continuar a
 *       reagir a edições feitas no Excel.</li>
 * </ul>
 *
 * A leitura da base de dados e a escrita do ficheiro estão separadas de
 * propósito: o {@link #summary} usa só a primeira, para o modal mostrar o que
 * vai sair antes do download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetExcelExportService {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    static final String SHEET_BUDGET = "Orçamento inicial";
    static final String SHEET_EXPENSES = "Despesas";
    static final String SHEET_COMPARISON = "Orçamento vs Gasto";
    static final String SHEET_RUBRICS = "Rubricas";
    static final String TABLE_EXPENSES = "TabelaDespesas";
    static final String TABLE_RUBRICS = "TabelaRubricas";
    /** Numa obra com vários lotes: uma folha "Orçamento - <Lote>" por lote (V39). */
    static final String SHEET_BUDGET_LOT_PREFIX = "Orçamento - ";
    /** Entre o lote e o índice na coluna Rubrica: "Lote A · 4.2.1 — Betão". */
    static final String LOT_SEPARATOR = " · ";
    private static final int SHEET_NAME_MAX = 31;

    /** A linha (0-based) do cabeçalho da "Orçamento inicial" — por baixo do bloco da empresa, como no vault. */
    static final int BUDGET_HEADER_ROW = 10;
    private static final int BUDGET_FONT_SIZE = 9;

    private static final String COMPANY_NAME = "Vilatro Construção & Engenharia, Lda.";
    private static final String COMPANY_ADDRESS = "Rua Engenheiro Joaquim Botelho de Lucena, nº28";
    private static final String COMPANY_POSTCODE = "5000-705 Vila Real";
    private static final String COMPANY_NIF = "NIF: 518849651";
    private static final String COMPANY_EMAIL = "Email: gestao.vilatro@gmail.com";

    // as cores das folhas do vault
    private static final String COLOR_WHITE = "FFFFFF";
    private static final String COLOR_HEADER_DARK = "3A3838";  // cabeçalho da "Despesas"
    private static final String COLOR_CHAPTER = "C6E0B4";      // capítulos do orçamento
    private static final String COLOR_DONE = "92D050";         // Liquidada / Bizdocs com "x"
    private static final String COLOR_PANEL_HEADER = "00B0F0"; // cabeçalho do painel
    private static final String COLOR_DATA_BAR = "638EC6";
    private static final String COLOR_RED = "FF0000";

    /** O tema do Office do vault — sem ele, o Excel usa o tema novo e a {@code TableStyleMedium2} muda de cor. */
    private static final String THEME_RESOURCE = "/excel/vilatro-theme.xml";
    private static final String LOGO_RESOURCE = "/excel/vilatro-logo.jpeg";
    static final String[] EXPENSES_HEADERS = {
            "Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada", "Metodo Pagamento",
            "Bizdocs", "Observações", "Rubrica", "Fornecedor", "NIF"};
    static final String[] RUBRICS_HEADERS = {
            "Art", "Descrição", "Cap", "Nível", "Tipo", "Orçamentado", "Gasto", "Saldo",
            "% consumido", "Nº faturas", "Etiqueta"};
    static final String[] RUBRICS_HEADERS_LOTS = {
            "Art", "Descrição", "Cap", "Nível", "Tipo", "Orçamentado", "Gasto", "Saldo",
            "% consumido", "Nº faturas", "Etiqueta", "Lote"};

    /**
     * Os formatos escrevem-se na sintaxe invariante do ficheiro (en-US); o Excel
     * em pt-PT mostra-os como {@code # ##0,00 €} e {@code dd/mm/aaaa}. É o mesmo
     * código que a coluna Valor do vault já usa — o {@code #.##0,00 €} do
     * contrato original falha acima de 1 000 000 € (armadilha 16 do vault).
     */
    private static final String FORMAT_CURRENCY = "#,##0.00\\ \"€\"";
    private static final String FORMAT_DATE = "dd/mm/yyyy";
    private static final String FORMAT_PERCENT = "0.0%";
    private static final String FORMAT_TEXT = "@";
    private static final String TABLE_STYLE = "TableStyleMedium2";

    private static final int LABEL_MAX_LENGTH = 70;
    private static final String TEST_PREFIX = "TESTE - ";
    /** O mesmo limite do formulário do Backoffice (`enterpriseFormSchema`). */
    private static final int SLUG_MAX_LENGTH = 120;
    private static final String NOTE_SEPARATOR = " · ";

    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final EnterpriseRepository enterpriseRepository;
    private final ConstructionBudgetItemService budgetService;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final ConstructionExpenseRepository expenseRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final PaymentService paymentService;
    private final InvoiceDocumentsExportService documentsExportService;

    /** O ficheiro pronto a devolver. */
    public record ExportFile(String fileName, byte[] content) {}

    /**
     * A pasta da obra pronta a zipar: o livro na raiz e o plano dos documentos
     * de {@code Faturas/Lançadas/}. Os bytes dos documentos não estão aqui — o
     * {@link InvoiceDocumentsExportService#writeZip} vai buscá-los ao Storage
     * enquanto escreve a resposta, já fora da transação.
     */
    public record ZipExport(String fileName, ExportFile workbook, InvoiceDocumentsExportService.Plan documents) {}

    // Nem `summary` nem `export` são `readOnly`: uma obra sem slug fica com um ao
    // passar por aqui (ver {@link #ensureSlug}) — é a única escrita.
    @Transactional
    public BudgetExportSummaryDTO summary(UUID enterpriseId) {
        return load(enterpriseId).toSummary(documentsExportService.plan(enterpriseId).toSummary());
    }

    @Transactional
    public ExportFile export(UUID enterpriseId, Set<BudgetExportSheet> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_NO_SHEETS);
        }
        Set<BudgetExportSheet> sheets = EnumSet.copyOf(requested);
        if (sheets.contains(BudgetExportSheet.COMPARISON)) {
            sheets.add(BudgetExportSheet.EXPENSES);
        }

        Model model = load(enterpriseId);
        boolean needsBudget = sheets.contains(BudgetExportSheet.BUDGET)
                || sheets.contains(BudgetExportSheet.COMPARISON);
        if (needsBudget && !model.hasBudget()) {
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_NO_BUDGET);
        }

        return new ExportFile(model.fileName, write(model, sheets));
    }

    /**
     * O livro mais os documentos, na estrutura exata da pasta do vault (§7):
     * {@code <slug>.zip} com {@code Despesas - <slug>.xlsx} e
     * {@code Faturas/Lançadas/*} na raiz — extrai-se em
     * {@code Empreendimentos\<slug>\} e fica igual.
     */
    @Transactional
    public ZipExport exportZip(UUID enterpriseId, Set<BudgetExportSheet> requested) {
        ExportFile workbook = export(enterpriseId, requested);
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId).orElseThrow();
        return new ZipExport(zipFileName(enterprise), workbook, documentsExportService.plan(enterpriseId));
    }

    /** Fora de transação de propósito — corre enquanto a resposta HTTP já está a ser escrita. */
    public void writeZip(ZipExport zip, OutputStream out) throws IOException {
        documentsExportService.writeZip(zip.workbook().fileName(), zip.workbook().content(), zip.documents(), out);
    }

    /** {@code Despesas - <slug>.xlsx}, como no vault; {@code TESTE - } à frente numa obra de teste. */
    static String fileName(Enterprise enterprise) {
        return testPrefix(enterprise) + "Despesas - " + folderName(enterprise) + ".xlsx";
    }

    /** {@code <slug>.zip} — o nome da pasta da obra no vault, para extrair em {@code Empreendimentos\}. */
    static String zipFileName(Enterprise enterprise) {
        return testPrefix(enterprise) + folderName(enterprise) + ".zip";
    }

    private static String folderName(Enterprise enterprise) {
        return safeName(isBlank(enterprise.getSlug()) ? enterprise.getName() : enterprise.getSlug());
    }

    private static String testPrefix(Enterprise enterprise) {
        return Boolean.TRUE.equals(enterprise.getIsTest()) ? TEST_PREFIX : "";
    }

    /** Sem os caracteres que o Windows recusa num nome de pasta/ficheiro; espaços e acentos ficam. */
    static String safeName(String name) {
        String safe = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|]", "-").replaceAll("\\s+", " ").trim();
        return safe.isEmpty() ? "obra" : safe;
    }

    /**
     * Uma obra sem slug fica com um ao ser exportada — o nome da pasta no vault
     * é o slug, e um ficheiro exportado sem ele não teria pasta onde viver. O
     * slug é o nome da obra (com espaços e acentos, como manda §2 do contrato),
     * limpo do que o Windows não aceita, e com sufixo {@code 2}, {@code 3}… se
     * já houver outra obra com esse nome. Pedido do utilizador a 2026-09-17, em
     * vez do aviso que existia.
     */
    private String ensureSlug(Enterprise enterprise, Model model) {
        if (!isBlank(enterprise.getSlug())) {
            return enterprise.getSlug();
        }
        String base = safeName(enterprise.getName());
        if (base.length() > SLUG_MAX_LENGTH) {
            base = base.substring(0, SLUG_MAX_LENGTH).trim();
        }
        String slug = base;
        for (int n = 2; enterpriseRepository.existsBySlugAndIdNot(slug, enterprise.getId()); n++) {
            slug = base + " " + n;
        }
        enterprise.setSlug(slug);
        enterpriseRepository.save(enterprise);
        model.warnings.add("A obra não tinha slug — ficou com \"" + slug
                + "\", que passa a ser o nome da sua pasta no vault.");
        return slug;
    }

    // ── leitura ───────────────────────────────────────────────

    private Model load(UUID enterpriseId) {
        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND));

        Model model = new Model();
        model.enterprise = enterprise;
        ensureSlug(enterprise, model);
        model.fileName = fileName(enterprise);
        if (Boolean.TRUE.equals(enterprise.getIsTest())) {
            model.warnings.add("Obra de teste — o ficheiro leva o prefixo \"" + TEST_PREFIX.trim()
                    + "\" e não deve entrar no vault.");
        }

        loadBudget(model, enterpriseId);
        loadExpenses(model, enterpriseId);
        return model;
    }

    private void loadBudget(Model model, UUID enterpriseId) {
        BudgetTreeDTO tree = budgetService.getTree(enterpriseId);
        model.tree = tree;
        if (tree.itemCount() == 0) {
            model.warnings.add("A obra não tem orçamento — só a folha \"Despesas\" pode ser exportada.");
            return;
        }

        // Uma obra com um só lote (com rubricas) sai exatamente como antes da V39.
        List<BudgetLotDTO> lots = budgetService.listLots(enterpriseId).stream()
                .filter(lot -> lot.itemCount() > 0)
                .toList();
        if (lots.size() <= 1) {
            for (BudgetItemNodeDTO root : tree.roots()) {
                flatten(root, null, null, model);
            }
            return;
        }

        model.warnings.add("Obra com " + lots.size() + " lotes — uma folha de orçamento por lote, e a coluna "
                + "Rubrica leva o lote antes do índice (\"" + lots.get(0).name() + LOT_SEPARATOR + "4.2.1 — …\").");
        for (BudgetLotDTO lot : lots) {
            BudgetTreeDTO lotTree = budgetService.getBudgetTree(lot.id());
            model.lots.add(new LotTree(lot.name(), lotTree));
            for (BudgetItemNodeDTO root : lotTree.roots()) {
                flatten(root, null, lot.name(), model);
            }
        }
    }

    /**
     * Achata a árvore para a {@code TabelaRubricas} e guarda, por rubrica, a
     * etiqueta que a coluna Rubrica da folha "Despesas" vai levar. Uma rubrica
     * sem índice (as "Alternativa …", que aceitam despesas) herda a etiqueta do
     * artigo com índice mais próximo acima — é o que o vault também faz quando
     * soma o valor da alternativa ao artigo de cima.
     */
    private void flatten(BudgetItemNodeDTO node, String inheritedLabel, String lot, Model model) {
        String label = inheritedLabel;
        if (!isBlank(node.code())) {
            label = lotPrefix(lot) + rubricLabel(node.code(), node.name());
            int level = node.code().split("\\.").length;
            BigDecimal budgeted = nullToZero(node.rolledUpBudget());
            String kind = level == 1 ? "CAPÍTULO" : budgeted.signum() > 0 ? "ITEM" : "TÍTULO";
            model.rubrics.add(new RubricRow(node.code(), node.name(), chapterOf(node.code()),
                    level, kind, budgeted, label, lot));
        } else if (node.acceptsExpenses() && node.ownExpenseCount() > 0) {
            model.warnings.add("A rubrica \"" + node.name() + "\" não tem índice — as suas "
                    + node.ownExpenseCount() + " despesas saem com a rubrica do artigo acima"
                    + (label != null ? " (" + label + ")" : "") + ".");
        }
        model.labelByItemId.put(node.id(), label);
        for (BudgetItemNodeDTO child : node.children()) {
            flatten(child, label, lot, model);
        }
    }

    private void loadExpenses(Model model, UUID enterpriseId) {
        List<ConstructionInvoice> invoices = invoiceRepository.findAllByEnterpriseIdForExport(enterpriseId);
        model.invoiceCount = invoices.size();

        Map<UUID, ConstructionInvoice> byId = invoices.stream()
                .collect(Collectors.toMap(ConstructionInvoice::getId, invoice -> invoice));
        Map<UUID, BigDecimal> creditedByInvoice = new HashMap<>();
        for (ConstructionInvoice invoice : invoices) {
            if (invoice.getRelatedInvoiceId() != null && invoice.getTotalAmount() != null) {
                creditedByInvoice.merge(invoice.getRelatedInvoiceId(), invoice.getTotalAmount(), BigDecimal::add);
            }
        }

        List<UUID> ids = invoices.stream().map(ConstructionInvoice::getId).toList();
        Map<UUID, BigDecimal> paidByInvoice = ids.isEmpty() ? Map.of()
                : invoicePaymentRepository.sumPaidByInvoices(ids).stream()
                        .collect(Collectors.toMap(InvoicePaymentRepository.InvoicePaidSum::getInvoiceId,
                                InvoicePaymentRepository.InvoicePaidSum::getPaid));
        Map<UUID, List<InvoicePaymentSummaryDTO>> paymentsByInvoice = paymentService.paymentsForInvoices(ids, false);

        Map<UUID, List<ConstructionExpense>> expensesByInvoice = new HashMap<>();
        List<ConstructionExpense> manual = new ArrayList<>();
        for (ConstructionExpense expense : expenseRepository.findAllByEnterpriseId(enterpriseId)) {
            if (expense.getInvoice() == null) {
                manual.add(expense);
            } else {
                expensesByInvoice.computeIfAbsent(expense.getInvoice().getId(), k -> new ArrayList<>()).add(expense);
            }
        }
        Comparator<ConstructionExpense> byCreation = Comparator.comparing(ConstructionExpense::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder()));
        expensesByInvoice.values().forEach(list -> list.sort(byCreation));

        for (ConstructionInvoice invoice : invoices) {
            addInvoiceRows(model, invoice, byId, creditedByInvoice, paidByInvoice, paymentsByInvoice,
                    expensesByInvoice.getOrDefault(invoice.getId(), List.of()));
        }

        manual.sort(Comparator.comparing(ConstructionExpense::getExpenseDate,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(byCreation));
        for (ConstructionExpense expense : manual) {
            model.manualExpenseCount++;
            model.rows.add(new ExpenseRow(null, expense.getExpenseDate(), expenseDescription(expense),
                    expense.getTotalPrice(), false, null, false,
                    "Despesa registada à mão na app, sem fatura.",
                    rubricLabelOf(expense, model), null, null));
        }

        // a tabela do vault está ordenada pela Data, da mais recente para a mais antiga;
        // a ordenação é estável, por isso as linhas de uma fatura repartida ficam juntas
        model.rows.sort(Comparator.comparing(ExpenseRow::date, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void addInvoiceRows(Model model, ConstructionInvoice invoice,
                                Map<UUID, ConstructionInvoice> byId,
                                Map<UUID, BigDecimal> creditedByInvoice,
                                Map<UUID, BigDecimal> paidByInvoice,
                                Map<UUID, List<InvoicePaymentSummaryDTO>> paymentsByInvoice,
                                List<ConstructionExpense> expenses) {
        boolean creditNote = invoice.getRelatedInvoiceId() != null
                || invoice.getDocumentType() == ConstructionInvoice.DocumentType.CREDIT_NOTE;
        if (creditNote) {
            model.creditNoteCount++;
        }
        if (isBlank(invoice.getInvoiceNumber())) {
            model.missingNumberCount++;
        }
        if (invoice.getInvoiceDate() == null || invoice.getTotalAmount() == null) {
            model.needsReviewCount++;
        }

        PaymentStatus status = PaymentStatus.UNPAID;
        List<InvoicePaymentSummaryDTO> payments = paymentsByInvoice.getOrDefault(invoice.getId(), List.of());
        if (!creditNote) {
            BigDecimal net = invoice.getTotalAmount() == null ? null
                    : invoice.getTotalAmount().subtract(creditedByInvoice.getOrDefault(invoice.getId(), BigDecimal.ZERO));
            status = PaymentService.deriveStatus(net, paidByInvoice.get(invoice.getId()));
            if (status == PaymentStatus.PARTIAL) {
                model.partialPaymentCount++;
            }
        }

        String number = invoiceNumberCell(invoice);
        boolean paid = status == PaymentStatus.PAID;
        String method = paid ? paymentMethodCell(payments, model, invoice) : null;
        String observations = observations(invoice, status, payments, creditNote, byId);

        if (expenses.isEmpty()) {
            model.unclassifiedInvoiceCount++;
            BigDecimal amount = invoice.getTotalAmount();
            if (creditNote && amount != null) {
                amount = amount.negate();
            }
            model.rows.add(new ExpenseRow(number, invoice.getInvoiceDate(), nullToEmpty(invoice.getDescription()),
                    amount, paid, method, invoice.isSentToAccountant(), observations, null,
                    invoice.getSupplierName(), invoice.getSupplierNif()));
            return;
        }

        BigDecimal allocated = BigDecimal.ZERO;
        for (ConstructionExpense expense : expenses) {
            allocated = allocated.add(nullToZero(expense.getTotalPrice()));
            String description = isBlank(invoice.getDescription()) ? expenseDescription(expense) : invoice.getDescription();
            model.rows.add(new ExpenseRow(number, invoice.getInvoiceDate(), description,
                    expense.getTotalPrice(), paid, method, invoice.isSentToAccountant(), observations,
                    rubricLabelOf(expense, model), invoice.getSupplierName(), invoice.getSupplierNif()));
        }

        BigDecimal expected = invoice.getTotalAmount() == null ? null
                : creditNote ? invoice.getTotalAmount().negate() : invoice.getTotalAmount();
        if (expected != null && allocated.compareTo(expected) != 0) {
            model.warnings.add("A fatura " + describe(invoice) + " está repartida em " + money(allocated)
                    + " mas o total é " + money(expected) + " — a soma da folha \"Despesas\" segue as despesas.");
        }
    }

    /** {@code invoice_number}; sem nº, o que o vault escreve nessa célula para dizer o que falta. */
    private static String invoiceNumberCell(ConstructionInvoice invoice) {
        if (!isBlank(invoice.getInvoiceNumber())) {
            return invoice.getInvoiceNumber();
        }
        return switch (invoice.getDocumentStatus()) {
            case TO_PRINT -> "Imprimir fatura";
            case TO_REQUEST -> "Pedir fatura";
            default -> null;
        };
    }

    private String paymentMethodCell(List<InvoicePaymentSummaryDTO> payments, Model model,
                                     ConstructionInvoice invoice) {
        if (payments.isEmpty()) {
            return null;
        }
        Set<String> methods = payments.stream().map(InvoicePaymentSummaryDTO::method).collect(Collectors.toSet());
        if (methods.size() > 1) {
            model.warnings.add("A fatura " + describe(invoice) + " foi paga por " + methods.size()
                    + " métodos diferentes — a coluna \"Metodo Pagamento\" leva o do último pagamento.");
        }
        String method = payments.get(payments.size() - 1).method();
        if (PaymentMethod.OUTRO.name().equals(method)) {
            model.warnings.add("A fatura " + describe(invoice) + " tem método de pagamento \"Outro\", "
                    + "que o vault não conhece — o detalhe vai nas observações.");
        }
        return methodLabel(method);
    }

    /**
     * A coluna Observações é <b>gerada</b> a partir dos pagamentos (contrato §4):
     * o vault escreve à mão "Pago por transferência em 28-08-2026 (extrato
     * ABANCA)" e, nos agregados, "…, 1 234,56 € junto com FT 12, FT 13 (…)". Um
     * pagamento parcial diz-se parcial e deixa a fatura por liquidar. Segue-se o
     * que estiver em {@code payment.notes} e {@code invoice.notes}.
     */
    private static String observations(ConstructionInvoice invoice, PaymentStatus status,
                                       List<InvoicePaymentSummaryDTO> payments, boolean creditNote,
                                       Map<UUID, ConstructionInvoice> byId) {
        List<String> parts = new ArrayList<>();
        for (InvoicePaymentSummaryDTO payment : payments) {
            String when = payment.paidOn() == null ? "?" : NOTE_DATE.format(payment.paidOn());
            String reference = isBlank(payment.reference()) ? "" : " (" + payment.reference().trim() + ")";
            String method = methodLabel(payment.method()).toLowerCase(Locale.ROOT);
            if (status == PaymentStatus.PARTIAL) {
                parts.add("Pago parcialmente " + money(payment.amountOnThisInvoice()) + " por " + method
                        + " em " + when + reference);
            } else if (payment.alsoCovers() != null && !payment.alsoCovers().isEmpty()) {
                parts.add("Pago por " + method + " em " + when + ", " + money(payment.paymentAmount())
                        + " junto com " + String.join(", ", payment.alsoCovers()) + reference);
            } else {
                parts.add("Pago por " + method + " em " + when + reference);
            }
            if (!isBlank(payment.notes())) {
                parts.add(payment.notes().trim());
            }
        }
        if (creditNote) {
            ConstructionInvoice origin = invoice.getRelatedInvoiceId() == null ? null
                    : byId.get(invoice.getRelatedInvoiceId());
            parts.add("Nota de crédito da fatura " + (origin == null ? "(fora desta obra)" : describe(origin)));
        }
        if (!isBlank(invoice.getNotes())) {
            parts.add(invoice.getNotes().trim());
        }
        return parts.isEmpty() ? null : String.join(NOTE_SEPARATOR, parts);
    }

    private static String rubricLabelOf(ConstructionExpense expense, Model model) {
        UUID itemId = expense.getBudgetItem().getId();
        if (!model.labelByItemId.containsKey(itemId)) {
            model.warnings.add("A despesa \"" + expenseDescription(expense)
                    + "\" está numa rubrica eliminada — sai sem rubrica.");
            return null;
        }
        return model.labelByItemId.get(itemId);
    }

    /** Mapa inverso do §4 do contrato — o texto que o vault usa na coluna "Metodo Pagamento". */
    static String methodLabel(String method) {
        if (method == null) {
            return "Outro";
        }
        return switch (method) {
            case "NUMERARIO" -> "Numerário";
            case "MULTIBANCO" -> "Pagamento MB";
            case "TRANSFERENCIA" -> "Transferência";
            default -> "Outro";
        };
    }

    /**
     * {@code <Art> — <Descrição>}, a forma da dropdown do vault (§6). A descrição
     * corta-se aos 70 caracteres exatamente como o script faz, porque a
     * {@code SUMIF} compara a célula inteira: a etiqueta da coluna Rubrica e a da
     * {@code TabelaRubricas} têm de ser iguais letra a letra.
     */
    static String rubricLabel(String code, String name) {
        String description = isBlank(name) ? "(sem descrição)" : name.replaceAll("\\s+", " ").trim();
        if (description.length() > LABEL_MAX_LENGTH) {
            description = description.substring(0, LABEL_MAX_LENGTH - 3).trim() + "...";
        }
        return code + " — " + description;
    }

    /** {@code "Lote A · "}, ou nada numa obra de um só lote. */
    static String lotPrefix(String lot) {
        return isBlank(lot) ? "" : lot.trim() + LOT_SEPARATOR;
    }

    /**
     * {@code "Orçamento - Lote A"}, dentro das regras do Excel para nomes de
     * folha: 31 caracteres, sem {@code []:*?/\}. O importador procura a folha
     * por este nome ao importar o orçamento de um lote.
     */
    static String lotSheetName(String lot) {
        String name = SHEET_BUDGET_LOT_PREFIX + lot.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        return name.length() > SHEET_NAME_MAX ? name.substring(0, SHEET_NAME_MAX).trim() : name;
    }

    private static int chapterOf(String code) {
        String head = code.split("\\.")[0];
        return head.matches("\\d+") ? Integer.parseInt(head) : 0;
    }

    private static String expenseDescription(ConstructionExpense expense) {
        if (!isBlank(expense.getName())) return expense.getName();
        if (!isBlank(expense.getDescription())) return expense.getDescription();
        return "";
    }

    private static String describe(ConstructionInvoice invoice) {
        if (!isBlank(invoice.getInvoiceNumber())) return invoice.getInvoiceNumber();
        if (!isBlank(invoice.getSupplierName())) return "de " + invoice.getSupplierName() + " (sem nº)";
        return "sem nº";
    }

    // ── escrita ───────────────────────────────────────────────

    private byte[] write(Model model, Set<BudgetExportSheet> sheets) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            applyVaultTheme(workbook);
            Styles styles = new Styles(workbook);
            if (sheets.contains(BudgetExportSheet.BUDGET)) {
                if (model.lots.isEmpty()) {
                    writeBudgetSheet(workbook, styles, model, SHEET_BUDGET, model.tree);
                } else {
                    for (LotTree lot : model.lots) {
                        writeBudgetSheet(workbook, styles, model, lotSheetName(lot.name()), lot.tree());
                    }
                }
            }
            XSSFSheet expensesSheet = null;
            if (sheets.contains(BudgetExportSheet.EXPENSES)) {
                expensesSheet = writeExpensesSheet(workbook, styles, model);
            }
            if (sheets.contains(BudgetExportSheet.COMPARISON)) {
                // A folha "Rubricas" nasce primeiro porque o painel a referencia por
                // nome de tabela e o POI resolve as fórmulas ao escrevê-las.
                int dropdownRows = writeRubricsSheet(workbook, styles, model);
                writeComparisonSheet(workbook, styles, model);
                workbook.setSheetOrder(SHEET_COMPARISON, workbook.getSheetIndex(SHEET_RUBRICS));
                addRubricDropdown(expensesSheet, model, dropdownRows);
            }
            if (expensesSheet != null) {
                // o vault abre na "Despesas" — é a folha onde se trabalha
                workbook.setActiveSheet(workbook.getSheetIndex(expensesSheet));
            }

            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException | RuntimeException e) {
            log.error("Falha a gerar o Excel da obra {}: {}", model.enterprise.getId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_WRITE_ERROR);
        }
    }

    // "Orçamento inicial" ────────────────────────────────────

    /**
     * A folha é o documento do orçamento, tal como está no vault (Vila Petrus):
     * "ORÇAMENTO", o bloco da empresa com o logo, Cliente/Obra/Data, e a tabela
     * {@code Rubrica | Descrição | Preço total} numa moldura — margem grossa por
     * fora, fina entre colunas, capítulos a verde, letra 9. Un., Quant, Preço Un
     * e Obs. ficam na app: a folha do vault não os tem (pedido do utilizador a
     * 2026-09-23). O importador encontra o cabeçalho sozinho, por isso o bloco de
     * cima não o atrapalha.
     */
    private void writeBudgetSheet(XSSFWorkbook workbook, Styles styles, Model model, String sheetName,
                                  BudgetTreeDTO tree) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        Look base = Look.of(BUDGET_FONT_SIZE);
        for (int c = 0; c <= 2; c++) sheet.setDefaultColumnStyle(c, styles.get(base));
        sheet.setColumnWidth(0, width(6.66));
        sheet.setColumnWidth(1, width(63.44));
        sheet.setColumnWidth(2, width(10));
        sheet.setZoom(130);

        Row title = sheet.createRow(1);
        CellStyle titleStyle = styles.get(base.bold().center());
        cell(title, 0, "ORÇAMENTO", titleStyle);
        cell(title, 1, (String) null, titleStyle);
        cell(title, 2, (String) null, titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        writeCompanyBlock(sheet, styles, base, model);

        Look codeCol = base.center().left(BorderStyle.MEDIUM).right(BorderStyle.THIN);
        Look textCol = base.left(BorderStyle.THIN).right(BorderStyle.THIN);
        Look priceCol = base.center().format(FORMAT_CURRENCY).left(BorderStyle.THIN).right(BorderStyle.MEDIUM);

        Row header = sheet.createRow(BUDGET_HEADER_ROW);
        cell(header, 0, "Rubrica", styles.get(codeCol.bottom(BorderStyle.THIN)));
        cell(header, 1, "Descrição", styles.get(textCol.bottom(BorderStyle.THIN)));
        cell(header, 2, "Preço total", styles.get(priceCol.h(HorizontalAlignment.LEFT).bottom(BorderStyle.THIN)));

        BudgetFrame frame = new BudgetFrame(
                styles.get(codeCol), styles.get(textCol.wrap()), styles.get(textCol.bold().wrap()), styles.get(priceCol),
                styles.get(codeCol.fill(COLOR_CHAPTER).top(BorderStyle.THIN).bottom(BorderStyle.THIN)),
                styles.get(textCol.bold().fill(COLOR_CHAPTER).top(BorderStyle.THIN).bottom(BorderStyle.THIN)),
                styles.get(priceCol.fill(COLOR_CHAPTER).top(BorderStyle.THIN).bottom(BorderStyle.THIN)));

        int[] rowIndex = {BUDGET_HEADER_ROW + 1};
        frameRow(sheet.createRow(rowIndex[0]++), frame); // a linha em branco a seguir ao cabeçalho
        for (BudgetItemNodeDTO root : tree.roots()) {
            writeBudgetRows(sheet, root, rowIndex, frame);
        }

        Row total = sheet.createRow(rowIndex[0]++);
        cell(total, 0, (String) null, frame.chapterCode());
        cell(total, 1, "TOTAL", frame.chapterText());
        cell(total, 2, tree.budgetTotal(), frame.chapterPrice());

        Row bottom = sheet.createRow(rowIndex[0]);
        cell(bottom, 0, (String) null, styles.get(codeCol.bottom(BorderStyle.MEDIUM)));
        cell(bottom, 1, (String) null, styles.get(base.bottom(BorderStyle.MEDIUM)));
        cell(bottom, 2, (String) null, styles.get(priceCol.left(BorderStyle.NONE).bottom(BorderStyle.MEDIUM)));
    }

    /** Linhas 3 a 10: a empresa (com o logo à direita), e Cliente/Obra/Data. */
    private void writeCompanyBlock(XSSFSheet sheet, Styles styles, Look base, Model model) {
        String[][] lines = {
                {"Empresa", COMPANY_NAME}, {null, COMPANY_ADDRESS}, {null, COMPANY_POSTCODE},
                {null, COMPANY_NIF}, {null, COMPANY_EMAIL},
                {"Cliente", null}, {"Obra", model.enterprise.getName()}, {"Data", null}};
        int first = 2, last = first + lines.length - 1, emailRow = first + 4;
        for (int i = 0; i < lines.length; i++) {
            int r = first + i;
            Look label = base.bold().center().left(BorderStyle.MEDIUM).right(BorderStyle.THIN);
            Look value = base.h(HorizontalAlignment.LEFT).v(VerticalAlignment.CENTER).wrap();
            Look edge = base.center().right(BorderStyle.THICK);
            if (r == first) {
                label = label.top(BorderStyle.MEDIUM);
                value = value.top(BorderStyle.MEDIUM);
                edge = edge.top(BorderStyle.THICK);
            }
            if (r == emailRow) {
                label = label.bottom(BorderStyle.THIN);
                value = value.bottom(BorderStyle.THIN);
            }
            if (r == last) {
                label = label.bottom(BorderStyle.MEDIUM);
                value = value.bottom(BorderStyle.MEDIUM);
                edge = edge.bottom(BorderStyle.THICK);
            }
            Row row = sheet.createRow(r);
            cell(row, 0, lines[i][0], styles.get(label));
            cell(row, 1, lines[i][1], styles.get(value));
            cell(row, 2, (String) null, styles.get(edge));
        }
        sheet.getRow(first).setHeightInPoints(32.25f);
        sheet.getRow(last).setHeightInPoints(15f);

        // o logo, no sítio onde está no vault: de B3 (à direita do texto) até C10
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = new XSSFClientAnchor(3147646, 32240, 661473, 178045, 1, first, 2, last - 1);
        anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
        drawing.createPicture(anchor, sheet.getWorkbook().addPicture(logo(), Workbook.PICTURE_TYPE_JPEG));
    }

    private void writeBudgetRows(XSSFSheet sheet, BudgetItemNodeDTO node, int[] rowIndex, BudgetFrame frame) {
        Row row = sheet.createRow(rowIndex[0]++);
        boolean chapter = node.depth() == 0 && !isBlank(node.code());
        if (chapter) {
            // no vault os capítulos levam o ponto no fim ("1.", "2.") — o importador tira-o
            String code = node.code().endsWith(".") ? node.code() : node.code() + ".";
            cell(row, 0, code, frame.chapterCode());
            cell(row, 1, node.name(), frame.chapterText());
            cell(row, 2, node.totalPrice(), frame.chapterPrice());
        } else {
            boolean heading = node.rowKind() == BudgetRowKind.HEADING || node.rowKind() == BudgetRowKind.NOTE;
            cell(row, 0, node.code(), frame.code());
            cell(row, 1, node.name(), heading ? frame.boldText() : frame.text());
            cell(row, 2, node.totalPrice(), frame.price());
        }
        for (BudgetItemNodeDTO child : node.children()) {
            writeBudgetRows(sheet, child, rowIndex, frame);
        }
    }

    /** Uma linha vazia dentro da moldura. */
    private static void frameRow(Row row, BudgetFrame frame) {
        cell(row, 0, (String) null, frame.code());
        cell(row, 1, (String) null, frame.text());
        cell(row, 2, (String) null, frame.price());
    }

    private record BudgetFrame(CellStyle code, CellStyle text, CellStyle boldText, CellStyle price,
                               CellStyle chapterCode, CellStyle chapterText, CellStyle chapterPrice) {}

    // "Despesas" ─────────────────────────────────────────────

    private XSSFSheet writeExpensesSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_EXPENSES);
        sheet.setZoom(115);

        // o aspeto é o da folha do vault: cabeçalho escuro, tudo alinhado ao topo
        // (as observações longas partem a linha), a data ao centro
        Row header = sheet.createRow(0);
        header.setHeightInPoints(31.95f);
        CellStyle headerStyle = styles.get(Look.of(11).bold().color(COLOR_WHITE).fill(COLOR_HEADER_DARK).center().wrap());
        for (int c = 0; c < EXPENSES_HEADERS.length; c++) cell(header, c, EXPENSES_HEADERS[c], headerStyle);

        Look top = Look.of(11).v(VerticalAlignment.TOP);
        CellStyle plain = styles.get(top);
        CellStyle text = styles.get(top.format(FORMAT_TEXT));
        CellStyle date = styles.get(top.format(FORMAT_DATE).h(HorizontalAlignment.CENTER));
        CellStyle money = styles.get(top.format(FORMAT_CURRENCY));
        CellStyle notes = styles.get(top.h(HorizontalAlignment.LEFT).wrap());
        CellStyle rubric = styles.get(top.format(FORMAT_TEXT).h(HorizontalAlignment.LEFT).wrap());

        int r = 1;
        for (ExpenseRow line : model.rows) {
            Row row = sheet.createRow(r++);
            cell(row, 0, line.number(), text);
            cell(row, 1, line.date(), date);
            cell(row, 2, line.description(), plain);
            cell(row, 3, line.amount(), money);
            cell(row, 4, line.paid() ? "x" : null, plain);
            cell(row, 5, line.method(), plain);
            cell(row, 6, line.bizdocs() ? "X" : null, plain);
            cell(row, 7, line.observations(), notes);
            cell(row, 8, line.rubric(), rubric);
            cell(row, 9, line.supplierName(), plain);
            cell(row, 10, line.supplierNif(), text);
        }
        if (model.rows.isEmpty()) {
            // uma tabela do Excel precisa de pelo menos uma linha de dados entre o cabeçalho e os totais
            sheet.createRow(r++);
        }

        int totalsRow = r;
        Row totals = sheet.createRow(totalsRow);
        Look bold = Look.of(11).bold();
        cell(totals, 0, "TOTAL", styles.get(bold));
        cell(totals, 1, (String) null, styles.get(bold.h(HorizontalAlignment.CENTER)));
        for (int c = 4; c < EXPENSES_HEADERS.length; c++) cell(totals, c, (String) null, styles.get(bold));

        // a tabela tem de existir antes de qualquer fórmula que a nomeie — o POI
        // resolve a referência estruturada ao escrever a fórmula
        XSSFTable table = createTable(sheet, TABLE_EXPENSES, 0, totalsRow, EXPENSES_HEADERS.length - 1, true);
        formula(totals, 3, "SUBTOTAL(109," + TABLE_EXPENSES + "[Valor])", styles.get(bold.format(FORMAT_CURRENCY)));
        for (CTTableColumn column : table.getCTTable().getTableColumns().getTableColumnList()) {
            if ("Valor".equals(column.getName())) {
                column.setTotalsRowFunction(STTotalsRowFunction.SUM);
            } else if ("Nº Fatura".equals(column.getName())) {
                column.setTotalsRowLabel("TOTAL");
            }
        }

        // Liquidada e Bizdocs: o "x" pinta a célula de verde (letra da cor do fundo), como no vault
        int lastData = Math.max(totalsRow - 1, 1);
        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        for (int c : new int[] {4, 6}) {
            XSSFConditionalFormattingRule rule = formatting.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"X\"");
            rule.createFontFormatting().setFontColor(rgb(COLOR_DONE));
            rule.createPatternFormatting().setFillBackgroundColor(rgb(COLOR_DONE));
            formatting.addConditionalFormatting(new CellRangeAddress[] {new CellRangeAddress(1, lastData, c, c)}, rule);
        }

        double[] widths = {20.66, 12.66, 55.66, 13.55, 14.66, 14.66, 10.33, 40.55, 28.66, 30, 14.66};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, width(widths[c]));
        return sheet;
    }

    // "Rubricas" ─────────────────────────────────────────────

    /** Devolve quantas etiquetas ficaram na coluna auxiliar M (a origem da dropdown). */
    private int writeRubricsSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_RUBRICS);
        sheet.setDefaultColumnStyle(0, styles.text);
        sheet.setDefaultColumnStyle(10, styles.text);
        // Vários lotes: coluna "Lote" no fim (L), para as colunas de sempre não mudarem de sítio
        boolean multiLot = !model.lots.isEmpty();
        String[] headers = multiLot ? RUBRICS_HEADERS_LOTS : RUBRICS_HEADERS;
        header(sheet, 0, headers, styles);

        int r = 1;
        for (RubricRow rubric : model.rubrics) {
            Row row = sheet.createRow(r++);
            int excelRow = r; // 1-based, para as fórmulas
            boolean chapter = rubric.level() == 1;
            CellStyle textStyle = chapter ? styles.textBold : styles.text;
            CellStyle plain = chapter ? styles.bold : null;
            CellStyle money = chapter ? styles.currencyBold : styles.currency;

            cell(row, 0, rubric.code(), textStyle);
            cell(row, 1, rubric.name(), plain);
            cell(row, 2, BigDecimal.valueOf(rubric.chapter()), plain);
            cell(row, 3, BigDecimal.valueOf(rubric.level()), plain);
            cell(row, 4, rubric.kind(), plain);
            cell(row, 5, rubric.budgeted(), money);

            // Gasto: as faturas cuja Rubrica seja a etiqueta completa, ou só o índice
            // ("4.2" / "4.2."), como o script do vault. Com lotes, o índice leva o lote à frente.
            String codeRef = multiLot ? "$L" + excelRow + "&\"" + LOT_SEPARATOR + "\"&$A" + excelRow : "$A" + excelRow;
            String matchLabel = TABLE_EXPENSES + "[Rubrica],$K" + excelRow;
            String matchCode = TABLE_EXPENSES + "[Rubrica]," + codeRef;
            String matchCodeDot = TABLE_EXPENSES + "[Rubrica]," + codeRef + "&\".\"";
            String values = "," + TABLE_EXPENSES + "[Valor]";
            formula(row, 6, "SUMIF(" + matchLabel + values + ")+SUMIF(" + matchCode + values + ")+SUMIF("
                    + matchCodeDot + values + ")", money);
            formula(row, 7, "F" + excelRow + "-G" + excelRow, money);
            formula(row, 8, "IF(F" + excelRow + "=0,\"\",G" + excelRow + "/F" + excelRow + ")", styles.percent);
            formula(row, 9, "COUNTIF(" + matchLabel + ")+COUNTIF(" + matchCode + ")+COUNTIF(" + matchCodeDot + ")",
                    plain);
            cell(row, 10, rubric.label(), textStyle);
            if (multiLot) {
                cell(row, 11, rubric.lot(), textStyle);
            }
        }
        int lastRow = Math.max(r - 1, 1);
        if (model.rubrics.isEmpty()) {
            sheet.createRow(1);
        }
        createTable(sheet, TABLE_RUBRICS, 0, lastRow, headers.length - 1, false);

        // coluna auxiliar M, escondida: só capítulos e itens — um título sem preço não é sítio para uma despesa
        int dropdownRows = 0;
        for (RubricRow rubric : model.rubrics) {
            if ("TÍTULO".equals(rubric.kind())) continue;
            Row row = sheet.getRow(dropdownRows) != null ? sheet.getRow(dropdownRows) : sheet.createRow(dropdownRows);
            cell(row, 12, rubric.label(), styles.text);
            dropdownRows++;
        }
        sheet.setColumnHidden(12, true);

        double[] widths = {10.77, 62.77, 6.77, 7.77, 11.77, 13.77, 13.77, 13.77, 13.77, 13.77, 40.77, 14.77};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, width(widths[c]));
        sheet.createFreezePane(0, 1);
        return dropdownRows;
    }

    /** A dropdown da coluna Rubrica (pedido 2 da decisão 26 do vault) — não bloqueia, só sugere. */
    private static void addRubricDropdown(XSSFSheet expensesSheet, Model model, int dropdownRows) {
        if (expensesSheet == null || dropdownRows == 0) {
            return;
        }
        int lastDataRow = Math.max(model.rows.size(), 1);
        DataValidationHelper helper = expensesSheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(
                "'" + SHEET_RUBRICS + "'!$M$1:$M$" + dropdownRows);
        CellRangeAddressList range = new CellRangeAddressList(1, lastDataRow, 8, 8);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(false);
        validation.setEmptyCellAllowed(true);
        validation.setSuppressDropDownArrow(true);
        validation.createPromptBox("Rubrica", "Escolhe da lista. O painel 'Orçamento vs Gasto' soma sozinho.");
        validation.setShowPromptBox(true);
        expensesSheet.addValidationData(validation);
    }

    // "Orçamento vs Gasto" ───────────────────────────────────

    private void writeComparisonSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_COMPARISON);
        // Com lotes, o capítulo 1 existe em todos: uma linha por lote e capítulo, pela ordem dos lotes
        boolean multiLot = !model.lots.isEmpty();
        List<String> lotOrder = model.lots.stream().map(LotTree::name).toList();
        List<RubricRow> chapters = model.rubrics.stream()
                .filter(rubric -> rubric.level() == 1)
                .sorted(Comparator.comparingInt((RubricRow rubric) -> multiLot ? lotOrder.indexOf(rubric.lot()) : 0)
                        .thenComparingInt(RubricRow::chapter))
                .toList();

        // a numeração é a do script do vault (1-based no Excel)
        final int kpiHeader = 4, kpiValue = 5, tableHeader = 8, first = 9;
        int totalRow = first + chapters.size();
        int unclassifiedRow = totalRow + 1;
        int unknownRow = totalRow + 2;

        String enterpriseLabel = isBlank(model.enterprise.getSlug()) ? model.enterprise.getName() : model.enterprise.getSlug();
        cell(sheet.createRow(0), 0, "ORÇAMENTO vs GASTO — " + enterpriseLabel, styles.title);
        cell(sheet.createRow(1), 0, "Gerado pela app Worksite em " + STAMP.format(LocalDateTime.now())
                + ". O Gasto é fórmula: assim que preencheres a coluna Rubrica na folha \"" + SHEET_EXPENSES
                + "\", isto atualiza-se sozinho.", styles.italic);

        String[] kpis = {"Orçamento total", "Custo classificado", "Por classificar", "Rubrica não reconhecida",
                "Total lançado", "% do orçamento", "Saldo"};
        // os totais numa caixa com todas as margens, como o script do vault desenha
        Look kpiLook = Look.of(13).bold().box(BorderStyle.THIN);
        CellStyle kpiCurrency = styles.get(kpiLook.format(FORMAT_CURRENCY));
        Row kpiHeaderRow = sheet.createRow(kpiHeader - 1);
        CellStyle kpiHeaderStyle = styles.get(Look.of(11).bold().wrap().box(BorderStyle.THIN));
        for (int i = 0; i < kpis.length; i++) cell(kpiHeaderRow, i + 1, kpis[i], kpiHeaderStyle);
        Row kpiRow = sheet.createRow(kpiValue - 1);
        formula(kpiRow, 1, "C" + totalRow, kpiCurrency);
        formula(kpiRow, 2, "D" + totalRow, kpiCurrency);
        formula(kpiRow, 3, "D" + unclassifiedRow, kpiCurrency);
        formula(kpiRow, 4, "D" + unknownRow, kpiCurrency);
        formula(kpiRow, 5, "SUM(" + TABLE_EXPENSES + "[Valor])", kpiCurrency);
        formula(kpiRow, 6, "IF(B" + kpiValue + "=0,\"\",F" + kpiValue + "/B" + kpiValue + ")",
                styles.get(kpiLook.format(FORMAT_PERCENT)));
        formula(kpiRow, 7, "B" + kpiValue + "-F" + kpiValue, kpiCurrency);

        cell(sheet.createRow(6), 0, "POR CAPÍTULO", styles.bold);
        String[] tableHeaders = {"Cap", "Rubrica", "Orçamentado", "Gasto", "Saldo", "% consumido", "Nº faturas"};
        Row tableHeaderRow = sheet.createRow(tableHeader - 1);
        CellStyle blueHeader = styles.get(Look.of(11).bold().color(COLOR_WHITE).fill(COLOR_PANEL_HEADER));
        for (int c = 0; c < tableHeaders.length; c++) cell(tableHeaderRow, c, tableHeaders[c], blueHeader);

        int r = first;
        for (RubricRow chapter : chapters) {
            Row row = sheet.createRow(r - 1);
            cell(row, 0, BigDecimal.valueOf(chapter.chapter()), null);
            cell(row, 1, lotPrefix(chapter.lot()) + chapter.name(), null);
            cell(row, 2, chapter.budgeted(), styles.currency);
            formula(row, 3, chapterSum(TABLE_RUBRICS + "[Gasto]", r, chapter), styles.currency);
            formula(row, 4, "C" + r + "-D" + r, styles.currency);
            formula(row, 5, "IF(C" + r + "=0,\"\",D" + r + "/C" + r + ")", styles.percent);
            formula(row, 6, chapterSum(TABLE_RUBRICS + "[Nº faturas]", r, chapter), null);
            r++;
        }

        int last = totalRow - 1;
        Row total = sheet.createRow(totalRow - 1);
        Look totalLook = Look.of(11).bold().top(BorderStyle.THIN);
        cell(total, 0, (String) null, styles.get(totalLook));
        cell(total, 1, "TOTAL", styles.get(totalLook));
        CellStyle totalCurrency = styles.get(totalLook.format(FORMAT_CURRENCY));
        formula(total, 2, "SUM(C" + first + ":C" + last + ")", totalCurrency);
        formula(total, 3, "SUM(D" + first + ":D" + last + ")", totalCurrency);
        formula(total, 4, "C" + totalRow + "-D" + totalRow, totalCurrency);
        formula(total, 5, "IF(C" + totalRow + "=0,\"\",D" + totalRow + "/C" + totalRow + ")",
                styles.get(totalLook.format(FORMAT_PERCENT)));
        formula(total, 6, "SUM(G" + first + ":G" + last + ")", styles.get(totalLook));

        CellStyle italicCurrency = styles.get(Look.of(11).italic().format(FORMAT_CURRENCY));
        Row unclassified = sheet.createRow(unclassifiedRow - 1);
        cell(unclassified, 1, "Faturas ainda sem rubrica", styles.italic);
        formula(unclassified, 3, "SUMPRODUCT((" + TABLE_EXPENSES + "[Rubrica]=\"\")*" + TABLE_EXPENSES + "[Valor])",
                italicCurrency);
        formula(unclassified, 6, "SUMPRODUCT(--(" + TABLE_EXPENSES + "[Rubrica]=\"\"))", styles.italic);

        Row unknown = sheet.createRow(unknownRow - 1);
        cell(unknown, 1, "Rubrica escrita que não existe no orçamento", styles.italic);
        formula(unknown, 3, "SUM(" + TABLE_EXPENSES + "[Valor])-D" + totalRow + "-D" + unclassifiedRow, italicCurrency);
        formula(unknown, 6, "SUMPRODUCT(--(" + TABLE_EXPENSES + "[Rubrica]<>\"\"))-G" + totalRow, styles.italic);

        cell(sheet.createRow(unknownRow + 1), 0, "O detalhe por sub-rubrica está na folha \"" + SHEET_RUBRICS
                + "\" — a mesma lista que alimenta a dropdown da coluna Rubrica.", styles.italic);

        // barras de dados na % consumida, vermelho acima dos 100% e no saldo negativo — as regras do script
        XSSFSheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        if (!chapters.isEmpty()) {
            CellRangeAddress[] percent = {new CellRangeAddress(first - 1, last - 1, 5, 5)};
            XSSFConditionalFormattingRule bar = formatting.createConditionalFormattingRule(rgb(COLOR_DATA_BAR));
            XSSFDataBarFormatting barFormat = bar.getDataBarFormatting();
            barFormat.getMinThreshold().setRangeType(ConditionalFormattingThreshold.RangeType.NUMBER);
            barFormat.getMinThreshold().setValue(0d);
            barFormat.getMaxThreshold().setRangeType(ConditionalFormattingThreshold.RangeType.NUMBER);
            barFormat.getMaxThreshold().setValue(1d);
            formatting.addConditionalFormatting(percent, bar);

            XSSFConditionalFormattingRule over = formatting.createConditionalFormattingRule(ComparisonOperator.GT, "1");
            XSSFFontFormatting overFont = over.createFontFormatting();
            overFont.setFontColor(rgb(COLOR_RED));
            overFont.setFontStyle(false, true);
            formatting.addConditionalFormatting(percent, over);
        }
        XSSFConditionalFormattingRule negative = formatting.createConditionalFormattingRule(ComparisonOperator.LT, "0");
        negative.createFontFormatting().setFontColor(rgb(COLOR_RED));
        formatting.addConditionalFormatting(new CellRangeAddress[] {new CellRangeAddress(first - 1, totalRow - 1, 4, 4)},
                negative);

        sheet.setColumnWidth(0, width(6.77));
        sheet.setColumnWidth(1, width(46.77));
        for (int c = 2; c <= 7; c++) sheet.setColumnWidth(c, width(17.77));
        sheet.setZoom(115);
        sheet.createFreezePane(0, first - 1);
    }

    /** A soma de um capítulo no painel — com lotes, só as rubricas desse lote. */
    private static String chapterSum(String column, int row, RubricRow chapter) {
        if (chapter.lot() == null) {
            return "SUMIF(" + TABLE_RUBRICS + "[Cap],$A" + row + "," + column + ")";
        }
        return "SUMIFS(" + column + "," + TABLE_RUBRICS + "[Cap],$A" + row + "," + TABLE_RUBRICS + "[Lote],\""
                + chapter.lot().replace("\"", "\"\"") + "\")";
    }

    // ── POI helpers ───────────────────────────────────────────

    /** Troca o tema do livro pelo do vault (as cores de tema das tabelas vêm daqui). */
    private static void applyVaultTheme(XSSFWorkbook workbook) throws IOException {
        workbook.getStylesSource().ensureThemesTable();
        try (InputStream in = resource(THEME_RESOURCE)) {
            workbook.getTheme().readFrom(in);
        }
    }

    private static byte[] logo() {
        try (InputStream in = resource(LOGO_RESOURCE)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream resource(String path) {
        InputStream in = BudgetExcelExportService.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Recurso em falta no classpath: " + path);
        }
        return in;
    }

    /** A largura de coluna tal como o Excel a mostra (nº de caracteres), nas unidades do POI. */
    private static int width(double characters) {
        return (int) Math.round(characters * 256);
    }

    private static XSSFColor rgb(String hex) {
        return new XSSFColor(HexFormat.of().parseHex(hex), null);
    }

    private static XSSFTable createTable(XSSFSheet sheet, String name, int firstRow, int lastRow,
                                         int lastCol, boolean withTotals) {
        AreaReference area = new AreaReference(new CellReference(firstRow, 0), new CellReference(lastRow, lastCol),
                SpreadsheetVersion.EXCEL2007);
        XSSFTable table = sheet.createTable(area);
        table.setName(name);
        table.setDisplayName(name);
        CTTable ct = table.getCTTable();
        int filterLastRow = withTotals ? lastRow - 1 : lastRow;
        ct.addNewAutoFilter().setRef(new AreaReference(new CellReference(firstRow, 0),
                new CellReference(filterLastRow, lastCol), SpreadsheetVersion.EXCEL2007).formatAsString());
        if (withTotals) {
            ct.setTotalsRowCount(1);
        } else {
            ct.setTotalsRowShown(false);
        }
        CTTableStyleInfo style = ct.addNewTableStyleInfo();
        style.setName(TABLE_STYLE);
        style.setShowRowStripes(true);
        style.setShowColumnStripes(false);
        style.setShowFirstColumn(false);
        style.setShowLastColumn(false);
        return table;
    }

    private static void header(XSSFSheet sheet, int rowIndex, String[] headers, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        for (int c = 0; c < headers.length; c++) {
            cell(row, c, headers[c], styles.bold);
        }
    }

    private static void cell(Row row, int col, String value, CellStyle style) {
        if (value == null && style == null) return;
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private static void cell(Row row, int col, BigDecimal value, CellStyle style) {
        if (value == null && style == null) return;
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value.doubleValue());
        if (style != null) cell.setCellStyle(style);
    }

    private static void cell(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value);
        if (style != null) cell.setCellStyle(style);
    }

    private static void formula(Row row, int col, String formula, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellFormula(formula);
        if (style != null) cell.setCellStyle(style);
    }

    /**
     * O aspeto de uma célula, como valor — duas células com o mesmo {@code Look}
     * partilham o mesmo estilo no livro ({@link Styles#get}). As folhas do vault
     * têm muitas combinações de margens (a moldura do orçamento), e criar um
     * estilo por célula esgotava o limite do Excel.
     */
    record Look(int size, boolean isBold, boolean isItalic, String color, String fill, String format,
                HorizontalAlignment h, VerticalAlignment v, boolean wraps,
                BorderStyle left, BorderStyle right, BorderStyle top, BorderStyle bottom) {

        static Look of(int size) {
            return new Look(size, false, false, null, null, null, HorizontalAlignment.GENERAL,
                    VerticalAlignment.BOTTOM, false, BorderStyle.NONE, BorderStyle.NONE, BorderStyle.NONE, BorderStyle.NONE);
        }

        Look bold() { return new Look(size, true, isItalic, color, fill, format, h, v, wraps, left, right, top, bottom); }
        Look italic() { return new Look(size, isBold, true, color, fill, format, h, v, wraps, left, right, top, bottom); }
        Look color(String rgb) { return new Look(size, isBold, isItalic, rgb, fill, format, h, v, wraps, left, right, top, bottom); }
        Look fill(String rgb) { return new Look(size, isBold, isItalic, color, rgb, format, h, v, wraps, left, right, top, bottom); }
        Look format(String f) { return new Look(size, isBold, isItalic, color, fill, f, h, v, wraps, left, right, top, bottom); }
        Look h(HorizontalAlignment a) { return new Look(size, isBold, isItalic, color, fill, format, a, v, wraps, left, right, top, bottom); }
        Look v(VerticalAlignment a) { return new Look(size, isBold, isItalic, color, fill, format, h, a, wraps, left, right, top, bottom); }
        Look wrap() { return new Look(size, isBold, isItalic, color, fill, format, h, v, true, left, right, top, bottom); }
        Look center() { return h(HorizontalAlignment.CENTER).v(VerticalAlignment.CENTER); }
        Look left(BorderStyle b) { return new Look(size, isBold, isItalic, color, fill, format, h, v, wraps, b, right, top, bottom); }
        Look right(BorderStyle b) { return new Look(size, isBold, isItalic, color, fill, format, h, v, wraps, left, b, top, bottom); }
        Look top(BorderStyle b) { return new Look(size, isBold, isItalic, color, fill, format, h, v, wraps, left, right, b, bottom); }
        Look bottom(BorderStyle b) { return new Look(size, isBold, isItalic, color, fill, format, h, v, wraps, left, right, top, b); }
        Look box(BorderStyle b) { return left(b).right(b).top(b).bottom(b); }
    }

    /** Os estilos partilhados do livro — o POI limita o nº de estilos, por isso criam-se uma vez. */
    private static final class Styles {
        final CellStyle bold, boldWrap, italic, title, text, textBold, date,
                currency, currencyBold, percent, kpiCurrency, kpiPercent;

        private final XSSFWorkbook workbook;
        private final DataFormat formats;
        private final Map<Look, CellStyle> byLook = new HashMap<>();
        private final Map<String, XSSFFont> fonts = new HashMap<>();

        CellStyle get(Look look) {
            return byLook.computeIfAbsent(look, this::create);
        }

        private CellStyle create(Look look) {
            XSSFCellStyle style = workbook.createCellStyle();
            style.setFont(font(look));
            if (look.format() != null) style.setDataFormat(formats.getFormat(look.format()));
            if (look.fill() != null) {
                style.setFillForegroundColor(rgb(look.fill()));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            style.setAlignment(look.h());
            style.setVerticalAlignment(look.v());
            style.setWrapText(look.wraps());
            style.setBorderLeft(look.left());
            style.setBorderRight(look.right());
            style.setBorderTop(look.top());
            style.setBorderBottom(look.bottom());
            return style;
        }

        private XSSFFont font(Look look) {
            String key = look.size() + "|" + look.isBold() + "|" + look.isItalic() + "|" + look.color();
            return fonts.computeIfAbsent(key, k -> {
                XSSFFont font = workbook.createFont();
                font.setFontName("Calibri");
                font.setFontHeightInPoints((short) look.size());
                font.setBold(look.isBold());
                font.setItalic(look.isItalic());
                if (look.color() != null) font.setColor(rgb(look.color()));
                return font;
            });
        }

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            DataFormat formats = workbook.createDataFormat();
            this.formats = formats;
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            Font italicFont = workbook.createFont();
            italicFont.setItalic(true);
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            Font kpiFont = workbook.createFont();
            kpiFont.setBold(true);
            kpiFont.setFontHeightInPoints((short) 13);

            bold = styled(workbook, boldFont, null, formats);
            boldWrap = styled(workbook, boldFont, null, formats);
            boldWrap.setWrapText(true);
            italic = styled(workbook, italicFont, null, formats);
            title = styled(workbook, titleFont, null, formats);
            text = styled(workbook, null, FORMAT_TEXT, formats);
            textBold = styled(workbook, boldFont, FORMAT_TEXT, formats);
            date = styled(workbook, null, FORMAT_DATE, formats);
            currency = styled(workbook, null, FORMAT_CURRENCY, formats);
            currencyBold = styled(workbook, boldFont, FORMAT_CURRENCY, formats);
            percent = styled(workbook, null, FORMAT_PERCENT, formats);
            kpiCurrency = styled(workbook, kpiFont, FORMAT_CURRENCY, formats);
            kpiPercent = styled(workbook, kpiFont, FORMAT_PERCENT, formats);
        }

        private static CellStyle styled(XSSFWorkbook workbook, Font font, String format, DataFormat formats) {
            CellStyle style = workbook.createCellStyle();
            if (font != null) style.setFont(font);
            if (format != null) style.setDataFormat(formats.getFormat(format));
            return style;
        }
    }

    // ── modelo em memória ─────────────────────────────────────

    private static final class Model {
        Enterprise enterprise;
        String fileName;
        BudgetTreeDTO tree;
        /** Só numa obra com vários lotes com rubricas; vazio = um só orçamento, como antes da V39. */
        final List<LotTree> lots = new ArrayList<>();
        final List<RubricRow> rubrics = new ArrayList<>();
        final Map<UUID, String> labelByItemId = new HashMap<>();
        final List<ExpenseRow> rows = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        int invoiceCount, unclassifiedInvoiceCount, manualExpenseCount, creditNoteCount,
                partialPaymentCount, missingNumberCount, needsReviewCount;

        boolean hasBudget() {
            return tree != null && tree.itemCount() > 0;
        }

        BudgetExportSummaryDTO toSummary(DocumentsExportSummaryDTO documents) {
            BigDecimal expensesTotal = rows.stream()
                    .map(ExpenseRow::amount)
                    .filter(amount -> amount != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int chapters = (int) rubrics.stream().filter(rubric -> rubric.level() == 1).count();
            return new BudgetExportSummaryDTO(
                    enterprise.getId(), enterprise.getName(), Boolean.TRUE.equals(enterprise.getIsTest()),
                    fileName, hasBudget(),
                    tree == null ? 0 : tree.itemCount(), chapters,
                    tree == null ? BigDecimal.ZERO : nullToZero(tree.budgetTotal()),
                    invoiceCount, rows.size(), expensesTotal,
                    unclassifiedInvoiceCount, manualExpenseCount, creditNoteCount, partialPaymentCount,
                    missingNumberCount, needsReviewCount, List.copyOf(warnings), documents);
        }
    }

    record LotTree(String name, BudgetTreeDTO tree) {}

    record RubricRow(String code, String name, int chapter, int level, String kind,
                     BigDecimal budgeted, String label, String lot) {}

    record ExpenseRow(String number, LocalDate date, String description, BigDecimal amount, boolean paid,
                      String method, boolean bizdocs, String observations, String rubric,
                      String supplierName, String supplierNif) {}

    // ── utilitários ───────────────────────────────────────────

    private static final DecimalFormat MONEY;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        MONEY = new DecimalFormat("#,##0.00 €", symbols);
    }

    /** {@code 1 234,56 €} — como o vault escreve os valores em prosa. */
    static String money(BigDecimal value) {
        return value == null ? "?" : MONEY.format(value);
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
