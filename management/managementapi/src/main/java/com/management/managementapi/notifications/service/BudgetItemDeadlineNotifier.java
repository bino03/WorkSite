package com.management.managementapi.notifications.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.enterprises.model.BudgetRowKind;
import com.management.managementapi.enterprises.model.ConstructionBudgetItem;
import com.management.managementapi.enterprises.repository.ConstructionBudgetItemRepository;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;
import com.management.managementapi.repository.ProfileRepository;

/**
 * Avisa os admins das rubricas de orçamento cujo prazo está a {@code daysAhead}
 * dias ou menos de terminar.
 *
 * Destinatários são todos os {@code ADMIN} porque não existe ligação
 * funcionário↔obra no modelo ({@code Enterprise.managerId/ownerId} nunca foram
 * usados) — decisão de 2026-09-16. Cada rubrica avisa uma vez por destinatário:
 * o dedupe é por {@code (recipient, type, entity)}, por isso adiar a data de uma
 * rubrica já avisada não gera aviso novo.
 */
@Service
@Transactional
public class BudgetItemDeadlineNotifier {

    private static final DateTimeFormatter DATA_PT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConstructionBudgetItemRepository budgetItems;
    private final ProfileRepository profiles;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final int daysAhead;

    public BudgetItemDeadlineNotifier(ConstructionBudgetItemRepository budgetItems,
                                      ProfileRepository profiles,
                                      NotificationRepository notifications,
                                      NotificationService notificationService,
                                      @Value("${app.notifications.budget-item-deadline.days-ahead:7}") int daysAhead) {
        this.budgetItems = budgetItems;
        this.profiles = profiles;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.daysAhead = daysAhead;
    }

    /** Devolve quantas notificações foram criadas. */
    public int notifyUpcomingDeadlines(LocalDate today) {
        List<UUID> admins = profiles.findIdsByRoleAndAccountStatus(ProfileRole.ADMIN, AccountStatus.unlocked);
        if (admins.isEmpty()) return 0;

        List<ConstructionBudgetItem> items = budgetItems.findActiveItemsEndingBetween(BudgetRowKind.ITEM, today, today.plusDays(daysAhead));

        int created = 0;
        for (ConstructionBudgetItem item : items) {
            for (UUID admin : admins) {
                if (notifications.existsByRecipientIdAndTypeAndEntityId(admin, Notification.TYPE_BUDGET_ITEM_DEADLINE, item.getId())) {
                    continue;
                }
                notificationService.notify(
                        admin,
                        Notification.TYPE_BUDGET_ITEM_DEADLINE,
                        "Prazo de rubrica a terminar",
                        descreve(item, today),
                        "/backoffice/empreendimentos/" + item.getEnterprise().getId() + "/budget"
                                + (item.getBudgetId() == null ? "" : "?lote=" + item.getBudgetId()),
                        item.getId());
                created++;
            }
        }
        return created;
    }

    private static String descreve(ConstructionBudgetItem item, LocalDate today) {
        String rubrica = item.getCode() == null ? item.getName() : item.getCode() + " — " + item.getName();
        long dias = ChronoUnit.DAYS.between(today, item.getEndDate());
        String quando = dias == 0 ? "termina hoje"
                : dias == 1 ? "termina amanhã"
                : "termina em " + dias + " dias (" + DATA_PT.format(item.getEndDate()) + ")";
        return rubrica + " · " + item.getEnterprise().getName() + " — " + quando;
    }
}
