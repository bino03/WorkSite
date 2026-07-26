// src/utils/errorHandler.ts
import { AxiosError } from 'axios';
import type { ErrorResponse, ErrorConfig } from '@/errors/error.types';
import { getUserFriendlyMessage } from '@/errors/errorMessages';
import { notificationService } from '../services/general/notificationService';

export class ErrorHandler {
  static handle(error: unknown, config: ErrorConfig = {}) {
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