package com.management.managementapi.exeption;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import com.management.managementapi.dto.error.ErrorResponseDTO;
import com.management.managementapi.dto.error.FieldErrorDTO;
import com.management.managementapi.dto.error.ErrorCode;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        
        log.error("Resource not found: {}", ex.getMessage());
        
        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.NOT_FOUND.value(),
            "Not Found",
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode().getCode()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponseDTO> handleForbiddenException(
            ForbiddenException ex,
            HttpServletRequest request) {

        log.error("Forbidden error: {}", ex.getMessage());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode().getCode()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ErrorResponseDTO> handleFileUploadException(
            FileUploadException ex,
            HttpServletRequest request) {

        if (ex.getErrorCode() == ErrorCode.FILE_SIZE_EXCEEDED
                || ex.getErrorCode() == ErrorCode.IMAGE_SIZE_EXCEEDED
                || ex.getErrorCode() == ErrorCode.VIDEO_SIZE_EXCEEDED) {
            log.warn("File too large for '{}': {}", request.getRequestURI(), ex.getMessage());
            ErrorResponseDTO error = new ErrorResponseDTO(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Payload Too Large",
                ex.getMessage(),
                request.getRequestURI(),
                ex.getErrorCode().getCode()
            );
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
        }

        log.warn("File upload error for '{}': {}", request.getRequestURI(), ex.getMessage());
        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode().getCode()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessException(
            BusinessException ex,
            HttpServletRequest request) {

        log.error("Business error: {}", ex.getMessage());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode().getCode()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        log.error("Validation error: {}", ex.getMessage());
        
        List<FieldErrorDTO> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new FieldErrorDTO(
                error.getField(),
                error.getDefaultMessage()
            ))
            .collect(Collectors.toList());
        
        ErrorResponseDTO error = new ErrorResponseDTO();
        error.setTimestamp(java.time.LocalDateTime.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Validation Error");
        error.setMessage("Erro de validação nos campos");
        error.setPath(request.getRequestURI());
        error.setErrorCode(ErrorCode.VALIDATION_ERROR.getCode());
        error.setFieldErrors(fieldErrors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        
        log.error("Database constraint violation: {}", ex.getMessage());

        // O índice único do ATCUD (V17) é a rede que apanha o que o SELECT do
        // serviço não vê: dois carregamentos do mesmo documento em paralelo,
        // cada um em sua transação. Chegar aqui é raro, mas quem está do outro
        // lado merece a mesma frase que teria no caminho normal, e não
        // "violação de constraint".
        boolean duplicateAtcud = String.valueOf(ex.getMessage())
                .contains("uq_invoice_enterprise_atcud");

        ErrorCode code = duplicateAtcud
                ? ErrorCode.INVOICE_DUPLICATE_ATCUD
                : ErrorCode.DATABASE_CONSTRAINT_VIOLATION;

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.CONFLICT.value(),
            "Conflict",
            duplicateAtcud
                ? code.getDefaultMessage()
                : "Violação de constraint na base de dados",
            request.getRequestURI(),
            code.getCode()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }
    
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ErrorResponseDTO> handleStorageException(
            StorageException ex,
            HttpServletRequest request) {

        // Passar `ex` e não só a mensagem: a causa traz o HTTP e o corpo da
        // resposta do Supabase ("Bucket not found", "Payload too large", …).
        // Sem ela o log diz apenas que falhou, e não porquê.
        log.error("Storage error: {}", ex.getMessage(), ex);

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.BAD_GATEWAY.value(),
            "Bad Gateway",
            ex.getMessage(),
            request.getRequestURI(),
            ex.getErrorCode().getCode()
        );

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied for '{}': {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            "Não tens permissão para aceder a este recurso",
            request.getRequestURI(),
            ErrorCode.ACCESS_DENIED.getCode()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        log.error("Constraint violation: {}", ex.getMessage());

        List<FieldErrorDTO> fieldErrors = ex.getConstraintViolations().stream()
            .map(v -> new FieldErrorDTO(
                v.getPropertyPath().toString(),
                v.getMessage()
            ))
            .collect(Collectors.toList());

        ErrorResponseDTO error = new ErrorResponseDTO();
        error.setTimestamp(java.time.LocalDateTime.now());
        error.setStatus(HttpStatus.BAD_REQUEST.value());
        error.setError("Validation Error");
        error.setMessage("Erro de validação nos parâmetros");
        error.setPath(request.getRequestURI());
        error.setErrorCode(ErrorCode.VALIDATION_ERROR.getCode());
        error.setFieldErrors(fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpServletRequest request) {

        log.warn("Unsupported media type '{}' for '{}'", ex.getContentType(), request.getRequestURI());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
            "Unsupported Media Type",
            "Content-Type '" + ex.getContentType() + "' não é suportado",
            request.getRequestURI(),
            ErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode()
        );

        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request) {

        log.warn("Upload size exceeded for '{}': {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.PAYLOAD_TOO_LARGE.value(),
            "Payload Too Large",
            "O ficheiro excede o tamanho máximo permitido (25 MB)",
            request.getRequestURI(),
            ErrorCode.FILE_SIZE_EXCEEDED.getCode()
        );

        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingRequestParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing required parameter '{}' for '{}'", ex.getParameterName(), request.getRequestURI());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Parâmetro obrigatório em falta: '" + ex.getParameterName() + "'",
            request.getRequestURI(),
            ErrorCode.BAD_REQUEST.getCode()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Unreadable request body for '{}': {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "O corpo do pedido é inválido ou está mal formatado",
            request.getRequestURI(),
            ErrorCode.BAD_REQUEST.getCode()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        log.error("Unexpected error: ", ex);
        
        ErrorResponseDTO error = new ErrorResponseDTO(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Ocorreu um erro inesperado. Por favor tente novamente.",
            request.getRequestURI(),
            ErrorCode.INTERNAL_SERVER_ERROR.getCode()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}