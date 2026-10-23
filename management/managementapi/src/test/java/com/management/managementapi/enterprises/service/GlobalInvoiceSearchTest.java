package com.management.managementapi.enterprises.service;

import com.management.managementapi.enterprises.dto.invoice.response.InvoiceSearchResultDTO;
import com.management.managementapi.enterprises.model.ConstructionInvoice;
import com.management.managementapi.enterprises.model.Enterprise;
import com.management.managementapi.enterprises.repository.ConstructionInvoiceRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pesquisa global de faturas (o modal Ctrl+K, 2026-10-22): o texto chega à
 * query aparado, um texto curto não chega lá, e um texto que se lê como valor
 * vai também como valor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalInvoiceSearchTest {

    @Mock private ConstructionInvoiceRepository repository;

    @InjectMocks private ConstructionInvoiceService service;

    @Test
    @DisplayName("menos de 2 caracteres não pesquisa")
    void shortTextReturnsNothing() {
        assertThat(service.searchEverywhere(" a ", 20)).isEmpty();
        assertThat(service.searchEverywhere(null, 20)).isEmpty();
        verify(repository, never()).searchEverywhere(anyString(), any(), any());
    }

    @Test
    @DisplayName("o texto vai aparado, sem valor quando não é número, com o limite pedido")
    void textIsTrimmedAndLimited() {
        when(repository.searchEverywhere(eq("brivel"), isNull(), any())).thenReturn(List.of());

        service.searchEverywhere("  brivel ", 7);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).searchEverywhere(eq("brivel"), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    @DisplayName("um valor à portuguesa vai também como valor")
    void amountTextIsAlsoSearchedAsAmount() {
        service.searchEverywhere("1 234,56 €", 20);

        verify(repository).searchEverywhere(eq("1 234,56 €"), eq(new BigDecimal("1234.56")), any());
    }

    @Test
    @DisplayName("formatos de valor aceites e recusados")
    void parsesAmounts() {
        assertThat(ConstructionInvoiceService.parseSearchAmount("1.234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1234,56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1234.56")).isEqualByComparingTo("1234.56");
        assertThat(ConstructionInvoiceService.parseSearchAmount("24,9")).isEqualByComparingTo("24.90");
        assertThat(ConstructionInvoiceService.parseSearchAmount("1222")).isEqualByComparingTo("1222");
        assertThat(ConstructionInvoiceService.parseSearchAmount("20263/1222")).isNull();
        assertThat(ConstructionInvoiceService.parseSearchAmount("FT 1222")).isNull();
        assertThat(ConstructionInvoiceService.parseSearchAmount("1,2345")).isNull();
    }

    @Test
    @DisplayName("a linha traz o nome da obra, e nada de obra na empresa")
    void mapsEnterpriseName() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId(UUID.randomUUID());
        enterprise.setName("Vila Petrus");

        ConstructionInvoice project = invoice(ConstructionInvoice.Scope.PROJECT, enterprise);
        ConstructionInvoice company = invoice(ConstructionInvoice.Scope.COMPANY, null);
        company.setDocumentType(ConstructionInvoice.DocumentType.CREDIT_NOTE);
        when(repository.searchEverywhere(anyString(), any(), any())).thenReturn(List.of(project, company));

        List<InvoiceSearchResultDTO> rows = service.searchEverywhere("ft", 20);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).enterpriseId()).isEqualTo(enterprise.getId());
        assertThat(rows.get(0).enterpriseName()).isEqualTo("Vila Petrus");
        assertThat(rows.get(0).scope()).isEqualTo("PROJECT");
        assertThat(rows.get(1).enterpriseName()).isNull();
        assertThat(rows.get(1).documentType()).isEqualTo("CREDIT_NOTE");
    }

    private static ConstructionInvoice invoice(ConstructionInvoice.Scope scope, Enterprise enterprise) {
        ConstructionInvoice invoice = new ConstructionInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setScope(scope);
        invoice.setEnterprise(enterprise);
        invoice.setInvoiceNumber("FT 1");
        invoice.setInvoiceDate(LocalDate.of(2026, 10, 1));
        invoice.setTotalAmount(new BigDecimal("10.00"));
        return invoice;
    }
}
