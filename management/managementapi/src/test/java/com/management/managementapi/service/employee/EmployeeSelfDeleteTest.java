package com.management.managementapi.service.employee;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.service.ProfileService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Eliminar contas é `hasRole('ADMIN')`, o que quer dizer que o único alvo que um
 * admin nunca deve conseguir apontar é ele próprio: ficaria de fora sem forma de
 * voltar a entrar, e pode não haver outro admin para repor a conta.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeSelfDeleteTest {

    @Mock private NamedParameterJdbcTemplate jdbc;
    @Mock private AuditorAware<UUID> auditorAware;
    @Mock private ProfileService profileService;

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OUTRO = UUID.randomUUID();

    private EmployeeServiceImpl service() {
        when(auditorAware.getCurrentAuditor()).thenReturn(Optional.of(ME));
        return new EmployeeServiceImpl(jdbc, auditorAware, profileService);
    }

    @Test
    @DisplayName("Eliminar a própria conta é recusado antes de chegar à base de dados")
    void refusesSelfDelete() {
        EmployeeServiceImpl service = service();

        assertThatThrownBy(() -> service.deleteProfile(ME))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_CANNOT_DELETE_SELF);

        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test
    @DisplayName("Eliminar outra conta continua a passar")
    void stillDeletesOtherAccounts() {
        EmployeeServiceImpl service = service();
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        assertThatCode(() -> service.deleteProfile(OUTRO)).doesNotThrowAnyException();
    }
}
