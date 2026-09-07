package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.enterprise.edit.EditOverViewCardDTO;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.EnterpriseLocationRepository;
import com.management.managementapi.enterprises.repository.EnterpriseMediaRepository;
import com.management.managementapi.enterprises.repository.EnterpriseRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.integrations.supabase.SupabaseStorageService;
import com.management.managementapi.repository.LocationRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O {@code slug} da obra é a chave da paridade com o vault Excel da Vilatro: dois projetos
 * não podem apontar para a mesma pasta. O {@code PATCH .../overview} passou a carregar
 * {@code slug} e {@code isTest} (antes só {@code EnterpriseService.update} os validava), e
 * estas são as propriedades que o não podem partir:
 *
 * <ol>
 *   <li>slug repetido noutro projeto → {@code ENT_032}, nada é gravado;</li>
 *   <li>slug em branco limpa a coluna, sem sequer consultar duplicados;</li>
 *   <li>slug ausente no corpo (PATCH) não mexe no que lá está.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnterpriseOverviewSlugTest {

    @Mock private EnterpriseRepository enterpriseRepository;
    @Mock private EnterpriseLocationRepository enterpriseLocationRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private EnterpriseMediaRepository enterprisesMediaRepository;
    @Mock private SupabaseStorageService storageService;

    private static final UUID ID = UUID.randomUUID();

    private EnterpriseRelationsService service() {
        when(enterpriseRepository.save(any(Enterprise.class))).thenAnswer(c -> c.getArgument(0));
        return new EnterpriseRelationsService(
                enterpriseRepository, enterpriseLocationRepository, locationRepository,
                enterprisesMediaRepository, storageService);
    }

    private Enterprise existing(String slug, boolean isTest) {
        Enterprise e = new Enterprise();
        e.setId(ID);
        e.setName("Obra");
        e.setSlug(slug);
        e.setIsTest(isTest);
        when(enterpriseRepository.findById(ID)).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    @DisplayName("slug novo e isTest são aplicados quando o slug está livre")
    void aplicaSlugEIsTest() {
        Enterprise e = existing(null, false);
        when(enterpriseRepository.existsBySlugAndIdNot("Vila Petrus", ID)).thenReturn(false);

        EditOverViewCardDTO dto = new EditOverViewCardDTO();
        dto.setSlug("  Vila Petrus  ");
        dto.setIsTest(true);

        EditOverViewCardDTO out = service().updateOverview(ID, dto);

        assertThat(e.getSlug()).isEqualTo("Vila Petrus");
        assertThat(e.getIsTest()).isTrue();
        assertThat(out.getSlug()).isEqualTo("Vila Petrus");
        assertThat(out.getIsTest()).isTrue();
    }

    @Test
    @DisplayName("slug já usado noutro projeto → ENT_032, nada gravado")
    void slugDuplicado() {
        Enterprise e = existing("Antigo", false);
        when(enterpriseRepository.existsBySlugAndIdNot("Vila Petrus", ID)).thenReturn(true);

        EditOverViewCardDTO dto = new EditOverViewCardDTO();
        dto.setSlug("Vila Petrus");

        assertThatThrownBy(() -> service().updateOverview(ID, dto))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ENTERPRISE_DUPLICATE_SLUG);

        assertThat(e.getSlug()).isEqualTo("Antigo");
        verify(enterpriseRepository, never()).save(any());
    }

    @Test
    @DisplayName("slug em branco limpa a coluna e não consulta duplicados")
    void slugEmBrancoLimpa() {
        Enterprise e = existing("Vila Petrus", false);

        EditOverViewCardDTO dto = new EditOverViewCardDTO();
        dto.setSlug("   ");

        service().updateOverview(ID, dto);

        assertThat(e.getSlug()).isNull();
        verify(enterpriseRepository, never()).existsBySlugAndIdNot(anyString(), any());
    }

    @Test
    @DisplayName("slug ausente no corpo não mexe no slug existente (PATCH)")
    void slugAusenteNaoMexe() {
        Enterprise e = existing("Vila Petrus", true);

        EditOverViewCardDTO dto = new EditOverViewCardDTO();
        dto.setName("Obra renomeada");

        service().updateOverview(ID, dto);

        assertThat(e.getSlug()).isEqualTo("Vila Petrus");
        assertThat(e.getIsTest()).isTrue();
        assertThat(e.getName()).isEqualTo("Obra renomeada");
        verify(enterpriseRepository, never()).existsBySlugAndIdNot(anyString(), any());
    }
}
