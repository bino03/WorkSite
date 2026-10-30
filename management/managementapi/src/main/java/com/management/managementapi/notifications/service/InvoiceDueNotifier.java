package com.management.managementapi.notifications.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.InvoiceDueDate;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;
import com.management.managementapi.repository.ProfileRepository;

/**
 * Avisa os admins e a contabilidade das faturas por liquidar que vencem dentro de
 * {@code daysAhead} dias.
 *
 * O mesmo desenho do {@link BudgetItemDeadlineNotifier}: todos os {@link ProfileRole#WORKS_MANAGERS}
 * desbloqueados, uma vez por fatura e por destinatário (dedupe por
 * {@code (recipient, type, entity)}). Por isso mudar o prazo do fornecedor, ou
 * o vencimento à mão, de uma fatura já avisada não volta a avisar.
 *
 * As faturas são as da página "A pagar" — obras e empresa, sem quarentena, sem
 * obras de teste — e só as que têm vencimento: uma fatura "sem prazo" não tem
 * dia de que avisar. As já vencidas também não entram; para essas há a lista,
 * que as mostra à frente. Avisa de todas, aprovadas ou não, e o texto diz qual
 * é o caso.
 */
@Service
@Transactional
public class InvoiceDueNotifier {

    private static final DateTimeFormatter DATA_PT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String LINK = "/backoffice/invoices/outstanding";

    private final ConstructionInvoiceRepository invoices;
    private final SupplierRepository suppliers;
    private final ProfileRepository profiles;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;
    private final int daysAhead;

    public InvoiceDueNotifier(ConstructionInvoiceRepository invoices,
                              SupplierRepository suppliers,
                              ProfileRepository profiles,
                              NotificationRepository notifications,
                              NotificationService notificationService,
                              @Value("${app.notifications.invoice-due.days-ahead:7}") int daysAhead) {
        this.invoices = invoices;
        this.suppliers = suppliers;
        this.profiles = profiles;
        this.notifications = notifications;
        this.notificationService = notificationService;
        this.daysAhead = daysAhead;
    }

    /** Devolve quantas notificações foram criadas. */
    public int notifyUpcomingDueDates(LocalDate today) {
        List<UUID> recipients = profiles.findIdsByRoleInAndAccountStatus(ProfileRole.WORKS_MANAGERS, AccountStatus.unlocked);
        if (recipients.isEmpty()) return 0;

        Map<String, Supplier> suppliersByNif = suppliers.findAll().stream()
                .collect(Collectors.toMap(Supplier::getNif, Function.identity(), (first, second) -> first));
        LocalDate limit = today.plusDays(daysAhead);

        int created = 0;
        for (ConstructionInvoice invoice :
                invoices.findOutstandingExcludingScope(ConstructionInvoice.Scope.UNIDENTIFIED)) {
            Supplier supplier = invoice.getSupplierNif() == null
                    ? null
                    : suppliersByNif.get(invoice.getSupplierNif().trim());
            LocalDate dueDate = InvoiceDueDate.of(invoice, supplier).dueDate();
            if (dueDate == null || dueDate.isBefore(today) || dueDate.isAfter(limit)) {
                continue;
            }

            for (UUID recipient : recipients) {
                if (notifications.existsByRecipientIdAndTypeAndEntityId(recipient, Notification.TYPE_INVOICE_DUE, invoice.getId())) {
                    continue;
                }
                notificationService.notify(
                        recipient,
                        Notification.TYPE_INVOICE_DUE,
                        "Fatura a vencer",
                        descreve(invoice, supplier, dueDate, today),
                        LINK,
                        invoice.getId());
                created++;
            }
        }
        return created;
    }

    private static String descreve(ConstructionInvoice invoice, Supplier supplier, LocalDate dueDate, LocalDate today) {
        String fornecedor = firstNonBlank(
                invoice.getSupplierName(),
                supplier == null ? null : supplier.getName(),
                invoice.getSupplierNif(),
                "Fornecedor por identificar");
        String numero = invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()
                ? ""
                : " · " + invoice.getInvoiceNumber().trim();
        String dono = invoice.getEnterprise() == null ? "Empresa" : invoice.getEnterprise().getName();

        long dias = ChronoUnit.DAYS.between(today, dueDate);
        String quando = dias == 0 ? "vence hoje"
                : dias == 1 ? "vence amanhã"
                : "vence em " + dias + " dias (" + DATA_PT.format(dueDate) + ")";
        String aprovacao = invoice.isApprovedForPayment() ? "aprovada para pagamento" : "por aprovar";

        return fornecedor + numero + " · " + dono + " — " + quando + " · " + aprovacao;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
