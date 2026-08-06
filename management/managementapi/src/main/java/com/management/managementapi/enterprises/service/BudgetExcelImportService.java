package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportRowDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Importa um orçamento de obra em .xlsx para a árvore de rubricas.
 *
 * O formato é o dos orçamentos que a empresa recebe:
 * {@code Art | Descrição | Un. | Quant | Preço Un | Preço total | Obs.}
 *
 * A parte não-trivial é reconstruir a hierarquia, porque o Excel mistura três
 * coisas na mesma coluna de descrição:
 * <ol>
 *   <li>rubricas numeradas, com profundidade variável ({@code 1.} … {@code 17.1.5});</li>
 *   <li>sub-títulos sem número ("Paredes", "Pavimentos") que agrupam tudo o que
 *       vem a seguir até ao título seguinte;</li>
 *   <li>linhas sem número que ou são notas entre parêntesis, ou são
 *       "Alternativa ..." — e estas últimas trazem o preço efectivo quando a
 *       rubrica numerada acima ficou com o total vazio.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetExcelImportService {

    private static final int COL_CODE = 0;   // A "Art"
    private static final int COL_NAME = 1;   // B "Descrição"
    private static final int COL_UNIT = 2;   // C "Un."
    private static final int COL_QTY = 3;    // D "Quant"
    private static final int COL_UNIT_PRICE = 4; // E "Preço Un"
    private static final int COL_TOTAL = 5;  // F "Preço total"
    private static final int COL_OBS = 6;    // G "Obs."

    private static final int MAX_HEADER_SCAN_ROWS = 60;

    /**
     * Nome de recurso para rubricas que vêm sem descrição. Não pode começar por
     * "(" senão seria classificada como nota de contexto.
     */
    private static final String NO_DESCRIPTION = "Sem descrição";

    private final ConstructionBudgetItemRepository repository;
    private final EnterpriseRepository enterpriseRepository;
    private final AuthContext authContext;

    @Transactional
    public BudgetImportResultDTO importBudget(UUID enterpriseId, MultipartFile file,
                                              boolean dryRun, boolean replace) {

        Enterprise enterprise = enterpriseRepository.findById(enterpriseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_ENTERPRISE_NOT_FOUND));

        validateFile(file);

        if (!dryRun && !replace && repository.existsByEnterpriseId(enterpriseId)) {
            throw new BusinessException(ErrorCode.BUDGET_IMPORT_NOT_EMPTY);
        }

        ParseResult parsed = parse(file);

        if (!dryRun) {
            if (replace) {
                repository.deleteAllByEnterpriseId(enterpriseId);
                repository.flush();
            }
            UUID createdBy = authContext.currentProfileId().orElse(null);
            for (Draft root : parsed.roots) {
                persist(root, null, enterprise, createdBy);
            }
        }

        return buildResult(parsed, dryRun);
    }

    // ── leitura do ficheiro ───────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BUDGET_IMPORT_EMPTY_FILE);
        }
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            throw new BusinessException(ErrorCode.BUDGET_IMPORT_INVALID_TYPE);
        }
    }

    private ParseResult parse(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getSheetAt(0);
            ParseResult result = new ParseResult();
            result.sheetName = sheet.getSheetName();

            int headerRow = findHeaderRow(sheet);
            if (headerRow < 0) {
                throw new BusinessException(ErrorCode.BUDGET_IMPORT_NO_HEADER);
            }

            buildTree(sheet, headerRow, result);

            if (result.roots.isEmpty()) {
                throw new BusinessException(ErrorCode.BUDGET_IMPORT_NO_ROWS);
            }
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.warn("Falha a ler o orçamento '{}': {}", file.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.BUDGET_IMPORT_READ_ERROR);
        }
    }

    /** A linha de cabeçalho é a que tem "Art" na coluna A. */
    private int findHeaderRow(Sheet sheet) {
        int limit = Math.min(sheet.getLastRowNum(), MAX_HEADER_SCAN_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String a = text(row, COL_CODE);
            if (a != null && a.trim().toLowerCase().startsWith("art")) {
                return r;
            }
        }
        return -1;
    }

    private void buildTree(Sheet sheet, int headerRow, ParseResult result) {
        Map<String, Draft> byCode = new HashMap<>();
        Draft currentChapter = null;
        Draft currentHeading = null;
        Draft lastItem = null;

        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            int excelRow = r + 1; // POI é 0-based, o Excel mostra 1-based
            String rawCode = text(row, COL_CODE);
            String name = text(row, COL_NAME);

            // a linha "TOTAL" fecha a tabela — abaixo dela só há notas do orçamento
            if (isTotalRow(rawCode, name)) {
                result.excelTotal = number(row, COL_TOTAL, 2, result, excelRow, "Preço total");
                break;
            }

            BigDecimal quantity = number(row, COL_QTY, 3, result, excelRow, "Quant");
            BigDecimal unitPrice = number(row, COL_UNIT_PRICE, 2, result, excelRow, "Preço Un");
            BigDecimal totalPrice = number(row, COL_TOTAL, 2, result, excelRow, "Preço total");

            String code = normalizeCode(rawCode);

            if (isBlank(name)) {
                // Há rubricas reais sem descrição no Excel (ex. "6.1" e "17.1.1" do
                // orçamento da Villa Petrus, que juntas valem 57.570,21 €). Descartá-las
                // furava o total, por isso entram com um nome de recurso — só se
                // ignora a linha quando não tem índice nem preço, aí é mesmo lixo.
                if (code == null && totalPrice == null) {
                    if (quantity != null || unitPrice != null) {
                        result.warnings.add("Linha " + excelRow
                                + ": ignorada — sem índice, sem descrição e sem preço total.");
                    }
                    continue;
                }
                name = NO_DESCRIPTION;
                result.warnings.add("Linha " + excelRow + ": rubrica"
                        + (code != null ? " \"" + code + "\"" : "")
                        + " sem descrição no Excel — importada como \"" + NO_DESCRIPTION + "\".");
            }

            Draft draft = new Draft();
            draft.excelRow = excelRow;
            draft.name = name;
            draft.unit = normalizeUnit(text(row, COL_UNIT));
            draft.quantity = quantity;
            draft.unitPrice = unitPrice;
            draft.totalPrice = totalPrice;
            draft.observations = text(row, COL_OBS);

            if (code != null && byCode.containsKey(code)) {
                result.warnings.add("Linha " + excelRow + ": índice \"" + code + "\" repetido (já usado na linha "
                        + byCode.get(code).excelRow + ") — a rubrica é importada sem índice.");
                draft.duplicateOfCode = code;
                code = null;
            }

            Draft parent;

            if (code != null) {
                // ── rubrica numerada ──
                draft.kind = BudgetRowKind.ITEM;
                draft.code = code;
                parent = resolveCodedParent(code, byCode, currentChapter, currentHeading);
                byCode.put(code, draft);
                if (!code.contains(".")) {
                    currentHeading = null; // capítulo novo fecha o sub-título aberto
                }
                lastItem = draft;

            } else if (draft.duplicateOfCode != null) {
                // índice repetido: fica onde o irmão original ficaria
                draft.kind = BudgetRowKind.ITEM;
                parent = resolveCodedParent(draft.duplicateOfCode, byCode, currentChapter, currentHeading);
                lastItem = draft;

            } else if (name.trim().startsWith("(")) {
                // ── nota de contexto, colada à rubrica anterior ──
                draft.kind = BudgetRowKind.NOTE;
                parent = lastItem != null ? lastItem : currentChapter;

            } else if (quantity != null || unitPrice != null || totalPrice != null) {
                // ── "Alternativa ..." — sem número, mas é quem traz o preço ──
                draft.kind = BudgetRowKind.ITEM;
                parent = lastItem != null ? lastItem : currentChapter;

            } else {
                // ── sub-título: passa a agrupar tudo o que vier a seguir ──
                draft.kind = BudgetRowKind.HEADING;
                parent = currentChapter;
                currentHeading = draft;
                lastItem = draft;
            }

            attach(draft, parent, result);

            if (draft.kind == BudgetRowKind.ITEM && draft.code != null && !draft.code.contains(".")) {
                currentChapter = draft;
            }
        }
    }

    /**
     * O pai natural de "4.2.1" é "4.2". A excepção é quando há um sub-título
     * aberto no capítulo: nesse caso "9.1" pendura de "Paredes" e não de "9.".
     */
    private Draft resolveCodedParent(String code, Map<String, Draft> byCode,
                                     Draft currentChapter, Draft currentHeading) {
        int lastDot = code.lastIndexOf('.');
        if (lastDot < 0) {
            return null; // capítulo — fica na raiz
        }

        Draft natural = byCode.get(code.substring(0, lastDot));

        if (currentHeading != null && natural != null && natural == currentHeading.parent) {
            return currentHeading;
        }
        if (natural != null) {
            return natural;
        }
        // numeração com buracos (ex. "8.4.1" sem "8.4"): cai no capítulo
        return currentChapter;
    }

    private void attach(Draft draft, Draft parent, ParseResult result) {
        draft.parent = parent;
        if (parent == null) {
            draft.depth = 0;
            draft.sortOrder = result.roots.size();
            result.roots.add(draft);
        } else {
            draft.depth = parent.depth + 1;
            draft.sortOrder = parent.children.size();
            parent.children.add(draft);
        }
        result.all.add(draft);
    }

    // ── persistência ──────────────────────────────────────────

    private void persist(Draft draft, ConstructionBudgetItem parent, Enterprise enterprise, UUID createdBy) {
        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setEnterprise(enterprise);
        item.setParent(parent);
        item.setRowKind(draft.kind);
        item.setCode(draft.code);
        item.setSortOrder(draft.sortOrder);
        item.setName(draft.name);
        item.setUnit(draft.unit);
        item.setQuantity(draft.quantity);
        item.setUnitPrice(draft.unitPrice);
        item.setTotalPrice(draft.totalPrice);
        item.setObservations(draft.observations);
        item.setCreatedBy(createdBy);

        ConstructionBudgetItem saved = repository.save(item);
        for (Draft child : draft.children) {
            persist(child, saved, enterprise, createdBy);
        }
    }

    // ── resultado ─────────────────────────────────────────────

    private BudgetImportResultDTO buildResult(ParseResult parsed, boolean dryRun) {
        List<BudgetImportRowDTO> rows = new ArrayList<>(parsed.all.size());
        int items = 0;
        int headings = 0;
        int notes = 0;

        for (Draft d : parsed.all) {
            switch (d.kind) {
                case ITEM -> items++;
                case HEADING -> headings++;
                case NOTE -> notes++;
            }
            rows.add(new BudgetImportRowDTO(
                    d.excelRow, d.depth, d.kind, d.code,
                    parentLabel(d),
                    d.name, d.unit, d.quantity, d.unitPrice, d.totalPrice, d.observations));
        }

        BigDecimal parsedTotal = parsed.roots.stream()
                .map(this::leafSum)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal difference = parsed.excelTotal == null
                ? null
                : parsedTotal.subtract(parsed.excelTotal);

        // O Excel soma floats por arredondar, nós arredondamos cada célula a 2 casas:
        // uns cêntimos de diferença são normais. Só se avisa a partir de 1 €, mas
        // totalDifference leva sempre o valor exacto.
        if (difference != null && difference.abs().compareTo(BigDecimal.ONE) > 0) {
            parsed.warnings.add("A soma das rubricas (" + parsedTotal + ") não bate certo com o TOTAL do Excel ("
                    + parsed.excelTotal + "): diferença de " + difference + ".");
        }

        return new BudgetImportResultDTO(
                dryRun, parsed.sheetName, items, headings, notes,
                parsedTotal, parsed.excelTotal, difference,
                parsed.warnings, rows);
    }

    /** Sub-títulos não têm índice, por isso na pré-visualização mostra-se o nome. */
    private static String parentLabel(Draft draft) {
        if (draft.parent == null) return null;
        return draft.parent.code != null ? draft.parent.code : draft.parent.name;
    }

    /**
     * Soma só as folhas com preço — mesma regra dos rollups da árvore. Somar
     * também os capítulos duplicaria, porque o Excel guarda o total do capítulo
     * na própria linha <i>e</i> o detalhe nas rubricas por baixo.
     */
    private BigDecimal leafSum(Draft draft) {
        BigDecimal childSum = BigDecimal.ZERO;
        boolean childHasPrice = false;
        for (Draft child : draft.children) {
            BigDecimal sub = leafSum(child);
            childSum = childSum.add(sub);
            if (sub.signum() != 0 || child.totalPrice != null) {
                childHasPrice = true;
            }
        }
        if (childHasPrice) {
            return childSum;
        }
        return draft.totalPrice != null ? draft.totalPrice : BigDecimal.ZERO;
    }

    // ── leitura de células ────────────────────────────────────

    private String text(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        String value = switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> stripTrailingZeros(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> stripTrailingZeros(cell.getNumericCellValue());
                default -> null;
            };
            default -> null;
        };

        if (value == null) return null;
        // as descrições longas vêm com quebras de linha do Excel; normaliza espaços à volta
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private BigDecimal number(Row row, int col, int scale, ParseResult result, int excelRow, String colName) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();

        if (type == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).setScale(scale, RoundingMode.HALF_UP);
        }

        if (type == CellType.STRING) {
            String raw = cell.getStringCellValue().trim();
            if (raw.isEmpty()) return null;
            try {
                String cleaned = raw.replace("€", "").replace(" ", "").replace(",", ".");
                return new BigDecimal(cleaned).setScale(scale, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                result.warnings.add("Linha " + excelRow + ", coluna \"" + colName
                        + "\": tem texto (\"" + raw + "\") onde era esperado um número — valor ignorado.");
                return null;
            }
        }

        return null;
    }

    private static boolean isTotalRow(String code, String name) {
        return isBlank(code) && name != null && name.trim().equalsIgnoreCase("TOTAL");
    }

    /** "1." → "1" · "4.2.1" → "4.2.1" · qualquer coisa não numérica → null. */
    private static String normalizeCode(String raw) {
        if (isBlank(raw)) return null;
        String code = raw.trim().replaceAll("\\s+", "");
        while (code.endsWith(".")) {
            code = code.substring(0, code.length() - 1);
        }
        return code.matches("\\d+(\\.\\d+)*") ? code : null;
    }

    /** O Excel mistura "un" e "Un." na mesma coluna. */
    private static String normalizeUnit(String raw) {
        if (isBlank(raw)) return null;
        String unit = raw.trim().toLowerCase();
        while (unit.endsWith(".")) {
            unit = unit.substring(0, unit.length() - 1);
        }
        return unit.isEmpty() ? null : unit;
    }

    private static String stripTrailingZeros(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ── estruturas internas ───────────────────────────────────

    /** Nó em construção, antes de existir na base de dados. */
    private static final class Draft {
        int excelRow;
        int depth;
        int sortOrder;
        BudgetRowKind kind = BudgetRowKind.ITEM;
        String code;
        String duplicateOfCode;
        String name;
        String unit;
        BigDecimal quantity;
        BigDecimal unitPrice;
        BigDecimal totalPrice;
        String observations;
        Draft parent;
        final List<Draft> children = new ArrayList<>();
    }

    private static final class ParseResult {
        String sheetName;
        BigDecimal excelTotal;
        final List<Draft> roots = new ArrayList<>();
        final List<Draft> all = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
    }
}
