package com.management.managementapi.exeption;

import com.management.managementapi.dto.error.ErrorCode;
import com.management.managementapi.dto.error.ErrorResponseDTO;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
