// src/utils/errorHandler.ts
import { AxiosError } from 'axios';
import type { ErrorResponse, ErrorConfig } from '@/errors/error.types';
import { getUserFriendlyMessage } from '@/errors/errorMessages';
import { notificationService } from '../services/general/notificationService';

/**
 * Marca posta no próprio objeto de erro por quem o tratou.
 *
 * O interceptor global do `api.ts` corre **antes** do `catch` do componente, e
 * durante muito tempo notificou sempre — resultado: cada erro aparecia a
 * dobrar, a mensagem PT do componente e a mensagem crua (em inglês) do
 * backend. Agora o interceptor adia a sua notificação um tick e só a mostra se
 * ninguém tiver reclamado o erro entretanto. Continua a servir de rede de
 * segurança para os `catch` que não chamam o `ErrorHandler`, sem duplicar os
 * que chamam.
 */
const NOTIFIED = Symbol.for("worksite.errorHandled");

/** Diz que este erro já tem dono — o interceptor global não lhe toca. */
export function markErrorHandled(error: unknown): void {
  if (error && typeof error === "object") {
    (error as Record<symbol, unknown>)[NOTIFIED] = true;
  }
}

export function wasErrorHandled(error: unknown): boolean {
  return !!(error && typeof error === "object" && (error as Record<symbol, unknown>)[NOTIFIED]);
}

export class ErrorHandler {
  static handle(error: unknown, config: ErrorConfig = {}) {
    // Marca-se sempre, mesmo com `showNotification: false`: chamar o handler é
    // assumir a responsabilidade pelo erro, incluindo a de o calar.
    markErrorHandled(error);

    const {
      showNotification = true,
      notificationType = 'error',
      customMessage,
      logToConsole = true,
    } = config;

    // Log no console (útil para debug)
    if (logToConsole && process.env.NODE_ENV === 'development') {
      console.error('Error caught:', error);
    }

    // Se for um erro do Axios
    if (this.isAxiosError(error)) {
      const errorResponse = error.response?.data as ErrorResponse;
      
      // Erros de validação (múltiplos campos)
      if (errorResponse?.fieldErrors && errorResponse.fieldErrors.length > 0) {
        if (showNotification) {
          notificationService.validationError(errorResponse.fieldErrors);
        }
        return errorResponse;
      }

      // Outros erros com errorCode
      const message = customMessage || 
                     getUserFriendlyMessage(errorResponse?.errorCode) ||
                     errorResponse?.message ||
                     'Ocorreu um erro inesperado.';

      if (showNotification) {
        notificationService[notificationType](
          'Erro',
          message
        );
      }

      return errorResponse;
    }

    // Erro genérico (não é do Axios)
    const message = customMessage || 'Ocorreu um erro inesperado.';
    if (showNotification) {
      notificationService[notificationType]('Erro', message);
    }

    return null;
  }

  private static isAxiosError(error: unknown): error is AxiosError {
    return (error as AxiosError).isAxiosError === true;
  }

  // Helper para extrair mensagem do erro
  static getMessage(error: unknown): string {
    if (this.isAxiosError(error)) {
      const errorResponse = error.response?.data as ErrorResponse;
      return getUserFriendlyMessage(errorResponse?.errorCode) ||
             errorResponse?.message ||
             'Ocorreu um erro inesperado.';
    }
    
    if (error instanceof Error) {
      return error.message;
    }

    return 'Ocorreu um erro inesperado.';
  }
}