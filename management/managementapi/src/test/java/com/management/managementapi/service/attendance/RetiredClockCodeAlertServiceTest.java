package com.management.managementapi.service.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.model.attendance.EnterpriseClockCode;
import com.management.managementapi.model.attendance.RetiredClockCode;
import com.management.managementapi.model.attendance.RetiredClockCodeAttempt;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.RetiredClockCodeAttemptRepository;

/** O aviso de QR antigo: uma vez por funcionário, código e dia, para todos os ADMIN. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetiredClockCodeAlertServiceTest {

    @Mock private RetiredClockCodeAttemptRepository attempts;
    @Mock private ProfileRepository profiles;
    @Mock private NotificationService notifications;

    private static final AttendanceZone ZONE = new AttendanceZone("Europe/Lisbon");
    private static final UUID ANA = UUID.randomUUID();
    private static final UUID ADMIN = UUID.randomUUID();

    private RetiredClockCode retired;

    private RetiredClockCodeAlertService service() {
        return new RetiredClockCodeAlertService(attempts, profiles, notifications, ZONE);
    }

    @BeforeEach
    void setUp() {
        Enterprise obra = new Enterprise();
        obra.setId(UUID.randomUUID());
        obra.setName("Villa Petrus");
        EnterpriseClockCode old = new EnterpriseClockCode();
        old.setEnterprise(obra);
        old.regenerate("codigo-antigo-que-foi-substituido", OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));
        retired = RetiredClockCode.of(old, OffsetDateTime.of(2026, 11, 20, 10, 0, 0, 0, ZoneOffset.UTC));

        when(profiles.findNameOnlyById(ANA)).thenReturn(Optional.of("Ana"));
        when(profiles.findIdsByRoleInAndAccountStatus(any(), eq(AccountStatus.unlocked))).thenReturn(List.of(ADMIN));
    }

    @Test
    @DisplayName("a primeira tentativa do dia regista-se e avisa os ADMIN, com quem, onde e quando")
    void primeiraTentativaAvisa() {
        when(attempts.existsByRetiredCodeIdAndProfileIdAndAttemptedOn(any(), eq(ANA), any())).thenReturn(false);

        assertThat(service().recordAttempt(retired, ANA)).isTrue();

        verify(attempts).save(any(RetiredClockCodeAttempt.class));
        verify(notifications).notifyAll(eq(List.of(ADMIN)), eq(Notification.TYPE_CLOCK_RETIRED_QR), anyString(),
                contains("Ana tentou picar na obra Villa Petrus com um QR antigo (substituído a 20/11/2026)"),
                contains("/team/time-entries?profile=" + ANA), eq(ANA));
    }

    @Test
    @DisplayName("as tentativas seguintes no mesmo dia não voltam a avisar")
    void repetidaNaoAvisa() {
        when(attempts.existsByRetiredCodeIdAndProfileIdAndAttemptedOn(any(), eq(ANA), any())).thenReturn(true);

        assertThat(service().recordAttempt(retired, ANA)).isFalse();

        verify(attempts, never()).save(any());
        verify(notifications, never()).notifyAll(anyCollection(), any(), any(), any(), any(), any());
    }
}
