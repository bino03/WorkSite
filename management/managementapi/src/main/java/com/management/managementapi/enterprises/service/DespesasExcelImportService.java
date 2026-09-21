package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteCreateDTO;
import com.management.managementapi.enterprises.dto.invoice.request.CreditNoteExpenseLineDTO;
import com.management.managementapi.enterprises.dto.invoice.request.ExpensesImportAnswersDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceRegisterDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceSplitLineDTO;
import com.management.managementapi.enterprises.dto.invoice.request.InvoiceTransferDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportInvoiceDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportIssueDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportQuestionDTO;
import com.management.managementapi.enterprises.dto.invoice.response.ExpensesImportResultDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentRequestDTO;
import com.management.managementapi.enterprises.dto.payment.AggregatePaymentResultDTO;
import com.management.managementapi.enterprises.dto.payment.MarkPaidRequestDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.ConstructionExpense;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.ConstructionInvoice.DocumentStatus;
import com.management.managementapi.enterprises.model.ConstructionInvoice.Scope;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.enums.PaymentMethod;
import com.management.managementapi.enterprises.model.enums.PaymentStatus;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionExpenseRepository;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Importa a folha "Despesas" do {@code Despesas - <Obra>.xlsx} do vault da
 * Vilatro — o sentido Excel → app do contrato de docs/excel-parity.md (§3, §4,
 * §9). É o par do {@link BudgetExcelExportService}, que faz o sentido inverso.
 *
 * O que a folha traz é uma linha por (fatura, rubrica); o que a app guarda é
 * uma fatura com N despesas, os pagamentos à parte e as notas de crédito
 * ligadas à fatura de origem. A parte não-trivial é reconstruir isso:
 * <ol>
 *   <li>agrupar as linhas pelo nº de fatura (as sem nº agrupam-se só quando são
 *       contíguas e iguais em tudo menos na rubrica);</li>
 *   <li>uma linha negativa é uma nota de crédito — a fatura de origem vem nas
 *       observações quando fomos nós a escrever a folha, e é <b>perguntada</b>
 *       quando foi a Vilatro;</li>
 *   <li>"Liquidada" é um pagamento: o método vem da coluna, a data e a
 *       referência das observações ("Pago por … em dd-mm-aaaa (ref)"); faturas
 *       com a mesma observação podem ter sido pagas num só movimento — também
 *       se pergunta.</li>
 * </ol>
 *
 * O {@code dryRun} devolve tudo isto sem gravar; a gravação exige zero erros,
 * todas as perguntas respondidas e os totais a bater certo (passo 9 do §9) —
 * qualquer falha anula a transação inteira.
 *
 * A quarentena ({@code UNIDENTIFIED}) lê a folha "Por identificar" do
 * {@code Faturas por identificar.xlsx} (§6): as mesmas 8 colunas mais
 * "Empreendimento", "Obras possíveis", "Perguntar a" e "Aqui desde". Uma linha
 * com "Empreendimento" preenchido entra em quarentena e é transferida na mesma
 * transação para essa obra (ou para as despesas da empresa), com a razão no
 * {@code activity_log} — a app fica igual a quem tivesse feito as duas coisas à
 * mão.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DespesasExcelImportService {

    static final String SHEET_EXPENSES = BudgetExcelExportService.SHEET_EXPENSES;
    static final String TABLE_EXPENSES = BudgetExcelExportService.TABLE_EXPENSES;
    static final String SHEET_QUARANTINE = "Por identificar";
    static final String TABLE_QUARANTINE = "TabelaPorIdentificar";

    static final String HEADER_NUMBER = "Nº Fatura";
    static final String HEADER_DATE = "Data";
    static final String HEADER_DESCRIPTION = "Produto/Serviço";
    static final String HEADER_AMOUNT = "Valor";
    static final String HEADER_PAID = "Liquidada";
    static final String HEADER_METHOD = "Metodo Pagamento";
    static final String HEADER_BIZDOCS = "Bizdocs";
    static final String HEADER_OBSERVATIONS = "Observações";
    static final String HEADER_RUBRIC = "Rubrica";
    static final String HEADER_SUPPLIER = "Fornecedor";
    static final String HEADER_NIF = "NIF";
    /** Só na folha "Por identificar": a obra a que a fatura afinal pertence (dropdown da folha "Listas"). */
    static final String HEADER_TARGET = "Empreendimento";
    static final String HEADER_POSSIBLE_ENTERPRISES = "Obras possíveis";
    static final String HEADER_ASK_WHOM = "Perguntar a";
    static final String HEADER_QUARANTINED_SINCE = "Aqui desde";
    /** O valor da dropdown que manda a fatura para as despesas da empresa em vez de uma obra. */
    static final String TARGET_COMPANY = "Despesas da empresa";

    private static final List<String> REQUIRED_HEADERS = List.of(
            HEADER_NUMBER, HEADER_DATE, HEADER_DESCRIPTION, HEADER_AMOUNT, HEADER_PAID,
            HEADER_METHOD, HEADER_BIZDOCS, HEADER_OBSERVATIONS);

    static final String QUESTION_CREDIT_NOTE_ORIGIN = "CREDIT_NOTE_ORIGIN";
    static final String QUESTION_AGGREGATE_PAYMENT = "AGGREGATE_PAYMENT";
    static final String ANSWER_SKIP = "SKIP";
    static final String ANSWER_ONE_PAYMENT = "ONE_PAYMENT";
    static final String ANSWER_SEPARATE = "SEPARATE";
    private static final String ANSWER_FILE_PREFIX = "file:";
    private static final String ANSWER_DB_PREFIX = "db:";

    /** O que o exportador escreve numa despesa sem fatura — volta a entrar como despesa solta. */
    static final String MANUAL_EXPENSE_MARK = "Despesa registada à mão na app, sem fatura.";

    private static final int MAX_HEADER_SCAN_ROWS = 20;
    /** Os cêntimos que o Excel perde a somar floats. */
    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");
    private static final String NOTE_SEPARATOR = " · ";
    private static final String TEST_FILE_PREFIX = "TESTE - ";

    private static final DateTimeFormatter NOTE_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("d/M/yyyy"), DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy"), DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("d/M/yy"), DateTimeFormatter.ofPattern("d-M-yy"));

    // As frases que o exportador gera na coluna Observações (contrato §4) — lidas
    // de volta ao pé da letra para o round-trip não perder nada.
    private static final Pattern PAID_PARTIAL = Pattern.compile(
            "^Pago parcialmente (.+?) por (.+?) em (\\S+?)(?: \\((.*)\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_AGGREGATE = Pattern.compile(
            "^Pago por (.+?) em (\\S+?), (.+?) junto com (.+?)(?: \\((.*)\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_SINGLE = Pattern.compile(
            "^Pago por (.+?) em (\\S+?)(?: \\((.*)\\))?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CREDIT_NOTE_OF = Pattern.compile(
            "^Nota de crédito da fatura (.+)$", Pattern.CASE_INSENSITIVE);
    /** O que a Vilatro escreve à mão: "Pago … em 28-08-2026" em qualquer ordem. */
    private static final Pattern PAID_LOOSE_DATE = Pattern.compile(
            "\\bpag[oa]\\b.*?\\bem\\s+(\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_REFERENCE = Pattern.compile("\\(([^()]*)\\)\\s*$");

    private final EnterpriseRepository enterpriseRepository;
    private final ConstructionInvoiceRepository invoiceRepository;
    private final ConstructionBudgetItemRepository budgetItemRepository;
    private final ConstructionExpenseRepository expenseRepository;
    private final ConstructionInvoiceService invoiceService;
    private final PaymentService paymentService;
    private final AuthContext authContext;

    @Transactional
    public ExpensesImportResultDTO importExpenses(String scopeValue, UUID enterpriseId, MultipartFile file,
                                                  boolean dryRun, ExpensesImportAnswersDTO answers) {
        Scope scope = parseScope(scopeValue);
        Enterprise enterprise = resolveEnterprise(scope, enterpriseId);
        validateFile(file);

        Model model = new Model(scope, enterprise);
        parse(file, model);
        resolveRubrics(model);
        resolveTargets(model);
        group(model);
        interpretPayments(model);
        noteQuarantinedSince(model);
        linkCreditNotes(model);
        detectDuplicates(model);
        checkTotals(model);
        applyAnswers(model, answers);
        dropPaymentsOnCreditedInvoices(model);

        if (!dryRun) {
            if (!model.errors.isEmpty()) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_HAS_ERRORS);
            }
            if (!model.questions.isEmpty()) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_UNANSWERED);
            }
            // O exportador prefixa "TESTE - " ao ficheiro de uma obra de teste; é
            // esse ficheiro, e só esse, que pode voltar a entrar numa obra de teste.
            if (enterprise != null && Boolean.TRUE.equals(enterprise.getIsTest())
                    && !Optional.ofNullable(file.getOriginalFilename()).orElse("").startsWith(TEST_FILE_PREFIX)) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_TEST_ENTERPRISE);
            }
            persist(model);
        }

        return buildResult(model, dryRun);
    }

    // ── âmbito e ficheiro ─────────────────────────────────────

    private static Scope parseScope(String value) {
        try {
            return Scope.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVOICE_SCOPE_UNKNOWN);
        }
    }

    static String sheetNameFor(Scope scope) {
        return scope == Scope.UNIDENTIFIED ? SHEET_QUARANTINE : SHEET_EXPENSES;
    }

    private static String tableNameFor(Scope scope) {
        return scope == Scope.UNIDENTIFIED ? TABLE_QUARANTINE : TABLE_EXPENSES;
    }

    private Enterprise resolveEnterprise(Scope scope, UUID enterpriseId) {
        if (scope == Scope.PROJECT) {
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

    private static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_EMPTY_FILE);
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xlsm")) {
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_INVALID_TYPE);
        }
    }

    // ── leitura da folha ──────────────────────────────────────

    private void parse(MultipartFile file, Model model) {
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            // Pelo nome, nunca "a folha que não é o orçamento": o livro tem 4 folhas
            // desde que o vault ganhou "Rubricas" e "Orçamento vs Gasto"; o da
            // quarentena tem a "Por identificar" e uma "Listas" oculta.
            String sheetName = sheetNameFor(model.scope);
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_NO_SHEET,
                        "O Excel não tem a folha \"" + sheetName + "\".");
            }
            model.sheetName = sheet.getSheetName();

            int headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_NO_HEADER);
            }
            Map<String, Integer> columns = mapColumns(sheet.getRow(headerRow));
            List<String> missing = REQUIRED_HEADERS.stream()
                    .filter(header -> !columns.containsKey(normalizeHeader(header)))
                    .toList();
            if (!missing.isEmpty()) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_MISSING_COLUMNS,
                        "Faltam as colunas: " + String.join(", ", missing));
            }
            model.hasRubricColumn = columns.containsKey(normalizeHeader(HEADER_RUBRIC));

            int totalsRow = findTotalsRow(sheet, tableNameFor(model.scope), headerRow, columns);
            readRows(sheet, headerRow, totalsRow, columns, model);
            if (totalsRow >= 0) {
                model.sheetTotal = readSheetTotal(workbook, sheet.getRow(totalsRow), columns, model);
            } else {
                model.warnings.add("A folha não tem linha de totais — os totais não foram conferidos.");
            }

            if (model.lines.isEmpty()) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_NO_ROWS);
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Falha a ler a folha de despesas '{}': {}", file.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_READ_ERROR);
        }
    }

    private static int findHeaderRow(Sheet sheet) {
        String wanted = normalizeHeader(HEADER_NUMBER);
        int limit = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && wanted.equals(normalizeHeader(cell.getStringCellValue()))) {
                    return r;
                }
            }
        }
        return -1;
    }

    /** Coluna por nome de cabeçalho normalizado — nunca pela letra (§3). */
    private static Map<String, Integer> mapColumns(Row header) {
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header) {
            if (cell.getCellType() != CellType.STRING) continue;
            String name = normalizeHeader(cell.getStringCellValue());
            if (!name.isEmpty()) {
                columns.putIfAbsent(name, cell.getColumnIndex());
            }
        }
        return columns;
    }

    /**
     * A linha de totais da tabela. A {@code TabelaDespesas} (ou a
     * {@code TabelaPorIdentificar}) sabe onde acaba; sem tabela, é a primeira
     * linha cujo "Nº Fatura" diz TOTAL.
     */
    private static int findTotalsRow(Sheet sheet, String tableName, int headerRow, Map<String, Integer> columns) {
        if (sheet instanceof XSSFSheet xssf) {
            for (XSSFTable table : xssf.getTables()) {
                if (tableName.equalsIgnoreCase(table.getName()) && table.getTotalsRowCount() > 0) {
                    AreaReference area = table.getArea();
                    return area.getLastCell().getRow();
                }
            }
        }
        int numberCol = columns.get(normalizeHeader(HEADER_NUMBER));
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row != null && "TOTAL".equalsIgnoreCase(Optional.ofNullable(text(row, numberCol)).orElse(""))) {
                return r;
            }
        }
        return -1;
    }

    private void readRows(Sheet sheet, int headerRow, int totalsRow, Map<String, Integer> columns, Model model) {
        int last = totalsRow >= 0 ? totalsRow - 1 : sheet.getLastRowNum();
        for (int r = headerRow + 1; r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int excelRow = r + 1;

            Line line = new Line();
            line.excelRow = excelRow;
            line.rawNumber = text(row, col(columns, HEADER_NUMBER));
            line.date = date(row, col(columns, HEADER_DATE), model, excelRow);
            line.description = text(row, col(columns, HEADER_DESCRIPTION));
            line.amount = money(row, col(columns, HEADER_AMOUNT), model, excelRow);
            line.paid = isYes(text(row, col(columns, HEADER_PAID)));
            line.methodRaw = text(row, col(columns, HEADER_METHOD));
            line.bizdocs = isYes(text(row, col(columns, HEADER_BIZDOCS)));
            line.observations = text(row, col(columns, HEADER_OBSERVATIONS));
            line.rubricRaw = model.hasRubricColumn ? text(row, col(columns, HEADER_RUBRIC)) : null;
            line.supplierName = text(row, col(columns, HEADER_SUPPLIER));
            line.supplierNif = text(row, col(columns, HEADER_NIF));
            if (model.scope == Scope.UNIDENTIFIED) {
                line.targetRaw = text(row, col(columns, HEADER_TARGET));
                line.possibleEnterprises = text(row, col(columns, HEADER_POSSIBLE_ENTERPRISES));
                line.askWhom = text(row, col(columns, HEADER_ASK_WHOM));
                line.quarantinedSince = date(row, col(columns, HEADER_QUARANTINED_SINCE), model, excelRow);
            }

            boolean empty = line.rawNumber == null && line.date == null && line.description == null
                    && line.amount == null && line.observations == null && line.rubricRaw == null;
            if (empty) {
                continue;
            }
            classifyNumber(line);
            if (line.amount == null) {
                // Sem nº (nem "Imprimir"/"Pedir"), sem data e sem valor é uma nota
                // ("preencher após o reembolso"), não uma despesa: salta-se com aviso.
                // Com nº ou data, é uma fatura ainda sem total — entra por rever, como
                // as que se registam sem QR.
                if (line.rawNumber == null && line.date == null) {
                    model.warnings.add("Linha " + excelRow + ": ignorada — sem nº, sem data e sem valor"
                            + (line.description != null ? " (\"" + line.description + "\")" : "") + ".");
                    continue;
                }
                model.warnings.add("Linha " + excelRow + ": sem valor — a fatura entra sem total, por rever.");
            }
            model.lines.add(line);
        }
    }

    /**
     * O nº de fatura, ou o que o vault escreve nessa célula para dizer o que
     * falta: "Imprimir fatura" / "IMPRIMIR" → por imprimir, "Pedir fatura" → por
     * pedir, vazio → em falta.
     */
    private static void classifyNumber(Line line) {
        String raw = line.rawNumber == null ? "" : stripAccents(line.rawNumber).toLowerCase(Locale.ROOT);
        // "-" é como a Vilatro marca "não tem nº" (Vila Petrus, linhas do Manitou da Civica)
        if (raw.isEmpty() || raw.matches("[-–—?]+")) {
            line.status = DocumentStatus.MISSING;
        } else if (raw.startsWith("imprimir")) {
            line.status = DocumentStatus.TO_PRINT;
        } else if (raw.startsWith("pedir")) {
            line.status = DocumentStatus.TO_REQUEST;
        } else {
            line.status = DocumentStatus.MISSING;
            line.number = line.rawNumber.trim();
        }
    }

    private BigDecimal readSheetTotal(Workbook workbook, Row totals, Map<String, Integer> columns, Model model) {
        if (totals == null) return null;
        Cell cell = totals.getCell(col(columns, HEADER_AMOUNT));
        if (cell == null) return null;
        if (cell.getCellType() != CellType.FORMULA) {
            return money(totals, cell.getColumnIndex(), model, totals.getRowNum() + 1);
        }
        // A linha TOTAL é um SUBTOTAL sobre a tabela. Um ficheiro escrito pelo POI
        // não traz o valor em cache — avalia-se; se o POI não souber (referência
        // estruturada), vale o que o Excel deixou em cache.
        try {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            return BigDecimal.valueOf(evaluator.evaluate(cell).getNumberValue()).setScale(2, RoundingMode.HALF_UP);
        } catch (RuntimeException e) {
            if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
            }
            model.warnings.add("Não foi possível ler o valor da linha de totais (" + e.getMessage()
                    + ") — os totais não foram conferidos.");
            return null;
        }
    }

    // ── rubricas ──────────────────────────────────────────────

    /**
     * "8.2 — Betão" → a rubrica "8.2" desta obra. Uma rubrica que não existe é
     * erro, nunca se cria (§6); um capítulo/sub-título/nota não recebe despesas.
     */
    private void resolveRubrics(Model model) {
        Map<String, Optional<ConstructionBudgetItem>> cache = new HashMap<>();
        for (Line line : model.lines) {
            if (line.rubricRaw == null) continue;
            line.rubricCode = normalizeRubricCode(line.rubricRaw);
            if (model.scope != Scope.PROJECT) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow, "Tem rubrica \"" + line.rubricRaw
                        + "\" mas " + (model.scope == Scope.COMPANY ? "as despesas da empresa não têm" : "a quarentena não tem")
                        + " orçamento."));
                continue;
            }
            if (line.rubricCode == null) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow,
                        "Rubrica \"" + line.rubricRaw + "\" sem índice reconhecível (esperado \"<Art> — <Descrição>\")."));
                continue;
            }
            Optional<ConstructionBudgetItem> item = cache.computeIfAbsent(line.rubricCode,
                    code -> budgetItemRepository.findByEnterpriseIdAndCode(model.enterprise.getId(), code));
            if (item.isEmpty()) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow,
                        "A rubrica \"" + line.rubricCode + "\" não existe no orçamento desta obra."));
            } else if (item.get().getRowKind() != BudgetRowKind.ITEM) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow,
                        "A rubrica \"" + line.rubricCode + "\" é um título — as despesas lançam-se numa rubrica."));
            } else {
                line.rubric = item.get();
            }
        }
    }

    /** "8.2 — Betão" → "8.2" · "8.2." → "8.2" · "Betão" → null. */
    static String normalizeRubricCode(String raw) {
        if (raw == null) return null;
        String code = raw.trim();
        int cut = code.indexOf(' ');
        if (cut > 0) code = code.substring(0, cut);
        cut = code.indexOf('—');
        if (cut > 0) code = code.substring(0, cut);
        while (code.endsWith(".")) code = code.substring(0, code.length() - 1);
        return code.matches("\\d+(\\.\\d+)*") ? code : null;
    }

    // ── destino da quarentena (§6) ────────────────────────────

    /**
     * "Empreendimento" é o valor da dropdown: o slug de uma obra (= nome da
     * pasta do vault) ou "Despesas da empresa". Sem obra com esse slug a linha
     * bloqueia — a obra cria-se primeiro, com o nome da pasta, como nas outras
     * importações; nunca se cria uma obra a partir de uma célula.
     */
    private void resolveTargets(Model model) {
        if (model.scope != Scope.UNIDENTIFIED) return;
        Map<String, Optional<Enterprise>> cache = new HashMap<>();
        for (Line line : model.lines) {
            String target = trimToNull(line.targetRaw);
            if (target == null) continue;
            if (normalizeText(target).equals(normalizeText(TARGET_COMPANY))) {
                line.targetScope = Scope.COMPANY;
                continue;
            }
            Optional<Enterprise> enterprise = cache.computeIfAbsent(target, enterpriseRepository::findBySlug);
            if (enterprise.isEmpty()) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow, "Não há na app nenhuma obra com o nome de pasta \""
                        + target + "\" — crie-a primeiro, ou limpe a coluna \"" + HEADER_TARGET + "\" para a fatura ficar em quarentena."));
            } else if (Boolean.TRUE.equals(enterprise.get().getIsTest())) {
                model.errors.add(new ExpensesImportIssueDTO(line.excelRow,
                        "A obra \"" + target + "\" é de teste — uma fatura real não se transfere para lá."));
            } else {
                line.targetScope = Scope.PROJECT;
                line.targetEnterprise = enterprise.get();
            }
        }
    }

    // ── agrupar por fatura ────────────────────────────────────

    /**
     * Uma fatura por nº. As linhas sem nº só se juntam quando são contíguas e
     * iguais em tudo menos na rubrica e no valor — é o que o exportador escreve
     * para uma fatura repartida ainda sem nº; duas linhas soltas "Imprimir
     * fatura" com descrições diferentes são duas faturas.
     */
    private void group(Model model) {
        Map<String, Group> byNumber = new LinkedHashMap<>();
        Group previousUnnumbered = null;

        for (Line line : model.lines) {
            if (MANUAL_EXPENSE_MARK.equalsIgnoreCase(Optional.ofNullable(line.observations).orElse(""))) {
                Group manual = newGroup(model, line);
                manual.manualExpense = true;
                previousUnnumbered = null;
                continue;
            }
            if (line.number != null) {
                String key = normalizeNumber(line.number);
                Group group = byNumber.get(key);
                if (group == null) {
                    group = newGroup(model, line);
                    byNumber.put(key, group);
                } else {
                    group.lines.add(line);
                }
                previousUnnumbered = null;
                continue;
            }
            if (previousUnnumbered != null && sameInvoice(previousUnnumbered, line)) {
                previousUnnumbered.lines.add(line);
            } else {
                previousUnnumbered = newGroup(model, line);
            }
        }

        for (Group group : model.groups) {
            consolidate(group, model);
        }
    }

    private static Group newGroup(Model model, Line first) {
        Group group = new Group();
        group.key = "r" + first.excelRow;
        group.lines.add(first);
        model.groups.add(group);
        return group;
    }

    private static boolean sameInvoice(Group group, Line line) {
        Line first = group.lines.get(0);
        return first.status == line.status
                && Objects.equals(first.date, line.date)
                && Objects.equals(first.description, line.description)
                && Objects.equals(first.observations, line.observations)
                && first.paid == line.paid
                && (first.amount == null || line.amount == null || first.amount.signum() == line.amount.signum());
    }

    /** Os campos da fatura vêm da primeira linha; as outras têm de concordar. */
    private static void consolidate(Group group, Model model) {
        Line first = group.lines.get(0);
        group.number = first.number;
        group.status = first.status;
        group.date = first.date;
        group.description = first.description;
        group.paid = first.paid;
        group.bizdocs = first.bizdocs;
        group.methodRaw = first.methodRaw;
        group.observations = first.observations;
        group.supplierName = first.supplierName;
        group.supplierNif = first.supplierNif;
        group.targetRaw = trimToNull(first.targetRaw);
        group.targetScope = first.targetScope;
        group.targetEnterprise = first.targetEnterprise;
        group.possibleEnterprises = first.possibleEnterprises;
        group.askWhom = first.askWhom;
        group.quarantinedSince = first.quarantinedSince;

        BigDecimal total = BigDecimal.ZERO;
        boolean anyAmount = false;
        boolean positive = false;
        boolean negative = false;
        for (Line line : group.lines) {
            if (line.amount == null) continue;
            anyAmount = true;
            total = total.add(line.amount);
            if (line.amount.signum() > 0) positive = true;
            if (line.amount.signum() < 0) negative = true;

            if (line != first && line.number != null) {
                if (!Objects.equals(line.date, first.date)) {
                    model.warnings.add("Linha " + line.excelRow + ": a fatura " + group.number
                            + " tem datas diferentes nas suas linhas — fica a da linha " + first.excelRow + ".");
                }
                if (line.paid != first.paid) {
                    model.errors.add(new ExpensesImportIssueDTO(line.excelRow, "A fatura " + group.number
                            + " está \"Liquidada\" numa linha e não noutra (linha " + first.excelRow + ")."));
                }
                if (!Objects.equals(trimToNull(line.targetRaw), group.targetRaw)) {
                    model.errors.add(new ExpensesImportIssueDTO(line.excelRow, "A fatura " + group.number
                            + " tem \"" + HEADER_TARGET + "\" diferente nas suas linhas (linha " + first.excelRow + ")."));
                }
            }
        }
        if (positive && negative) {
            model.errors.add(new ExpensesImportIssueDTO(first.excelRow, "A fatura "
                    + describe(group) + " mistura valores positivos e negativos nas suas linhas."));
        }
        group.creditNote = negative && !positive && !group.manualExpense;
        // sem nenhum valor a fatura entra sem total (por rever), não a zero
        group.total = !anyAmount ? null : group.creditNote ? total.negate() : total;

        boolean anyRubric = group.lines.stream().anyMatch(line -> line.rubricRaw != null);
        if (anyRubric) {
            for (Line line : group.lines) {
                if (line.rubricRaw == null) {
                    model.errors.add(new ExpensesImportIssueDTO(line.excelRow, "A fatura " + describe(group)
                            + " está repartida por rubricas mas esta linha não tem rubrica."));
                }
            }
        }
        if (group.manualExpense && (model.scope != Scope.PROJECT || first.rubricRaw == null)) {
            model.errors.add(new ExpensesImportIssueDTO(first.excelRow,
                    "Uma despesa sem fatura precisa de rubrica para entrar na app."));
        }
        if (group.manualExpense && first.date == null) {
            model.errors.add(new ExpensesImportIssueDTO(first.excelRow, "Uma despesa sem fatura precisa de data."));
        }
    }

    // ── pagamentos (§4) ───────────────────────────────────────

    private void interpretPayments(Model model) {
        Map<String, List<Group>> byObservation = new LinkedHashMap<>();

        for (Group group : model.groups) {
            if (group.manualExpense || group.creditNote) {
                consumeObservations(group, model);
                continue;
            }
            consumeObservations(group, model);

            if (group.payment == null && group.paid) {
                group.payment = new PaymentInfo();
                group.payment.status = PaymentStatus.PAID;
            }
            if (group.payment == null) {
                if (group.methodRaw != null) {
                    model.warnings.add("Linha " + group.lines.get(0).excelRow + ": a fatura " + describe(group)
                            + " tem método de pagamento mas não está \"Liquidada\" — fica por pagar.");
                }
                continue;
            }
            PaymentInfo payment = group.payment;
            if (!group.paid && payment.status == PaymentStatus.PAID) {
                // as observações dizem "Pago por…" mas a coluna Liquidada está vazia: a coluna manda
                model.warnings.add("Linha " + group.lines.get(0).excelRow + ": a fatura " + describe(group)
                        + " diz \"Pago\" nas observações mas não está \"Liquidada\" — fica por pagar.");
                group.payment = null;
                continue;
            }

            PaymentMethod columnMethod = parseMethod(group.methodRaw);
            if (columnMethod != null) {
                payment.method = columnMethod;
                if (columnMethod == PaymentMethod.OUTRO) {
                    payment.notes = group.methodRaw;
                    model.warnings.add("Linha " + group.lines.get(0).excelRow + ": método de pagamento \""
                            + group.methodRaw + "\" desconhecido — fica como \"Outro\", com o texto nas notas do pagamento.");
                }
            } else if (payment.method == null) {
                payment.method = PaymentMethod.OUTRO;
                model.warnings.add("Linha " + group.lines.get(0).excelRow + ": a fatura " + describe(group)
                        + " está liquidada sem método de pagamento — fica como \"Outro\".");
            }

            if (payment.paidOn == null) {
                if (group.date != null) {
                    payment.paidOn = group.date;
                    model.warnings.add("Linha " + group.lines.get(0).excelRow + ": a fatura " + describe(group)
                            + " não diz quando foi paga — fica a data da fatura (" + NOTE_DATE.format(group.date) + ").");
                } else {
                    model.errors.add(new ExpensesImportIssueDTO(group.lines.get(0).excelRow, "A fatura "
                            + describe(group) + " está liquidada mas não tem data nem \"Pago … em dd-mm-aaaa\" nas observações."));
                }
            }

            if (payment.alsoCovers != null) {
                aggregateFromExport(group, model);
            } else if (payment.status == PaymentStatus.PAID && group.rawObservationKey != null) {
                byObservation.computeIfAbsent(group.rawObservationKey, k -> new ArrayList<>()).add(group);
            }
        }

        // Observação igual em várias faturas pagas: pode ser um só movimento (§4) — pergunta-se.
        for (List<Group> candidates : byObservation.values()) {
            if (candidates.size() < 2) continue;
            Group first = candidates.get(0);
            String id = "agg:" + first.lines.get(0).excelRow;
            List<Integer> rows = candidates.stream().map(g -> g.lines.get(0).excelRow).toList();
            String numbers = candidates.stream().map(DespesasExcelImportService::describe)
                    .collect(Collectors.joining(", "));
            model.questions.add(new ExpensesImportQuestionDTO(id, QUESTION_AGGREGATE_PAYMENT,
                    "As faturas " + numbers + " têm a mesma observação (\"" + first.observations
                            + "\"). Foram pagas num só movimento?",
                    rows, List.of(
                            new ExpensesImportQuestionDTO.Option(ANSWER_ONE_PAYMENT, "Sim — um só pagamento para todas"),
                            new ExpensesImportQuestionDTO.Option(ANSWER_SEPARATE, "Não — um pagamento por fatura"))));
            model.pendingAggregates.put(id, candidates);
        }
    }

    /**
     * Lê o que o exportador (ou a Vilatro) escreveu em Observações. As frases
     * geradas pelo §4 são consumidas; o resto fica como notas da fatura.
     */
    private static void consumeObservations(Group group, Model model) {
        if (group.observations == null) return;
        List<String> leftover = new ArrayList<>();
        for (String part : group.observations.split(Pattern.quote(NOTE_SEPARATOR))) {
            String text = part.trim();
            if (text.isEmpty()) continue;

            Matcher m;
            if ((m = PAID_PARTIAL.matcher(text)).matches()) {
                PaymentInfo payment = paymentOf(group);
                payment.status = PaymentStatus.PARTIAL;
                payment.amount = parseMoney(m.group(1));
                payment.method = parseMethod(m.group(2));
                payment.paidOn = parseDate(m.group(3));
                payment.reference = m.group(4);
            } else if ((m = PAID_AGGREGATE.matcher(text)).matches()) {
                PaymentInfo payment = paymentOf(group);
                payment.status = PaymentStatus.PAID;
                payment.method = parseMethod(m.group(1));
                payment.paidOn = parseDate(m.group(2));
                payment.amount = parseMoney(m.group(3));
                payment.alsoCovers = List.of(m.group(4).split(",\\s*"));
                payment.reference = m.group(5);
            } else if ((m = PAID_SINGLE.matcher(text)).matches()) {
                PaymentInfo payment = paymentOf(group);
                payment.status = PaymentStatus.PAID;
                payment.method = parseMethod(m.group(1));
                payment.paidOn = parseDate(m.group(2));
                payment.reference = m.group(3);
            } else if ((m = CREDIT_NOTE_OF.matcher(text)).matches()) {
                group.originNumber = m.group(1).trim();
            } else if (MANUAL_EXPENSE_MARK.equalsIgnoreCase(text)) {
                // já tratado ao agrupar
            } else {
                // texto da Vilatro: tira-se a data de pagamento se lá estiver, mas guarda-se o texto
                // todo. Só um texto com data de pagamento pode ser a marca de um movimento
                // agregado — "Imprimir fatura" repetido em duas linhas não é.
                if ((m = PAID_LOOSE_DATE.matcher(text)).find()) {
                    PaymentInfo payment = paymentOf(group);
                    payment.status = PaymentStatus.PAID;
                    payment.paidOn = parseDate(m.group(1));
                    Matcher ref = TRAILING_REFERENCE.matcher(text);
                    if (ref.find()) payment.reference = ref.group(1).trim();
                    group.rawObservationKey = normalizeText(text);
                }
                leftover.add(text);
            }
        }
        group.notes = leftover.isEmpty() ? null : String.join(NOTE_SEPARATOR, leftover);
    }

    private static PaymentInfo paymentOf(Group group) {
        if (group.payment == null) group.payment = new PaymentInfo();
        return group.payment;
    }

    /**
     * "Pago por … em …, 1 234,56 € junto com FT 12, FT 13": o movimento cobre
     * esta fatura e as listadas. Cada membro repete a frase (sem se listar a si
     * próprio), por isso o agregado identifica-se pelo conjunto de números.
     */
    private static void aggregateFromExport(Group group, Model model) {
        Set<String> members = new HashSet<>();
        members.add(normalizeNumber(describe(group)));
        group.payment.alsoCovers.forEach(number -> members.add(normalizeNumber(number)));
        String id = members.stream().sorted().collect(Collectors.joining("|"));
        group.aggregateId = id;
        model.aggregates.computeIfAbsent(id, k -> new Aggregate()).members.add(group);
        model.aggregates.get(id).amount = group.payment.amount;
    }

    /**
     * "Aqui desde" não tem coluna na app ({@code created_at} é a data da
     * importação) — fica nas notas, depois do que a Vilatro escreveu, para não
     * se perder quanto tempo a fatura já esteve à espera.
     */
    private static void noteQuarantinedSince(Model model) {
        for (Group group : model.groups) {
            if (group.quarantinedSince == null) continue;
            String since = "Em quarentena desde " + NOTE_DATE.format(group.quarantinedSince) + ".";
            group.notes = group.notes == null ? since : group.notes + NOTE_SEPARATOR + since;
        }
    }

    // ── notas de crédito (§3.2) ───────────────────────────────

    private void linkCreditNotes(Model model) {
        List<Group> creditNotes = model.groups.stream().filter(g -> g.creditNote).toList();
        if (creditNotes.isEmpty()) return;

        Map<String, Group> invoicesByNumber = new HashMap<>();
        for (Group group : model.groups) {
            if (!group.creditNote && !group.manualExpense && group.number != null) {
                invoicesByNumber.put(normalizeNumber(group.number), group);
            }
        }
        List<ConstructionInvoice> existing = loadExistingInvoices(model);
        Map<String, ConstructionInvoice> existingByNumber = new HashMap<>();
        for (ConstructionInvoice invoice : existing) {
            if (invoice.getInvoiceNumber() != null
                    && invoice.getDocumentType() == ConstructionInvoice.DocumentType.INVOICE) {
                existingByNumber.putIfAbsent(normalizeNumber(invoice.getInvoiceNumber()), invoice);
            }
        }

        for (Group creditNote : creditNotes) {
            int row = creditNote.lines.get(0).excelRow;
            if (creditNote.originNumber != null) {
                String key = normalizeNumber(creditNote.originNumber);
                if (invoicesByNumber.containsKey(key)) {
                    creditNote.originKey = invoicesByNumber.get(key).key;
                    continue;
                }
                if (existingByNumber.containsKey(key)) {
                    creditNote.originInvoiceId = existingByNumber.get(key).getId();
                    continue;
                }
                model.warnings.add("Linha " + row + ": a nota de crédito diz ser da fatura \"" + creditNote.originNumber
                        + "\", que não está nem no ficheiro nem na app — é preciso escolher.");
            }

            List<ExpensesImportQuestionDTO.Option> options = new ArrayList<>();
            model.groups.stream()
                    .filter(g -> !g.creditNote && !g.manualExpense)
                    .sorted((a, b) -> Boolean.compare(
                            !Objects.equals(a.description, creditNote.description),
                            !Objects.equals(b.description, creditNote.description)))
                    .forEach(g -> options.add(new ExpensesImportQuestionDTO.Option(
                            ANSWER_FILE_PREFIX + g.key, optionLabel(g) + " (neste ficheiro)")));
            existing.stream()
                    .filter(i -> i.getDocumentType() == ConstructionInvoice.DocumentType.INVOICE)
                    .forEach(i -> options.add(new ExpensesImportQuestionDTO.Option(
                            ANSWER_DB_PREFIX + i.getId(), optionLabel(i) + " (já na app)")));
            options.add(new ExpensesImportQuestionDTO.Option(ANSWER_SKIP, "Não importar esta nota de crédito"));

            model.questions.add(new ExpensesImportQuestionDTO("nc:" + row, QUESTION_CREDIT_NOTE_ORIGIN,
                    "A linha " + row + " é uma nota de crédito de " + money(creditNote.total)
                            + (creditNote.description != null ? " (\"" + creditNote.description + "\")" : "")
                            + ". A que fatura pertence?",
                    List.of(row), options));
            model.pendingCreditNotes.put("nc:" + row, creditNote);
        }
    }

    private List<ConstructionInvoice> loadExistingInvoices(Model model) {
        return model.scope == Scope.PROJECT
                ? invoiceRepository.findAllByEnterpriseIdForExport(model.enterprise.getId())
                : invoiceRepository.findAllByScopeWithoutEnterprise(model.scope);
    }

    // ── duplicados (decisão 18) e totais (passo 9) ────────────

    private void detectDuplicates(Model model) {
        Set<String> known = invoiceRepository.findAllInvoiceNumbers().stream()
                .map(DespesasExcelImportService::normalizeNumber)
                .collect(Collectors.toSet());
        for (Group group : model.groups) {
            if (group.number == null || known.isEmpty()) continue;
            if (known.contains(normalizeNumber(group.number))) {
                group.duplicate = true;
                model.errors.add(new ExpensesImportIssueDTO(group.lines.get(0).excelRow,
                        "Já existe na app uma fatura com o número \"" + group.number + "\"."));
            }
        }
    }

    private static void checkTotals(Model model) {
        BigDecimal parsed = BigDecimal.ZERO;
        for (Line line : model.lines) {
            if (line.amount != null) parsed = parsed.add(line.amount);
        }
        model.parsedTotal = parsed;
        if (model.sheetTotal == null) return;
        model.totalDifference = parsed.subtract(model.sheetTotal);
        if (model.totalDifference.abs().compareTo(TOTAL_TOLERANCE) > 0) {
            model.errors.add(new ExpensesImportIssueDTO(0, "A soma das linhas (" + money(parsed)
                    + ") não bate certo com a linha de totais da folha (" + money(model.sheetTotal)
                    + "): diferença de " + money(model.totalDifference) + "."));
        }
    }

    // ── respostas ─────────────────────────────────────────────

    private void applyAnswers(Model model, ExpensesImportAnswersDTO answers) {
        if (answers == null || answers.answers() == null) return;
        Map<String, String> byId = new HashMap<>();
        for (ExpensesImportAnswersDTO.Answer answer : answers.answers()) {
            byId.put(answer.questionId(), answer.value());
        }

        for (Map.Entry<String, Group> entry : model.pendingCreditNotes.entrySet()) {
            String value = byId.get(entry.getKey());
            if (value == null) continue;
            Group creditNote = entry.getValue();
            if (ANSWER_SKIP.equals(value)) {
                creditNote.skipped = true;
                model.warnings.add("Linha " + creditNote.lines.get(0).excelRow
                        + ": nota de crédito deixada de fora por decisão de quem importou.");
            } else if (value.startsWith(ANSWER_FILE_PREFIX)) {
                String key = value.substring(ANSWER_FILE_PREFIX.length());
                boolean exists = model.groups.stream().anyMatch(g -> g.key.equals(key) && !g.creditNote && !g.manualExpense);
                if (!exists) continue;
                creditNote.originKey = key;
            } else if (value.startsWith(ANSWER_DB_PREFIX)) {
                try {
                    creditNote.originInvoiceId = UUID.fromString(value.substring(ANSWER_DB_PREFIX.length()));
                } catch (IllegalArgumentException e) {
                    continue;
                }
            } else {
                continue;
            }
            model.questions.removeIf(q -> q.id().equals(entry.getKey()));
        }

        for (Map.Entry<String, List<Group>> entry : model.pendingAggregates.entrySet()) {
            String value = byId.get(entry.getKey());
            if (ANSWER_ONE_PAYMENT.equals(value)) {
                Aggregate aggregate = new Aggregate();
                aggregate.members.addAll(entry.getValue());
                entry.getValue().forEach(g -> g.aggregateId = entry.getKey());
                model.aggregates.put(entry.getKey(), aggregate);
            } else if (!ANSWER_SEPARATE.equals(value)) {
                continue;
            }
            model.questions.removeIf(q -> q.id().equals(entry.getKey()));
        }
    }

    // ── faturas anuladas por nota de crédito ──────────────────

    /**
     * Uma fatura que uma NC deste ficheiro anula por inteiro fica com líquido
     * zero — e a app recusa pagá-la ({@code INVOICE_019}). No Excel ela está
     * "Liquidada" na mesma (foi o lote que a pagou, e a NC abateu-se no lote).
     * O pagamento cai, com aviso; a fatura entra como anulada, não como paga.
     * Só depois das respostas: é aí que se sabe a origem de cada NC.
     */
    private static void dropPaymentsOnCreditedInvoices(Model model) {
        Map<String, BigDecimal> creditedByKey = new HashMap<>();
        for (Group group : model.groups) {
            if (group.creditNote && !group.skipped && group.originKey != null && group.total != null) {
                creditedByKey.merge(group.originKey, group.total, BigDecimal::add);
            }
        }
        for (Group group : model.groups) {
            if (group.payment == null || group.total == null) continue;
            BigDecimal credited = creditedByKey.getOrDefault(group.key, BigDecimal.ZERO);
            if (group.total.subtract(credited).signum() > 0) continue;
            group.payment = null;
            if (group.aggregateId != null) {
                Aggregate aggregate = model.aggregates.get(group.aggregateId);
                if (aggregate != null) {
                    aggregate.members.remove(group);
                    if (aggregate.members.isEmpty()) model.aggregates.remove(group.aggregateId);
                }
                group.aggregateId = null;
            }
            model.warnings.add("Linha " + group.lines.get(0).excelRow + ": a fatura " + describe(group)
                    + " está anulada por inteiro por nota de crédito — entra sem pagamento, embora esteja \"Liquidada\".");
        }
    }

    // ── gravação ──────────────────────────────────────────────

    private void persist(Model model) {
        Map<String, UUID> idByKey = new HashMap<>();
        int created = 0;

        for (Group group : model.groups) {
            if (group.creditNote || group.manualExpense) continue;
            UUID id = invoiceService.register(registerDTO(group, model)).id();
            idByKey.put(group.key, id);
            created++;

            if (group.targetScope != null) {
                // Antes das NC e dos pagamentos: a NC copia o âmbito da origem, e o
                // transfer() apaga repartições — que uma fatura em quarentena não tem.
                invoiceService.transfer(id, new InvoiceTransferDTO(group.targetScope.name(),
                        group.targetEnterprise == null ? null : group.targetEnterprise.getId(),
                        "Importação da folha \"" + SHEET_QUARANTINE + "\": a coluna \"" + HEADER_TARGET
                                + "\" diz \"" + group.targetRaw + "\"."));
            }
            if (group.bizdocs) {
                markSentToAccountant(id);
            }
            List<InvoiceSplitLineDTO> lines = splitLines(group);
            if (!lines.isEmpty()) {
                invoiceService.split(id, lines);
            }
        }

        for (Group group : model.groups) {
            if (!group.manualExpense) continue;
            Line line = group.lines.get(0);
            ConstructionExpense expense = new ConstructionExpense();
            expense.setBudgetItem(line.rubric);
            expense.setName(line.description);
            expense.setExpenseDate(line.date);
            expense.setTotalPrice(line.amount);
            expense.setObservations(group.notes);
            authContext.currentProfileId().ifPresent(expense::setCreatedBy);
            expenseRepository.save(expense);
        }

        // As NC antes dos pagamentos: o que falta pagar é o líquido.
        for (Group group : model.groups) {
            if (!group.creditNote || group.skipped) continue;
            UUID originId = group.originKey != null ? idByKey.get(group.originKey) : group.originInvoiceId;
            if (originId == null) {
                throw new BusinessException(ErrorCode.INVOICE_IMPORT_UNANSWERED);
            }
            List<CreditNoteExpenseLineDTO> expenses = group.lines.stream()
                    .filter(line -> line.rubric != null && line.amount != null)
                    .map(line -> new CreditNoteExpenseLineDTO(line.rubric.getId(), line.amount))
                    .toList();
            UUID id = invoiceService.createCreditNote(originId, new CreditNoteCreateDTO(
                    group.total, group.number, null, group.date, trimToNull(group.supplierNif),
                    group.description, group.notes, group.status.name(), expenses)).id();
            if (group.bizdocs) {
                markSentToAccountant(id);
            }
        }

        for (Aggregate aggregate : model.aggregates.values()) {
            List<UUID> ids = aggregate.members.stream().map(g -> idByKey.get(g.key)).filter(Objects::nonNull).toList();
            if (ids.size() != aggregate.members.size()) {
                // um dos membros não está neste ficheiro (já estava na app, ou noutra obra)
                Group first = aggregate.members.get(0);
                log.info("Pagamento agregado da fatura {} tem membros fora do ficheiro — registado só para as {} presentes",
                        describe(first), ids.size());
            }
            PaymentInfo payment = aggregate.members.get(0).payment;
            BigDecimal amount = aggregate.amount;
            if (amount == null || ids.size() != aggregate.members.size()) {
                amount = aggregate.members.stream().filter(g -> idByKey.containsKey(g.key))
                        .map(g -> g.total == null ? BigDecimal.ZERO : g.total).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            AggregatePaymentResultDTO result = paymentService.registerAggregate(new AggregatePaymentRequestDTO(
                    ids, payment.paidOn, payment.method.name(), amount, payment.reference, payment.notes), null);
            if (!result.created()) {
                throw new BusinessException(ErrorCode.INVOICE_PAYMENT_SUM_MISMATCH,
                        "O movimento de " + money(amount) + " não cobre as faturas " + aggregate.members.stream()
                                .map(DespesasExcelImportService::describe).collect(Collectors.joining(", ")));
            }
        }

        for (Group group : model.groups) {
            if (group.payment == null || group.aggregateId != null || group.creditNote || group.manualExpense) continue;
            PaymentInfo payment = group.payment;
            BigDecimal amount = payment.status == PaymentStatus.PARTIAL ? payment.amount : null;
            paymentService.markAsPaid(idByKey.get(group.key), new MarkPaidRequestDTO(
                    payment.paidOn, payment.method.name(), amount, payment.reference, payment.notes), null);
        }

        verifyPersisted(model, idByKey, created);
    }

    private InvoiceRegisterDTO registerDTO(Group group, Model model) {
        return new InvoiceRegisterDTO(
                model.scope.name(),
                model.enterprise == null ? null : model.enterprise.getId(),
                trimToNull(group.supplierName),
                trimToNull(group.supplierNif),
                group.number,
                null,
                group.date,
                group.total,
                group.description,
                group.status.name(),
                trimToNull(group.possibleEnterprises),
                trimToNull(group.askWhom),
                group.notes);
    }

    /** Uma linha por rubrica; a mesma rubrica duas vezes na fatura soma-se (o split não aceita repetidas). */
    private static List<InvoiceSplitLineDTO> splitLines(Group group) {
        Map<UUID, BigDecimal> byItem = new LinkedHashMap<>();
        for (Line line : group.lines) {
            if (line.rubric == null) continue;
            byItem.merge(line.rubric.getId(), line.amount == null ? BigDecimal.ZERO : line.amount, BigDecimal::add);
        }
        return byItem.entrySet().stream()
                .map(e -> new InvoiceSplitLineDTO(e.getKey(), e.getValue()))
                .toList();
    }

    private void markSentToAccountant(UUID invoiceId) {
        ConstructionInvoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
        invoice.setSentToAccountant(true);
        invoice.setSentToAccountantAt(OffsetDateTime.now());
        authContext.currentProfileId().ifPresent(invoice::setSentToAccountantBy);
        invoiceRepository.save(invoice);
    }

    /**
     * Passo 9 do §9, do lado da base de dados: o que ficou gravado tem de ser o
     * que a folha dizia — nº de faturas, soma, e quantas ficaram por liquidar.
     * Uma diferença aqui é bug nosso, não erro do Excel; anula tudo.
     */
    private void verifyPersisted(Model model, Map<String, UUID> idByKey, int created) {
        Counts counts = counts(model);
        if (created != counts.invoices) {
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_TOTALS_MISMATCH,
                    "Ficaram " + created + " faturas gravadas mas a folha tem " + counts.invoices + ".");
        }
        List<UUID> ids = new ArrayList<>(idByKey.values());
        Map<UUID, BigDecimal> paid = paymentService.paidSums(ids);
        int paidInDb = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (UUID id : ids) {
            ConstructionInvoice invoice = invoiceRepository.findById(id).orElseThrow();
            BigDecimal total = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
            sum = sum.add(total);
            BigDecimal net = total.subtract(invoiceRepository.sumCreditNotesFor(id));
            if (PaymentService.deriveStatus(net, paid.get(id)) == PaymentStatus.PAID) {
                paidInDb++;
            }
        }
        if (paidInDb != counts.paid) {
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_TOTALS_MISMATCH,
                    "Ficaram " + paidInDb + " faturas liquidadas mas a folha tem " + counts.paid + ".");
        }
        BigDecimal expected = model.groups.stream()
                .filter(g -> !g.creditNote && !g.manualExpense)
                .map(g -> g.total == null ? BigDecimal.ZERO : g.total).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(expected) != 0) {
            throw new BusinessException(ErrorCode.INVOICE_IMPORT_TOTALS_MISMATCH,
                    "A soma gravada (" + money(sum) + ") não é a da folha (" + money(expected) + ").");
        }
    }

    // ── resultado ─────────────────────────────────────────────

    private ExpensesImportResultDTO buildResult(Model model, boolean dryRun) {
        Counts counts = counts(model);
        List<ExpensesImportInvoiceDTO> invoices = model.groups.stream().map(this::toDTO).toList();
        model.errors.sort((a, b) -> Integer.compare(a.excelRow(), b.excelRow()));
        return new ExpensesImportResultDTO(
                dryRun, model.scope.name(), model.sheetName, model.lines.size(),
                counts.invoices, counts.creditNotes, counts.manual, counts.transferred,
                counts.paid, counts.partial, counts.unpaid,
                model.parsedTotal, model.sheetTotal, model.totalDifference,
                model.errors, model.warnings, model.questions, invoices);
    }

    private ExpensesImportInvoiceDTO toDTO(Group group) {
        PaymentInfo payment = group.payment;
        List<ExpensesImportInvoiceDTO.Line> lines = group.lines.stream()
                .map(line -> new ExpensesImportInvoiceDTO.Line(line.excelRow, line.rubricCode,
                        line.rubric == null ? line.rubricRaw : BudgetExcelExportService.rubricLabel(
                                line.rubric.getCode(), line.rubric.getName()),
                        line.amount))
                .toList();
        return new ExpensesImportInvoiceDTO(
                group.key,
                group.lines.stream().map(l -> l.excelRow).toList(),
                group.number,
                group.status == null ? null : group.status.name(),
                group.date,
                group.description,
                group.total,
                group.creditNote,
                group.originKey,
                group.manualExpense,
                payment == null ? PaymentStatus.UNPAID.name() : payment.status.name(),
                payment == null || payment.method == null ? null : payment.method.name(),
                payment == null ? null : payment.paidOn,
                payment == null ? null : payment.amount,
                payment == null ? null : payment.reference,
                group.bizdocs,
                group.notes,
                trimToNull(group.supplierName),
                trimToNull(group.supplierNif),
                group.duplicate,
                trimToNull(group.possibleEnterprises),
                trimToNull(group.askWhom),
                transferLabel(group),
                lines);
    }

    /** Para onde a fatura vai a seguir a entrar em quarentena, como a pessoa a conhece — nome da obra ou "Despesas da empresa". */
    private static String transferLabel(Group group) {
        if (group.targetScope == null) return null;
        return group.targetScope == Scope.COMPANY ? TARGET_COMPANY : group.targetEnterprise.getName();
    }

    private static Counts counts(Model model) {
        Counts counts = new Counts();
        for (Group group : model.groups) {
            if (group.manualExpense) {
                counts.manual++;
            } else if (group.creditNote) {
                if (!group.skipped) counts.creditNotes++;
            } else {
                counts.invoices++;
                if (group.targetScope != null) counts.transferred++;
                if (group.payment == null) {
                    counts.unpaid++;
                } else if (group.payment.status == PaymentStatus.PARTIAL) {
                    counts.partial++;
                    counts.unpaid++;
                } else {
                    counts.paid++;
                }
            }
        }
        return counts;
    }

    // ── leitura de células ────────────────────────────────────

    private static int col(Map<String, Integer> columns, String header) {
        return columns.getOrDefault(normalizeHeader(header), -1);
    }

    private static String text(Row row, int col) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        String value = switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? NOTE_DATE.format(cell.getLocalDateTimeCellValue().toLocalDate())
                    : stripTrailingZeros(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
        if (value == null) return null;
        String trimmed = value.replace(' ', ' ').trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static LocalDate date(Row row, int col, Model model, int excelRow) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String raw = text(row, col);
        if (raw == null) return null;
        LocalDate parsed = parseDate(raw);
        if (parsed == null) {
            model.errors.add(new ExpensesImportIssueDTO(excelRow, "Data \"" + raw + "\" não reconhecida (esperado dd/mm/aaaa)."));
        }
        return parsed;
    }

    private static BigDecimal money(Row row, int col, Model model, int excelRow) {
        if (col < 0) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(2, RoundingMode.HALF_UP);
        }
        String raw = text(row, col);
        if (raw == null) return null;
        BigDecimal parsed = parseMoney(raw);
        if (parsed == null) {
            model.errors.add(new ExpensesImportIssueDTO(excelRow, "Valor \"" + raw + "\" não é um número."));
        }
        return parsed;
    }

    /** Aceita "11 643,33 €", "11.643,33 €", "11643,33", "-250,00", "1234.56". */
    static BigDecimal parseMoney(String raw) {
        if (raw == null) return null;
        String s = raw.replace("€", "").replace(' ', ' ').replace(" ", "").trim();
        if (s.isEmpty()) return null;
        int comma = s.lastIndexOf(',');
        int dot = s.lastIndexOf('.');
        if (comma >= 0 && dot >= 0) {
            s = comma > dot ? s.replace(".", "").replace(',', '.') : s.replace(",", "");
        } else if (comma >= 0) {
            s = s.replace(',', '.');
        } else if (dot >= 0 && s.length() - dot - 1 == 3) {
            s = s.replace(".", ""); // "11.643" é milhar, não decimal
        }
        try {
            return new BigDecimal(s).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDate parseDate(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, format);
            } catch (DateTimeParseException ignored) {
                // tenta o formato seguinte
            }
        }
        return null;
    }

    /** Mapa do §4: "Numerário" · "Pagamento MB"/"MB"/"TPA" · "Transferência" · resto → OUTRO. Sem acentos nem caixa. */
    static PaymentMethod parseMethod(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = stripAccents(raw).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (s.startsWith("numerario") || s.equals("dinheiro")) return PaymentMethod.NUMERARIO;
        if (s.contains("mb") || s.contains("multibanco") || s.contains("tpa")) return PaymentMethod.MULTIBANCO;
        if (s.startsWith("transfer")) return PaymentMethod.TRANSFERENCIA;
        if (s.equals("outro")) return PaymentMethod.OUTRO;
        return PaymentMethod.OUTRO;
    }

    /** "x", "X", "Sim" — os três aparecem nos ficheiros reais (pedido 3 da decisão 26 por cumprir). */
    static boolean isYes(String raw) {
        if (raw == null) return false;
        String s = stripAccents(raw).toLowerCase(Locale.ROOT).trim();
        return s.equals("x") || s.equals("sim") || s.equals("s") || s.equals("true") || s.equals("1") || s.equals("yes");
    }

    static String normalizeHeader(String raw) {
        return raw == null ? "" : stripAccents(raw).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /** A mesma regra do {@code rejectIfDuplicate}: só letras e dígitos, em maiúsculas. */
    static String normalizeNumber(String value) {
        return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}]", "").toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        return stripAccents(value).replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private static String stripAccents(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private static String stripTrailingZeros(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String describe(Group group) {
        if (group.number != null) return group.number;
        return "sem nº (linha " + group.lines.get(0).excelRow + ")";
    }

    private static String optionLabel(Group group) {
        return describe(group) + (group.description != null ? " — " + shorten(group.description) : "")
                + " — " + money(group.total);
    }

    /** Só a primeira linha e até 60 caracteres — é uma opção de uma lista, não a descrição. */
    private static String shorten(String description) {
        String first = description.split("\r?\n")[0].trim();
        return first.length() > 60 ? first.substring(0, 57).trim() + "..." : first;
    }

    private static String optionLabel(ConstructionInvoice invoice) {
        String number = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "sem nº";
        return number + (invoice.getDescription() != null ? " — " + shorten(invoice.getDescription()) : "")
                + (invoice.getTotalAmount() != null ? " — " + money(invoice.getTotalAmount()) : "");
    }

    private static String money(BigDecimal value) {
        return value == null ? "?" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + " €";
    }

    // ── estruturas internas ───────────────────────────────────

    private static final class Line {
        int excelRow;
        String rawNumber;
        String number;
        DocumentStatus status;
        LocalDate date;
        String description;
        BigDecimal amount;
        boolean paid;
        String methodRaw;
        boolean bizdocs;
        String observations;
        String rubricRaw;
        String rubricCode;
        ConstructionBudgetItem rubric;
        String supplierName;
        String supplierNif;
        String targetRaw;
        Scope targetScope;
        Enterprise targetEnterprise;
        String possibleEnterprises;
        String askWhom;
        LocalDate quarantinedSince;
    }

    private static final class Group {
        String key;
        final List<Line> lines = new ArrayList<>();
        String number;
        DocumentStatus status;
        LocalDate date;
        String description;
        BigDecimal total;
        boolean paid;
        boolean bizdocs;
        String methodRaw;
        String observations;
        String rawObservationKey;
        String notes;
        String supplierName;
        String supplierNif;
        String targetRaw;
        Scope targetScope;
        Enterprise targetEnterprise;
        String possibleEnterprises;
        String askWhom;
        LocalDate quarantinedSince;
        boolean creditNote;
        String originNumber;
        String originKey;
        UUID originInvoiceId;
        boolean skipped;
        boolean manualExpense;
        boolean duplicate;
        PaymentInfo payment;
        String aggregateId;
    }

    private static final class PaymentInfo {
        PaymentStatus status = PaymentStatus.PAID;
        PaymentMethod method;
        LocalDate paidOn;
        BigDecimal amount;
        String reference;
        String notes;
        List<String> alsoCovers;
    }

    private static final class Aggregate {
        final List<Group> members = new ArrayList<>();
        BigDecimal amount;
    }

    private static final class Counts {
        int invoices, creditNotes, manual, transferred, paid, partial, unpaid;
    }

    private static final class Model {
        final Scope scope;
        final Enterprise enterprise;
        String sheetName;
        boolean hasRubricColumn;
        BigDecimal sheetTotal;
        BigDecimal parsedTotal = BigDecimal.ZERO;
        BigDecimal totalDifference;
        final List<Line> lines = new ArrayList<>();
        final List<Group> groups = new ArrayList<>();
        final List<ExpensesImportIssueDTO> errors = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<ExpensesImportQuestionDTO> questions = new ArrayList<>();
        final Map<String, Group> pendingCreditNotes = new LinkedHashMap<>();
        final Map<String, List<Group>> pendingAggregates = new LinkedHashMap<>();
        final Map<String, Aggregate> aggregates = new LinkedHashMap<>();

        Model(Scope scope, Enterprise enterprise) {
            this.scope = scope;
            this.enterprise = enterprise;
        }
    }
}
