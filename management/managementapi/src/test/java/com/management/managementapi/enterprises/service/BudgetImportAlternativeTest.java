package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.dto.budget.response.BudgetImportRowDTO;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.security.AuthContext;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A regra de colocação das linhas "Alternativa ..." na árvore.
 *
 * O caso mau (linha 82 do orçamento da Villa Petrus): entre a rubrica "8.4" e a
 * sua alternativa entraram as sub-rubricas "8.4.1".."8.4.3", por isso o
 * {@code lastItem} do parser já tinha descido para "8.4.3" e a alternativa
 * pendurava-se lá dentro em vez de em "8.4". Os totais ficavam certos — só a
 * posição na árvore é que não.
 *
 * O ficheiro sintético abaixo reproduz essa fatia (e o caso que já funcionava:
 * uma alternativa logo a seguir a "8.5", sem sub-rubricas pelo meio).
 */
@ExtendWith(MockitoExtension.class)
class BudgetImportAlternativeTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private BudgetExcelImportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private BudgetImportResultDTO parsePreview() {
        when(enterpriseRepository.findById(ENTERPRISE_ID))
                .thenReturn(Optional.of(new Enterprise()));
        return service.importBudget(ENTERPRISE_ID, buildWorkbook(), true, false);
    }

    @Test
    @DisplayName("A alternativa que vem depois de sub-rubricas pendura na rubrica-mãe, não na última sub-rubrica")
    void alternativeAfterSubRubricsAttachesToGroupingRubric() {
        BudgetImportResultDTO result = parsePreview();

        BudgetImportRowDTO alternative = row(result, "Alternativa em reforço de Etics");
        assertThat(alternative.parentCode()).isEqualTo("8.4");
        assertThat(alternative.parentCode()).isNotEqualTo("8.4.3");
        // "8." (0) → "8.4" (1) → alternativa (2)
        assertThat(alternative.depth()).isEqualTo(2);
    }

    @Test
    @DisplayName("A alternativa logo a seguir à rubrica que substitui continua a pendurar nela")
    void alternativeRightAfterItsRubricStaysThere() {
        BudgetImportResultDTO result = parsePreview();

        BudgetImportRowDTO alternative = row(result, "Alternativa em Etics de 60mm");
        assertThat(alternative.parentCode()).isEqualTo("8.5");
    }

    @Test
    @DisplayName("As sub-rubricas continuam debaixo da rubrica-mãe e os totais não mudam")
    void subRubricsAndTotalsUnchanged() {
        BudgetImportResultDTO result = parsePreview();

        assertThat(row(result, "Rebordo de vãos").parentCode()).isEqualTo("8.4");
        assertThat(row(result, "Esquinas dos edifícios").parentCode()).isEqualTo("8.4");
        // soma das folhas com preço: 12471.12 (alt. de 8.4) + 22019.24 (alt. de 8.5)
        assertThat(result.parsedTotal()).isEqualByComparingTo("34490.36");
    }

    // ── ajudas ────────────────────────────────────────────────

    private static BudgetImportRowDTO row(BudgetImportResultDTO result, String namePrefix) {
        return result.rows().stream()
                .filter(r -> r.name() != null && r.name().startsWith(namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("linha não encontrada: " + namePrefix));
    }

    /**
     * Fatia do orçamento real: capítulo "8." com total próprio, a rubrica de
     * agrupamento "8.4" sem preço, três sub-rubricas sem total, e a alternativa
     * que traz o preço do conjunto; depois "8.5" (rubrica-folha sem total)
     * seguida logo da sua alternativa.
     */
    private static MockMultipartFile buildWorkbook() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Orçamento inicial");

            text(sheet, 0, "Art", "Descrição", "Un.", "Quant", "Preço Un", "Preço total", "Obs.");

            String r = "Fornecimento e execução de revestimento em ";
            code(sheet, 1, "8.", "REVESTIMENTOS EXTERIORES", null, null, null, 135519.25);
            code(sheet, 2, "8.4", r + "capeamentos", null, null, null, null);
            code(sheet, 3, "8.4.1", "Rebordo de vãos", "m", 513.34, 34.4, null);
            code(sheet, 4, "8.4.2", "Separação de pisos", "m", 68d, 34.4, null);
            code(sheet, 5, "8.4.3", "Esquinas dos edifícios", "m", 51d, 34.4, null);
            code(sheet, 6, null, "Alternativa em reforço de Etics, mais salientes",
                    "m", 634.34, 19.66, 12471.12);
            code(sheet, 7, "8.5", r + "pedra natural", "m2", 283.06, 168.28, null);
            code(sheet, 8, null, "Alternativa em Etics de 60mm, aplicado sobre a alvenaria",
                    "m2", 283.06, 77.79, 22019.24);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile(
                    "file", "orcamento.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void text(Sheet sheet, int rowIdx, String... values) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < values.length; c++) {
            if (values[c] != null) {
                row.createCell(c).setCellValue(values[c]);
            }
        }
    }

    /** code | name | unit | qty | unitPrice | total — nulos ficam por preencher. */
    private static void code(Sheet sheet, int rowIdx, String code, String name, String unit,
                             Double qty, Double unitPrice, Double total) {
        Row row = sheet.createRow(rowIdx);
        if (code != null) row.createCell(0).setCellValue(code);
        if (name != null) row.createCell(1).setCellValue(name);
        if (unit != null) row.createCell(2).setCellValue(unit);
        if (qty != null) row.createCell(3).setCellValue(qty);
        if (unitPrice != null) row.createCell(4).setCellValue(unitPrice);
        if (total != null) row.createCell(5).setCellValue(total);
    }
}
