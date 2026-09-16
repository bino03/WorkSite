package com.management.managementapi.notifications;

import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;
import com.management.managementapi.notifications.service.BudgetItemDeadlineNotifier;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regras decididas a 2026-09-16: avisa todos os admins, uma vez por rubrica e
 * destinatário, com {@code daysAhead} dias de antecedência. A seleção por
 * janela/estado da obra vive na query; aqui testa-se o que o serviço faz com
 * o que a query devolve.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetItemDeadlineNotifierTest {

    @Mock private ConstructionBudgetItemRepository budgetItems;
    @Mock private ProfileRepository profiles;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationService notificationService;

    private BudgetItemDeadlineNotifier notifier;

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 16);
    private static final UUID ADMIN_A = UUID.randomUUID();
    private static final UUID ADMIN_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new BudgetItemDeadlineNotifier(budgetItems, profiles, notifications, notificationService, 7);
        when(profiles.findIdsByRoleAndAccountStatus(ProfileRole.ADMIN, AccountStatus.unlocked))
                .thenReturn(List.of(ADMIN_A, ADMIN_B));
    }

    private ConstructionBudgetItem rubrica(String code, String name, LocalDate endDate) {
        Enterprise obra = new Enterprise();
        obra.setId(UUID.randomUUID());
        obra.setName("Vila Petrus");

        ConstructionBudgetItem item = new ConstructionBudgetItem();
        item.setId(UUID.randomUUID());
        item.setEnterprise(obra);
        item.setCode(code);
        item.setName(name);
        item.setEndDate(endDate);
        return item;
    }

    @Test
    @DisplayName("Pede à query a janela [hoje, hoje + daysAhead]")
    void queriesTheConfiguredWindow() {
        when(budgetItems.findActiveItemsEndingBetween(eq(BudgetRowKind.ITEM), any(), any())).thenReturn(List.of());

        notifier.notifyUpcomingDeadlines(HOJE);

        verify(budgetItems).findActiveItemsEndingBetween(BudgetRowKind.ITEM, HOJE, HOJE.plusDays(7));
    }

    @Test
    @DisplayName("Cada rubrica avisa todos os admins, com link para o orçamento da obra")
    void notifiesEveryAdminOnce() {
        ConstructionBudgetItem item = rubrica("4.2.1", "Betão armado", HOJE.plusDays(3));
        when(budgetItems.findActiveItemsEndingBetween(eq(BudgetRowKind.ITEM), any(), any())).thenReturn(List.of(item));
        when(notifications.existsByRecipientIdAndTypeAndEntityId(any(), anyString(), any())).thenReturn(false);

        int created = notifier.notifyUpcomingDeadlines(HOJE);

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(ADMIN_A), eq(Notification.TYPE_BUDGET_ITEM_DEADLINE), anyString(),
                body.capture(), eq("/backoffice/empreendimentos/" + item.getEnterprise().getId() + "/budget"),
                eq(item.getId()));
        verify(notificationService).notify(eq(ADMIN_B), eq(Notification.TYPE_BUDGET_ITEM_DEADLINE), anyString(),
                anyString(), anyString(), eq(item.getId()));
        assertThat(body.getValue()).isEqualTo("4.2.1 — Betão armado · Vila Petrus — termina em 3 dias (19/09/2026)");
    }

    @Test
    @DisplayName("Quem já foi avisado desta rubrica não volta a ser — o job corre todos os dias")
    void skipsAlreadyNotified() {
        ConstructionBudgetItem item = rubrica(null, "Alternativa em madeira", HOJE);
        when(budgetItems.findActiveItemsEndingBetween(eq(BudgetRowKind.ITEM), any(), any())).thenReturn(List.of(item));
        when(notifications.existsByRecipientIdAndTypeAndEntityId(eq(ADMIN_A), anyString(), eq(item.getId())))
                .thenReturn(true);
        when(notifications.existsByRecipientIdAndTypeAndEntityId(eq(ADMIN_B), anyString(), eq(item.getId())))
                .thenReturn(false);

        int created = notifier.notifyUpcomingDeadlines(HOJE);

        assertThat(created).isEqualTo(1);
        verify(notificationService, never()).notify(eq(ADMIN_A), anyString(), anyString(), anyString(), anyString(), any());
        verify(notificationService).notify(eq(ADMIN_B), eq(Notification.TYPE_BUDGET_ITEM_DEADLINE), anyString(),
                eq("Alternativa em madeira · Vila Petrus — termina hoje"), anyString(), eq(item.getId()));
    }

    @Test
    @DisplayName("Sem admins desbloqueados não consulta rubricas nem cria nada")
    void noAdminsNoWork() {
        when(profiles.findIdsByRoleAndAccountStatus(ProfileRole.ADMIN, AccountStatus.unlocked)).thenReturn(List.of());

        int created = notifier.notifyUpcomingDeadlines(HOJE);

        assertThat(created).isZero();
        verify(budgetItems, times(0)).findActiveItemsEndingBetween(eq(BudgetRowKind.ITEM), any(), any());
    }
}
