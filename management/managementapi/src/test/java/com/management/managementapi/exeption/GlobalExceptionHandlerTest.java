package com.management.managementapi.exeption;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.error.ErrorResponseDTO;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Uma URL que não casa com nenhum handler nem recurso estático tem de dar 404, e não
 * 500 {@code ERR_001}. Sem o {@code @ExceptionHandler(NoResourceFoundException.class)},
 * a exceção propagava-se até ao ramo genérico {@code handleGenericException} — e um 500
 * numa rota errada engana quem está a depurar.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("rota inexistente → 404 RESOURCE_NOT_FOUND (ERR_003), não 500")
    void rotaInexistenteDa404() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/rota-que-nao-existe");

        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "rota-que-nao-existe");

        ResponseEntity<ErrorResponseDTO> response = handler.handleNoResourceFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
        assertThat(response.getBody().getErrorCode()).isNotEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getBody().getPath()).isEqualTo("/rota-que-nao-existe");
    }

    // Os índices únicos de fatura são globais desde a V29 (ATCUD, NIF+número) e a
    // V24 (checksum). O handler mapeava os nomes antigos por projeto (V17/V18),
    // largados há muito — qualquer colisão concorrente saía como DB_003 genérico.

    @Test
    @DisplayName("unique_violation em uq_invoice_atcud → 409 INVOICE_010")
    void atcudIndexMapsToInvoice010() {
        assertThat(codeFor("uq_invoice_atcud")).isEqualTo(ErrorCode.INVOICE_DUPLICATE_ATCUD.getCode());
    }

    @Test
    @DisplayName("unique_violation em uq_invoice_nif_number → 409 INVOICE_011")
    void nifNumberIndexMapsToInvoice011() {
        assertThat(codeFor("uq_invoice_nif_number")).isEqualTo(ErrorCode.INVOICE_DUPLICATE_DOCUMENT.getCode());
    }

    @Test
    @DisplayName("unique_violation em uq_invoice_document_checksum → 409 INVOICE_012")
    void checksumIndexMapsToInvoice012() {
        assertThat(codeFor("uq_invoice_document_checksum")).isEqualTo(ErrorCode.INVOICE_DUPLICATE_FILE.getCode());
    }

    @Test
    @DisplayName("índice desconhecido → 409 DB_003 genérico")
    void unknownIndexFallsBackToDb003() {
        assertThat(codeFor("uq_qualquer_outra_coisa")).isEqualTo(ErrorCode.DATABASE_CONSTRAINT_VIOLATION.getCode());
    }

    private String codeFor(String indexName) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/construction-invoices");

        // A frase é a do driver do Postgres, com o nome do índice no meio.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \""
                        + indexName + "\"]");

        ResponseEntity<ErrorResponseDTO> response = handler.handleDataIntegrityViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getErrorCode();
    }
}
