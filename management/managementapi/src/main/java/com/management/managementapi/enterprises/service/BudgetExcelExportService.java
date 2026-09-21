package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.request.BudgetExportSheet;
import com.management.managementapi.enterprises.dto.budget.response.BudgetExportSummaryDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetItemNodeDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetTreeDTO;
import com.management.managementapi.enterprises.dto.budget.response.DocumentsExportSummaryDTO;
import com.management.managementapi.enterprises.dto.payment.InvoicePaymentSummaryDTO;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
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
import java.io.OutputStream;
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

    static final String[] BUDGET_HEADERS =
            {"Rubrica", "Descrição", "Un.", "Quant", "Preço Un", "Preço total", "Obs."};
    static final String[] EXPENSES_HEADERS = {
            "Nº Fatura", "Data", "Produto/Serviço", "Valor", "Liquidada", "Metodo Pagamento",
            "Bizdocs", "Observações", "Rubrica", "Fornecedor", "NIF"};
    static final String[] RUBRICS_HEADERS = {
            "Art", "Descrição", "Cap", "Nível", "Tipo", "Orçamentado", "Gasto", "Saldo",
            "% consumido", "Nº faturas", "Etiqueta"};

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
        for (BudgetItemNodeDTO root : tree.roots()) {
            flatten(root, null, model);
        }
    }

    /**
     * Achata a árvore para a {@code TabelaRubricas} e guarda, por rubrica, a
     * etiqueta que a coluna Rubrica da folha "Despesas" vai levar. Uma rubrica
     * sem índice (as "Alternativa …", que aceitam despesas) herda a etiqueta do
     * artigo com índice mais próximo acima — é o que o vault também faz quando
     * soma o valor da alternativa ao artigo de cima.
     */
    private void flatten(BudgetItemNodeDTO node, String inheritedLabel, Model model) {
        String label = inheritedLabel;
        if (!isBlank(node.code())) {
            label = rubricLabel(node.code(), node.name());
            int level = node.code().split("\\.").length;
            BigDecimal budgeted = nullToZero(node.rolledUpBudget());
            String kind = level == 1 ? "CAPÍTULO" : budgeted.signum() > 0 ? "ITEM" : "TÍTULO";
            model.rubrics.add(new RubricRow(node.code(), node.name(), chapterOf(node.code()),
                    level, kind, budgeted, label));
        } else if (node.acceptsExpenses() && node.ownExpenseCount() > 0) {
            model.warnings.add("A rubrica \"" + node.name() + "\" não tem índice — as suas "
                    + node.ownExpenseCount() + " despesas saem com a rubrica do artigo acima"
                    + (label != null ? " (" + label + ")" : "") + ".");
        }
        model.labelByItemId.put(node.id(), label);
        for (BudgetItemNodeDTO child : node.children()) {
            flatten(child, label, model);
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

            Styles styles = new Styles(workbook);
            if (sheets.contains(BudgetExportSheet.BUDGET)) {
                writeBudgetSheet(workbook, styles, model);
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

            workbook.setForceFormulaRecalculation(true);
            workbook.write(out);
            return out.toByteArray();

        } catch (IOException | RuntimeException e) {
            log.error("Falha a gerar o Excel da obra {}: {}", model.enterprise.getId(), e.getMessage(), e);
            throw new BusinessException(ErrorCode.BUDGET_EXPORT_WRITE_ERROR);
        }
    }

    // "Orçamento inicial" ────────────────────────────────────

    private void writeBudgetSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_BUDGET);
        sheet.setDefaultColumnStyle(0, styles.text);
        header(sheet, 0, BUDGET_HEADERS, styles);

        int[] rowIndex = {1};
        for (BudgetItemNodeDTO root : model.tree.roots()) {
            writeBudgetRows(sheet, root, rowIndex, styles);
        }

        Row total = sheet.createRow(rowIndex[0]);
        cell(total, 1, "TOTAL", styles.bold);
        cell(total, 5, model.tree.budgetTotal(), styles.currencyBold);

        sheet.setColumnWidth(0, 10 * 256);
        sheet.setColumnWidth(1, 62 * 256);
        sheet.setColumnWidth(2, 6 * 256);
        for (int c = 3; c <= 5; c++) sheet.setColumnWidth(c, 14 * 256);
        sheet.setColumnWidth(6, 30 * 256);
        sheet.createFreezePane(0, 1);
    }

    private void writeBudgetRows(XSSFSheet sheet, BudgetItemNodeDTO node, int[] rowIndex, Styles styles) {
        Row row = sheet.createRow(rowIndex[0]++);
        boolean chapter = node.depth() == 0 && !isBlank(node.code());
        cell(row, 0, node.code(), styles.text);
        cell(row, 1, node.name(), chapter ? styles.bold : null);
        cell(row, 2, node.unit(), null);
        cell(row, 3, node.quantity(), null);
        cell(row, 4, node.unitPrice(), styles.currency);
        cell(row, 5, node.totalPrice(), chapter ? styles.currencyBold : styles.currency);
        cell(row, 6, node.observations(), null);
        for (BudgetItemNodeDTO child : node.children()) {
            writeBudgetRows(sheet, child, rowIndex, styles);
        }
    }

    // "Despesas" ─────────────────────────────────────────────

    private XSSFSheet writeExpensesSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_EXPENSES);
        header(sheet, 0, EXPENSES_HEADERS, styles);

        int r = 1;
        for (ExpenseRow line : model.rows) {
            Row row = sheet.createRow(r++);
            cell(row, 0, line.number(), styles.text);
            cell(row, 1, line.date(), styles.date);
            cell(row, 2, line.description(), null);
            cell(row, 3, line.amount(), styles.currency);
            cell(row, 4, line.paid() ? "Sim" : null, null);
            cell(row, 5, line.method(), null);
            cell(row, 6, line.bizdocs() ? "X" : null, null);
            cell(row, 7, line.observations(), null);
            cell(row, 8, line.rubric(), styles.text);
            cell(row, 9, line.supplierName(), null);
            cell(row, 10, line.supplierNif(), styles.text);
        }
        if (model.rows.isEmpty()) {
            // uma tabela do Excel precisa de pelo menos uma linha de dados entre o cabeçalho e os totais
            sheet.createRow(r++);
        }

        int totalsRow = r;
        Row totals = sheet.createRow(totalsRow);
        cell(totals, 0, "TOTAL", styles.bold);

        // a tabela tem de existir antes de qualquer fórmula que a nomeie — o POI
        // resolve a referência estruturada ao escrever a fórmula
        XSSFTable table = createTable(sheet, TABLE_EXPENSES, 0, totalsRow, EXPENSES_HEADERS.length - 1, true);
        formula(totals, 3, "SUBTOTAL(109," + TABLE_EXPENSES + "[Valor])", styles.currencyBold);
        for (CTTableColumn column : table.getCTTable().getTableColumns().getTableColumnList()) {
            if ("Valor".equals(column.getName())) {
                column.setTotalsRowFunction(STTotalsRowFunction.SUM);
            } else if ("Nº Fatura".equals(column.getName())) {
                column.setTotalsRowLabel("TOTAL");
            }
        }

        int[] widths = {20, 12, 50, 14, 10, 18, 9, 50, 40, 30, 12};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
        sheet.createFreezePane(0, 1);
        return sheet;
    }

    // "Rubricas" ─────────────────────────────────────────────

    /** Devolve quantas etiquetas ficaram na coluna auxiliar M (a origem da dropdown). */
    private int writeRubricsSheet(XSSFWorkbook workbook, Styles styles, Model model) {
        XSSFSheet sheet = workbook.createSheet(SHEET_RUBRICS);
        sheet.setDefaultColumnStyle(0, styles.text);
        sheet.setDefaultColumnStyle(10, styles.text);
        header(sheet, 0, RUBRICS_HEADERS, styles);

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
            // ("4.2" / "4.2."), como o script do vault
            String matchLabel = TABLE_EXPENSES + "[Rubrica],$K" + excelRow;
            String matchCode = TABLE_EXPENSES + "[Rubrica],$A" + excelRow;
            String matchCodeDot = TABLE_EXPENSES + "[Rubrica],$A" + excelRow + "&\".\"";
            String values = "," + TABLE_EXPENSES + "[Valor]";
            formula(row, 6, "SUMIF(" + matchLabel + values + ")+SUMIF(" + matchCode + values + ")+SUMIF("
                    + matchCodeDot + values + ")", money);
            formula(row, 7, "F" + excelRow + "-G" + excelRow, money);
            formula(row, 8, "IF(F" + excelRow + "=0,\"\",G" + excelRow + "/F" + excelRow + ")", styles.percent);
            formula(row, 9, "COUNTIF(" + matchLabel + ")+COUNTIF(" + matchCode + ")+COUNTIF(" + matchCodeDot + ")",
                    plain);
            cell(row, 10, rubric.label(), textStyle);
        }
        int lastRow = Math.max(r - 1, 1);
        if (model.rubrics.isEmpty()) {
            sheet.createRow(1);
        }
        createTable(sheet, TABLE_RUBRICS, 0, lastRow, RUBRICS_HEADERS.length - 1, false);

        // coluna auxiliar M, escondida: só capítulos e itens — um título sem preço não é sítio para uma despesa
        int dropdownRows = 0;
        for (RubricRow rubric : model.rubrics) {
            if ("TÍTULO".equals(rubric.kind())) continue;
            Row row = sheet.getRow(dropdownRows) != null ? sheet.getRow(dropdownRows) : sheet.createRow(dropdownRows);
            cell(row, 12, rubric.label(), styles.text);
            dropdownRows++;
        }
        sheet.setColumnHidden(12, true);

        int[] widths = {10, 62, 6, 7, 11, 13, 13, 13, 13, 13, 40};
        for (int c = 0; c < widths.length; c++) sheet.setColumnWidth(c, widths[c] * 256);
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
        List<RubricRow> chapters = model.rubrics.stream()
                .filter(rubric -> rubric.level() == 1)
                .sorted(Comparator.comparingInt(RubricRow::chapter))
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

        String[] kpis = {"Orçamento total", "Gasto classificado", "Por classificar", "Rubrica não reconhecida",
                "Total lançado", "% do orçamento", "Saldo"};
        Row kpiHeaderRow = sheet.createRow(kpiHeader - 1);
        for (int i = 0; i < kpis.length; i++) cell(kpiHeaderRow, i + 1, kpis[i], styles.boldWrap);
        Row kpiRow = sheet.createRow(kpiValue - 1);
        formula(kpiRow, 1, "C" + totalRow, styles.kpiCurrency);
        formula(kpiRow, 2, "D" + totalRow, styles.kpiCurrency);
        formula(kpiRow, 3, "D" + unclassifiedRow, styles.kpiCurrency);
        formula(kpiRow, 4, "D" + unknownRow, styles.kpiCurrency);
        formula(kpiRow, 5, "SUM(" + TABLE_EXPENSES + "[Valor])", styles.kpiCurrency);
        formula(kpiRow, 6, "IF(B" + kpiValue + "=0,\"\",F" + kpiValue + "/B" + kpiValue + ")", styles.kpiPercent);
        formula(kpiRow, 7, "B" + kpiValue + "-F" + kpiValue, styles.kpiCurrency);

        cell(sheet.createRow(6), 0, "POR CAPÍTULO", styles.bold);
        header(sheet, tableHeader - 1, new String[] {"Cap", "Rubrica", "Orçamentado", "Gasto", "Saldo",
                "% consumido", "Nº faturas"}, styles);

        int r = first;
        for (RubricRow chapter : chapters) {
            Row row = sheet.createRow(r - 1);
            cell(row, 0, BigDecimal.valueOf(chapter.chapter()), null);
            cell(row, 1, chapter.name(), null);
            cell(row, 2, chapter.budgeted(), styles.currency);
            formula(row, 3, "SUMIF(" + TABLE_RUBRICS + "[Cap],$A" + r + "," + TABLE_RUBRICS + "[Gasto])", styles.currency);
            formula(row, 4, "C" + r + "-D" + r, styles.currency);
            formula(row, 5, "IF(C" + r + "=0,\"\",D" + r + "/C" + r + ")", styles.percent);
            formula(row, 6, "SUMIF(" + TABLE_RUBRICS + "[Cap],$A" + r + "," + TABLE_RUBRICS + "[Nº faturas])", null);
            r++;
        }

        int last = totalRow - 1;
        Row total = sheet.createRow(totalRow - 1);
        cell(total, 1, "TOTAL", styles.bold);
        formula(total, 2, "SUM(C" + first + ":C" + last + ")", styles.currencyBold);
        formula(total, 3, "SUM(D" + first + ":D" + last + ")", styles.currencyBold);
        formula(total, 4, "C" + totalRow + "-D" + totalRow, styles.currencyBold);
        formula(total, 5, "IF(C" + totalRow + "=0,\"\",D" + totalRow + "/C" + totalRow + ")", styles.percent);
        formula(total, 6, "SUM(G" + first + ":G" + last + ")", styles.bold);

        Row unclassified = sheet.createRow(unclassifiedRow - 1);
        cell(unclassified, 1, "Faturas ainda sem rubrica", styles.italic);
        formula(unclassified, 3, "SUMPRODUCT((" + TABLE_EXPENSES + "[Rubrica]=\"\")*" + TABLE_EXPENSES + "[Valor])",
                styles.currency);
        formula(unclassified, 6, "SUMPRODUCT(--(" + TABLE_EXPENSES + "[Rubrica]=\"\"))", null);

        Row unknown = sheet.createRow(unknownRow - 1);
        cell(unknown, 1, "Rubrica escrita que não existe no orçamento", styles.italic);
        formula(unknown, 3, "SUM(" + TABLE_EXPENSES + "[Valor])-D" + totalRow + "-D" + unclassifiedRow, styles.currency);
        formula(unknown, 6, "SUMPRODUCT(--(" + TABLE_EXPENSES + "[Rubrica]<>\"\"))-G" + totalRow, null);

        cell(sheet.createRow(unknownRow + 1), 0, "O detalhe por sub-rubrica está na folha \"" + SHEET_RUBRICS
                + "\" — a mesma lista que alimenta a dropdown da coluna Rubrica.", styles.italic);

        sheet.setColumnWidth(0, 6 * 256);
        sheet.setColumnWidth(1, 46 * 256);
        for (int c = 2; c <= 7; c++) sheet.setColumnWidth(c, 17 * 256);
        sheet.createFreezePane(0, first - 1);
    }

    // ── POI helpers ───────────────────────────────────────────

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

    /** Os estilos partilhados do livro — o POI limita o nº de estilos, por isso criam-se uma vez. */
    private static final class Styles {
        final CellStyle bold, boldWrap, italic, title, text, textBold, date,
                currency, currencyBold, percent, kpiCurrency, kpiPercent;

        Styles(XSSFWorkbook workbook) {
            DataFormat formats = workbook.createDataFormat();
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

    record RubricRow(String code, String name, int chapter, int level, String kind,
                     BigDecimal budgeted, String label) {}

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
