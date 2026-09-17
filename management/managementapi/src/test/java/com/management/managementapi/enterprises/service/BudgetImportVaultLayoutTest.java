package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * A "Orçamento inicial" do {@code Despesas - <Obra>.xlsx} do vault não é o
 * orçamento do empreiteiro: tem só {@code Rubrica | Descrição | Preço total}
 * (3 colunas em vez de 7). Descoberto ao migrar o Vila Petrus a 2026-09-17 —
 * o importador lia o total na coluna F e entrava tudo a zero. As colunas
 * resolvem-se agora pelo cabeçalho.
 */
@ExtendWith(MockitoExtension.class)
class BudgetImportVaultLayoutTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private BudgetExcelImportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    @Test
    @DisplayName("A \"Orçamento inicial\" do vault (3 colunas) entra com os preços na coluna certa e o TOTAL do Excel")
    void vaultBudgetSheetReadsPricesByHeader() throws Exception {
        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(new Enterprise()));
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/excel-parity/Despesas - Vila Petrus.xlsx")) {
            content = in.readAllBytes();
        }

        BudgetImportResultDTO result = service.importBudget(ENTERPRISE_ID,
                new MockMultipartFile("file", "Despesas - Vila Petrus.xlsx", BudgetExcelExportService.CONTENT_TYPE, content),
                true, false);

        assertThat(result.sheetName()).isEqualTo("Orçamento inicial");
        assertThat(result.excelTotal()).isEqualByComparingTo("3000000.00");
        // o Excel soma floats e o importador arredonda célula a célula: 2 cêntimos de diferença, abaixo do 1 € que avisa
        assertThat(result.parsedTotal()).isEqualByComparingTo("2999999.98");
        assertThat(result.totalDifference().abs()).isLessThan(java.math.BigDecimal.ONE);
        assertThat(result.warnings()).noneMatch(w -> w.contains("não bate certo"));
        assertThat(result.itemCount()).isEqualTo(178); // 174 com índice + as 4 "Alternativa …" que trazem preço
        assertThat(result.headingCount()).isEqualTo(9); // "Paredes", "Pavimentos", "Tectos", "Serralharia de …", …
        assertThat(result.noteCount()).isEqualTo(11);
        assertThat(result.rows()).filteredOn(r -> "1".equals(r.code())).singleElement()
                .satisfies(r -> {
                    assertThat(r.kind()).isEqualTo(BudgetRowKind.ITEM);
                    assertThat(r.totalPrice()).isEqualByComparingTo("63359.20");
                });
        // os dois índices repetidos do Petrus (8.2 e 13.2.1) entram sem índice, avisados — nunca se desempata sozinho
        assertThat(result.warnings()).filteredOn(w -> w.contains("repetido")).hasSize(2);
    }
}
