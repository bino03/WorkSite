package com.management.managementapi.notifications;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;
import com.management.managementapi.notifications.service.NotificationService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * O id de uma notificação vai no URL. Sem esta verificação, adivinhar um id
 * bastava para marcar como lida a notificação de outra pessoa.
 *
 * A resposta é NOT_FOUND e não "sem permissão" de propósito: dizer "existe mas
 * não é tua" confirma a existência a quem não devia sequer saber disso.
 */
@ExtendWith(MockitoExtension.class)
class NotificationOwnershipTest {

    @Mock private NotificationRepository repository;
    @InjectMocks private NotificationService service;

    @Test
    @DisplayName("Marcar como lida a notificação de outra pessoa é recusado")
    void refusesToMarkSomeoneElsesNotification() {
        UUID dono = UUID.randomUUID();
        UUID intruso = UUID.randomUUID();

        Notification alheia = new Notification();
        alheia.setRecipientId(dono);
        when(repository.findById(any())).thenReturn(Optional.of(alheia));

        assertThatThrownBy(() -> service.markRead(UUID.randomUUID(), intruso))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);

        verify(repository, never()).save(any());
    }
}
