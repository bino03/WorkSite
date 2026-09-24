package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.ConstructionBudget;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.ConstructionBudgetRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

/**
 * Importar por cima de um orçamento existente duplicaria a árvore toda — o
 * importador só sabe criar, nunca reconcilia com o que já lá está.
 *
 * A guarda vive antes do parse, o que também é o que torna este teste barato:
 * os bytes do ficheiro nunca chegam a ser lidos no caso que interessa.
 */
@ExtendWith(MockitoExtension.class)
class BudgetImportGuardTest {

    @Mock private ConstructionBudgetItemRepository repository;
    @Mock private ConstructionBudgetRepository budgetRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private BudgetExcelImportService service;

    private static final UUID BUDGET_ID = UUID.randomUUID();

    private MockMultipartFile excel() {
        return new MockMultipartFile(
                "file", "orcamento.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "conteudo-irrelevante".getBytes());
    }

    private void lotExists() {
        ConstructionBudget lot = new ConstructionBudget();
        lot.setEnterprise(new Enterprise());
        when(budgetRepository.findById(BUDGET_ID)).thenReturn(Optional.of(lot));
    }

    @Test
    @DisplayName("Gravar sobre um lote que já tem orçamento é recusado")
    void refusesImportWhenBudgetAlreadyExists() {
        lotExists();
        when(repository.existsByBudgetId(BUDGET_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.importBudget(BUDGET_ID, excel(), false, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_IMPORT_NOT_EMPTY);
    }

    @Test
    @DisplayName("A pré-visualização (dryRun) continua a correr com orçamento existente")
    void previewIsNotBlockedByExistingBudget() {
        lotExists();

        // Chega ao parse e morre nos bytes falsos — o que importa é que não morre na guarda.
        BusinessException thrown = catchThrowableOfType(
                () -> service.importBudget(BUDGET_ID, excel(), true, false),
                BusinessException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getErrorCode()).isNotEqualTo(ErrorCode.BUDGET_IMPORT_NOT_EMPTY);
    }
}
