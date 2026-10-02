package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.budget.response.BudgetImportResultDTO;
import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * O orçamento de estrutura do Loteamento Aleu — Lote 3 é um <i>terceiro</i>
 * layout, além do orçamento de empreiteiro de 7 colunas e da "Orçamento inicial"
 * do vault (ver {@link BudgetImportVaultLayoutTest}). Falhou o upload a
 * 2026-10-01 com BUDGET_IMPORT_NO_HEADER, e cada uma das seis diferenças que
 * tinha para o que o importador sabia ler está aqui fixada:
 *
 * <ol>
 *   <li>o livro abre com uma folha de capa vazia ("0000") e a tabela está na
 *       segunda folha ("ORÇAMENTO") — lia-se a folha 0 às cegas;</li>
 *   <li>a coluna A está vazia como margem e a tabela começa na B — o cabeçalho
 *       só se procurava na coluna A;</li>
 *   <li>o preço unitário chama-se "P.U." e o total "VALOR" — como o cabeçalho
 *       tem "Descrição", entrava no modo estrito e as duas colunas ficavam a -1:
 *       o orçamento importava inteiro <b>a zero euros, sem erro nenhum</b>, que
 *       era pior do que falhar;</li>
 *   <li>os capítulos escrevem-se "CAP. 1", "CAP. 2", "CAP.3";</li>
 *   <li>o terceiro nível separa-se por vírgula: "1.1,1";</li>
 *   <li>a tabela fecha em "VALOR TOTAL DO ORÇAMENTO" e não num "TOTAL" seco —
 *       sem o reconhecer, o total entrava como rubrica e a obra valia o dobro.</li>
 * </ol>
 *
 * Pelo caminho apanhou-se um sétimo problema que não é deste layout nem deste
 * ficheiro — uma fórmula de preço arrastada para uma linha vazia apagava o preço
 * da rubrica de cima. Tem teste próprio em {@link #strayFormulaRowIsIgnored()}.
 */
@ExtendWith(MockitoExtension.class)
class BudgetImportContractorLayoutTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private BudgetExcelImportService service;

    private static final UUID LOT_ID = UUID.randomUUID();
    private static final String FILE = "Orcamento Estrutura - Aleu Lote 3.xls";

    private static ConstructionBudget lotOf(String name) {
        ConstructionBudget lot = new ConstructionBudget();
        lot.setEnterprise(new Enterprise());
        lot.setName(name);
        return lot;
    }

    private BudgetImportResultDTO importFile() throws Exception {
        when(budgetRepository.findById(LOT_ID)).thenReturn(Optional.of(lotOf("Lote 3")));
        byte[] content;
        try (InputStream in = getClass().getResourceAsStream("/excel-parity/" + FILE)) {
            content = in.readAllBytes();
        }
        return service.importBudget(LOT_ID,
                new MockMultipartFile("file", FILE, "application/vnd.ms-excel", content),
                true, false);
    }

    @Test
    @DisplayName("O orçamento do Aleu Lote 3 entra da 2.ª folha, com a tabela na coluna B, e bate certo ao cêntimo")
    void contractorLayoutImportsWithPricesAndHierarchy() throws Exception {
        BudgetImportResultDTO result = importFile();

        // (1) a folha com a tabela, não a capa "0000"
        assertThat(result.sheetName()).isEqualTo("ORÇAMENTO");

        // (3) + (6) os preços entram, a linha "VALOR TOTAL DO ORÇAMENTO" fecha a
        // tabela (sem a reconhecer o total entrava como rubrica e a obra valia o
        // dobro), e a nossa soma bate certo ao cêntimo com o TOTAL do Excel
        assertThat(result.parsedTotal()).isEqualByComparingTo("1508658.00");
        assertThat(result.excelTotal()).isEqualByComparingTo("1508658.00");
        assertThat(result.totalDifference()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warnings()).noneMatch(w -> w.contains("não bate certo"));

        // 23 rubricas (3 capítulos + 20 linhas), 2 sub-títulos ("LOTE 3" e o do estaleiro)
        assertThat(result.itemCount()).isEqualTo(23);
        assertThat(result.headingCount()).isEqualTo(2);
        // a nota do IVA está *abaixo* da linha de total: não se lê
        assertThat(result.noteCount()).isZero();
        assertThat(result.rows()).noneMatch(r -> r.name().startsWith("(a este valor"));

        // (4) "CAP. 1" / "CAP. 2" / "CAP.3" valem 1, 2 e 3 — e ficam na raiz
        assertThat(result.rows()).filteredOn(r -> "1".equals(r.code())).singleElement()
                .satisfies(r -> {
                    assertThat(r.kind()).isEqualTo(BudgetRowKind.ITEM);
                    assertThat(r.name()).isEqualTo("TRABALHOS PRELIMINARES");
                    assertThat(r.depth()).isZero();
                });
        assertThat(result.rows()).filteredOn(r -> "3".equals(r.code())).singleElement()
                .satisfies(r -> assertThat(r.name()).isEqualTo("IMPERMEABILIZAÇÕES"));

        // (5) "1.1,1" é a sub-rubrica 1.1.1, com o seu preço e dentro de 1.1
        assertThat(result.rows()).filteredOn(r -> "1.1.1".equals(r.code())).singleElement()
                .satisfies(r -> {
                    assertThat(r.name()).isEqualTo("Montagem");
                    assertThat(r.unit()).isEqualTo("vg");
                    assertThat(r.quantity()).isEqualByComparingTo("1");
                    assertThat(r.unitPrice()).isEqualByComparingTo("20000.00");
                    assertThat(r.totalPrice()).isEqualByComparingTo("20000.00");
                    assertThat(r.parentCode()).isEqualTo("1.1");
                });

        // as rubricas do capítulo 2 penduram do próprio capítulo
        assertThat(result.rows()).filteredOn(r -> "2.4".equals(r.code())).singleElement()
                .satisfies(r -> {
                    assertThat(r.unitPrice()).isEqualByComparingTo("450.00");
                    assertThat(r.totalPrice()).isEqualByComparingTo("819000.00");
                    assertThat(r.parentCode()).isEqualTo("2");
                });

        // nenhuma rubrica com índice fica sem preço por causa do nome da coluna:
        // as únicas sem total próprio são as que o têm no detalhe por baixo
        assertThat(result.rows())
                .filteredOn(r -> r.code() != null && r.code().startsWith("3."))
                .allSatisfy(r -> assertThat(r.totalPrice()).isNotNull());
    }

    @Test
    @DisplayName("Uma fórmula de preço arrastada para uma linha vazia é ignorada, e não apaga o preço da rubrica acima")
    void strayFormulaRowIsIgnored() throws Exception {
        BudgetImportResultDTO result = importFile();

        // A linha 51 do Aleu Lote 3 é uma linha de separação vazia onde ficou um
        // "=F51*D51" arrastado, com 0 em cache. Entrava como rubrica fantasma
        // "Sem descrição" pendurada na 3.7 e, por ter total (ainda que 0), fazia o
        // rollup da 3.7 valer os filhos em vez dos seus 600 € — o orçamento saía
        // 600 € abaixo com um aviso de "não bate certo" que ninguém sabia explicar.
        assertThat(result.rows()).noneMatch(r -> r.excelRow() == 51);
        assertThat(result.warnings()).noneMatch(w -> w.contains("sem descrição"));

        assertThat(result.rows()).filteredOn(r -> "3.7".equals(r.code())).singleElement()
                .satisfies(r -> assertThat(r.totalPrice()).isEqualByComparingTo("600.00"));
    }
}
