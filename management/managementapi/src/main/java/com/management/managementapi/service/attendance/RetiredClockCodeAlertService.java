package com.management.managementapi.service.attendance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.model.attendance.RetiredClockCode;
import com.management.managementapi.model.attendance.RetiredClockCodeAttempt;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.repository.attendance.RetiredClockCodeAttemptRepository;

/**
 * regista uma tentativa de picar com um QR já substituído e avisa os ADMIN — uma vez
 * por funcionário, código e dia.
 *
 * <p>É a exceção à regra do {@link NotificationService} ("a notificação vai na mesma
 * transação do que a originou"): aqui o que a origina é um <em>erro</em>. Quem chama
 * lança {@code ATT_026} a seguir, e essa exceção desfaz a transação dele — por isso a
 * tentativa e o aviso gravam-se numa transação própria ({@code REQUIRES_NEW}), que já
 * fez commit quando a exceção sobe. Bean à parte porque o proxy do Spring não
 * intercepta chamadas dentro da mesma classe.
 */
@Service
public class RetiredClockCodeAlertService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RetiredClockCodeAttemptRepository attempts;
    private final ProfileRepository profiles;
    private final NotificationService notifications;
    private final AttendanceZone zone;

    public RetiredClockCodeAlertService(RetiredClockCodeAttemptRepository attempts,
                                        ProfileRepository profiles,
                                        NotificationService notifications,
                                        AttendanceZone zone) {
        this.attempts = attempts;
        this.profiles = profiles;
        this.notifications = notifications;
        this.zone = zone;
    }

    /** Devolve {@code true} se foi a primeira tentativa do dia (e portanto avisou). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordAttempt(RetiredClockCode retired, UUID profileId) {
        LocalDate today = zone.today();
        if (attempts.existsByRetiredCodeIdAndProfileIdAndAttemptedOn(retired.getId(), profileId, today)) {
            return false;
        }
        attempts.save(new RetiredClockCodeAttempt(retired, profileId, today));

        String employee = profiles.findNameOnlyById(profileId).orElse("Um funcionário");
        String enterprise = retired.getEnterprise().getName();
        String retiredOn = retired.getRetiredAt().atZoneSameInstant(zone.zoneId()).format(DATE);
        List<UUID> admins = profiles.findIdsByRoleInAndAccountStatus(List.of(ProfileRole.ADMIN), AccountStatus.unlocked);

        notifications.notifyAll(
                admins,
                Notification.TYPE_CLOCK_RETIRED_QR,
                "Picagem com QR antigo",
                employee + " tentou picar na obra " + enterprise + " com um QR antigo (substituído a " + retiredOn + ").",
                "/team/time-entries?profile=" + profileId + "&day=" + today,
                profileId);
        return true;
    }
}
