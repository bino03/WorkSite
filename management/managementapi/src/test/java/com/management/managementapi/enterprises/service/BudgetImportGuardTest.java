package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
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
    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private BudgetExcelImportService service;

    private static final UUID ENTERPRISE_ID = UUID.randomUUID();

    private MockMultipartFile excel() {
        return new MockMultipartFile(
                "file", "orcamento.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "conteudo-irrelevante".getBytes());
    }

    private void enterpriseExists() {
        when(enterpriseRepository.findById(ENTERPRISE_ID)).thenReturn(Optional.of(new Enterprise()));
    }

    @Test
    @DisplayName("Gravar sobre um projeto que já tem orçamento é recusado")
    void refusesImportWhenBudgetAlreadyExists() {
        enterpriseExists();
        when(repository.existsByEnterpriseId(ENTERPRISE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.importBudget(ENTERPRISE_ID, excel(), false, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_IMPORT_NOT_EMPTY);
    }

    @Test
    @DisplayName("A pré-visualização (dryRun) continua a correr com orçamento existente")
    void previewIsNotBlockedByExistingBudget() {
        enterpriseExists();

        // Chega ao parse e morre nos bytes falsos — o que importa é que não morre na guarda.
        BusinessException thrown = catchThrowableOfType(
                () -> service.importBudget(ENTERPRISE_ID, excel(), true, false),
                BusinessException.class);

        assertThat(thrown).isNotNull();
        assertThat(thrown.getErrorCode()).isNotEqualTo(ErrorCode.BUDGET_IMPORT_NOT_EMPTY);
    }
}
