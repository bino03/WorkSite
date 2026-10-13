package com.management.managementapi.notifications;

import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.Supplier;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.SupplierRepository;
import com.management.managementapi.model.enums.AccountStatus;
import com.management.managementapi.model.enums.ProfileRole;
import com.management.managementapi.notifications.model.Notification;
import com.management.managementapi.notifications.repository.NotificationRepository;
import com.management.managementapi.notifications.service.InvoiceDueNotifier;
import com.management.managementapi.notifications.service.NotificationService;
import com.management.managementapi.repository.ProfileRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regras decididas a 2026-10-13: avisa todos os admins, uma vez por fatura e
 * destinatário, 7 dias antes do vencimento; todas as por liquidar com
 * vencimento, aprovadas ou não, com o texto a dizer qual é o caso. Que faturas
 * estão por liquidar é da query; aqui testa-se a janela e o aviso.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceDueNotifierTest {

    @Mock private ConstructionInvoiceRepository invoices;
    @Mock private SupplierRepository suppliers;
    @Mock private ProfileRepository profiles;
    @Mock private NotificationRepository notifications;
    @Mock private NotificationService notificationService;

    private InvoiceDueNotifier notifier;

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 13);
    private static final String NIF = "502693134";
    private static final UUID ADMIN_A = UUID.randomUUID();
    private static final UUID ADMIN_B = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifier = new InvoiceDueNotifier(invoices, suppliers, profiles, notifications, notificationService, 7);
        when(profiles.findIdsByRoleAndAccountStatus(ProfileRole.ADMIN, AccountStatus.unlocked))
                .thenReturn(List.of(ADMIN_A, ADMIN_B));

        Supplier brivel = new Supplier();
        brivel.setNif(NIF);
        brivel.setName("Brivel");
        brivel.setPaymentTermDays(30);
        when(suppliers.findAll()).thenReturn(List.of(brivel));
        when(notifications.existsByRecipientIdAndTypeAndEntityId(any(), anyString(), any())).thenReturn(false);
    }

    /** Fatura do Brivel (30 dias) que vence daqui a {@code daysUntilDue} dias. */
    private ConstructionInvoice faturaAVencer(int daysUntilDue) {
        Enterprise obra = new Enterprise();
        obra.setId(UUID.randomUUID());
        obra.setName("Vila Petrus");

        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setEnterprise(obra);
        invoice.setSupplierNif(NIF);
        invoice.setInvoiceNumber("FT 2026/114");
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setInvoiceDate(HOJE.plusDays(daysUntilDue).minusDays(30));
        return invoice;
    }

    private void porLiquidar(ConstructionInvoice... list) {
        when(invoices.findOutstandingExcludingScope(ConstructionInvoice.Scope.UNIDENTIFIED))
                .thenReturn(List.of(list));
    }

    @Test
    @DisplayName("Cada fatura avisa todos os admins, com link para a página A pagar e o estado da aprovação")
    void notifiesEveryAdminOnce() {
        ConstructionInvoice invoice = faturaAVencer(3);
        porLiquidar(invoice);

        int created = notifier.notifyUpcomingDueDates(HOJE);

        assertThat(created).isEqualTo(2);
        verify(notificationService).notify(eq(ADMIN_A), eq(Notification.TYPE_INVOICE_DUE), eq("Fatura a vencer"),
                eq("Brivel · FT 2026/114 · Vila Petrus — vence em 3 dias (16/10/2026) · por aprovar"),
                eq("/backoffice/invoices/outstanding"), eq(invoice.getId()));
        verify(notificationService).notify(eq(ADMIN_B), eq(Notification.TYPE_INVOICE_DUE), anyString(),
                anyString(), anyString(), eq(invoice.getId()));
    }

    @Test
    @DisplayName("Uma fatura já aprovada avisa na mesma, e o texto di-lo")
    void approvedInvoiceSaysSo() {
        ConstructionInvoice invoice = faturaAVencer(0);
        invoice.setApprovedForPayment(true);
        porLiquidar(invoice);

        notifier.notifyUpcomingDueDates(HOJE);

        verify(notificationService).notify(eq(ADMIN_A), anyString(), anyString(),
                eq("Brivel · FT 2026/114 · Vila Petrus — vence hoje · aprovada para pagamento"),
                anyString(), eq(invoice.getId()));
    }

    @Test
    @DisplayName("A janela é [hoje, hoje + 7]: a que vence no 7º dia entra, a do 8º e a já vencida não")
    void onlyTheConfiguredWindow() {
        ConstructionInvoice lastDay = faturaAVencer(7);
        ConstructionInvoice tooFar = faturaAVencer(8);
        ConstructionInvoice alreadyOverdue = faturaAVencer(-1);
        porLiquidar(lastDay, tooFar, alreadyOverdue);

        int created = notifier.notifyUpcomingDueDates(HOJE);

        assertThat(created).isEqualTo(2);
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), anyString(), eq(tooFar.getId()));
        verify(notificationService, never()).notify(any(), anyString(), anyString(), anyString(), anyString(), eq(alreadyOverdue.getId()));
    }

    @Test
    @DisplayName("Fatura sem vencimento não avisa — não há dia de que avisar")
    void noDueDateNoWarning() {
        ConstructionInvoice semNif = faturaAVencer(3);
        semNif.setSupplierNif(null);
        porLiquidar(semNif);

        assertThat(notifier.notifyUpcomingDueDates(HOJE)).isZero();
    }

    @Test
    @DisplayName("O vencimento escrito à mão conta, mesmo sem fornecedor; despesa da empresa diz Empresa")
    void manualDueDateCounts() {
        ConstructionInvoice company = new ConstructionInvoice();
        company.setId(UUID.randomUUID());
        company.setScope(ConstructionInvoice.Scope.COMPANY);
        company.setSupplierName("Allianz");
        company.setTotalAmount(new BigDecimal("250.00"));
        company.setDueDateOverride(HOJE.plusDays(1));
        porLiquidar(company);

        notifier.notifyUpcomingDueDates(HOJE);

        verify(notificationService).notify(eq(ADMIN_A), anyString(), anyString(),
                eq("Allianz · Empresa — vence amanhã · por aprovar"), anyString(), eq(company.getId()));
    }

    @Test
    @DisplayName("Quem já foi avisado desta fatura não volta a ser — o job corre todos os dias")
    void skipsAlreadyNotified() {
        ConstructionInvoice invoice = faturaAVencer(2);
        porLiquidar(invoice);
        when(notifications.existsByRecipientIdAndTypeAndEntityId(eq(ADMIN_A), anyString(), eq(invoice.getId())))
                .thenReturn(true);

        int created = notifier.notifyUpcomingDueDates(HOJE);

        assertThat(created).isEqualTo(1);
        verify(notificationService, never()).notify(eq(ADMIN_A), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Sem admins desbloqueados não consulta faturas nem cria nada")
    void noAdminsNoWork() {
        when(profiles.findIdsByRoleAndAccountStatus(ProfileRole.ADMIN, AccountStatus.unlocked)).thenReturn(List.of());

        assertThat(notifier.notifyUpcomingDueDates(HOJE)).isZero();
        verifyNoInteractions(invoices);
    }
}
