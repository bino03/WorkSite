package com.management.managementapi.enterprises.service;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentCreateDTO;
import com.management.managementapi.enterprises.dto.incident.InvoiceIncidentResponseDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.model.InvoiceIncident;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;
import com.management.managementapi.enterprises.repository.InvoiceIncidentRepository;
import com.management.managementapi.exeption.BusinessException;
import com.management.managementapi.exeption.ResourceNotFoundException;
import com.management.managementapi.repository.ProfileRepository;
import com.management.managementapi.security.AuthContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceIncidentServiceTest {

    @Mock private InvoiceIncidentRepository repository;
    @Mock private ConstructionInvoiceRepository invoiceRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private AuthContext authContext;

    @InjectMocks private InvoiceIncidentService service;

    private static final UUID ME = UUID.randomUUID();

    private ConstructionInvoice invoice(UUID id) {
        ConstructionInvoice inv = new ConstructionInvoice();
        inv.setId(id);
        inv.setScope(ConstructionInvoice.Scope.PROJECT);
        Enterprise e = new Enterprise();
        e.setId(UUID.randomUUID());
        inv.setEnterprise(e);
        inv.setInvoiceNumber("FT 1");
        return inv;
    }

    @Test
    @DisplayName("create: com todas as faturas → grava, título e corpo aparados, autor do contexto")
    void createGrava() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(authContext.currentProfileId()).thenReturn(Optional.of(ME));
        when(invoiceRepository.findAllById(any())).thenReturn(List.of(invoice(a), invoice(b)));
        when(repository.save(any(InvoiceIncident.class))).thenAnswer(c -> c.getArgument(0));

        service.create(new InvoiceIncidentCreateDTO("  Falta a NC  ", "  corpo  ", List.of(a, b)));

        ArgumentCaptor<InvoiceIncident> saved = ArgumentCaptor.forClass(InvoiceIncident.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Falta a NC");
        assertThat(saved.getValue().getBody()).isEqualTo("corpo");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ME);
        assertThat(saved.getValue().getInvoices()).hasSize(2);
    }

    @Test
    @DisplayName("create: uma fatura inexistente → INVOICE_035, nada gravado")
    void createFaturaInexistente() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(invoiceRepository.findAllById(any())).thenReturn(List.of(invoice(a))); // falta b

        assertThatThrownBy(() -> service.create(
                new InvoiceIncidentCreateDTO("x", "y", List.of(a, b))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_INCIDENT_INVOICE_NOT_FOUND);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("get: id desconhecido → INVOICE_034")
    void getDesconhecido() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVOICE_INCIDENT_NOT_FOUND);
    }

    @Test
    @DisplayName("resolve: marca resolvedAt e quem; um segundo resolve não mexe na data")
    void resolveÉIdempotente() {
        UUID id = UUID.randomUUID();
        InvoiceIncident incident = new InvoiceIncident();
        incident.setId(id);
        incident.setTitle("t");
        incident.setBody("b");
        when(repository.findById(id)).thenReturn(Optional.of(incident));
        when(authContext.currentProfileId()).thenReturn(Optional.of(ME));
        when(repository.save(any(InvoiceIncident.class))).thenAnswer(c -> c.getArgument(0));

        InvoiceIncidentResponseDTO first = service.resolve(id);
        assertThat(first.resolvedAt()).isNotNull();
        assertThat(first.resolvedBy()).isEqualTo(ME);

        OffsetDateTime when = incident.getResolvedAt();
        service.resolve(id);
        assertThat(incident.getResolvedAt()).isEqualTo(when);
        verify(repository).save(any()); // só uma vez
    }
}
