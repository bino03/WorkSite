package com.management.managementapi.exeption;

import com.management.managementapi.dto.error.ErrorCode;
import lombok.Getter;

/**
 * Exceção lançada quando um utilizador não tem permissão para aceder a um recurso.
 * Retorna HTTP 403 Forbidden.
 */
@Getter
public class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage) {
        super(errorCode, customMessage);
    }

    public ForbiddenException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }

    public ForbiddenException(ErrorCode errorCode, String customMessage, Throwable cause) {
        super(errorCode, customMessage, cause);
    }
}
