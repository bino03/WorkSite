// src/hooks/useErrorHandler.ts
import { useCallback } from 'react';
import { notificationService } from '@/services/general/notificationService';
import { parseApiError } from '@/utils/apiError';
import type { ApiErrorResponse } from '@/errors/error.types';

export interface UseErrorHandlerOptions {
  /** Suprime a notificação automática */
  silent?: boolean;
}

export function useErrorHandler() {
  /**
   * Mostra a notificação adequada para um ApiErrorResponse já parseado.
   */
  const showApiError = useCallback(
    (apiError: ApiErrorResponse, options?: UseErrorHandlerOptions) => {
      if (options?.silent) return;

      if (apiError.errorCode === 'ERR_002' && apiError.fieldErrors?.length) {
        notificationService.validationError(apiError.fieldErrors);
        return;
      }

      if (apiError.status === 404) {
        notificationService.warning('Não encontrado', apiError.message);
        return;
      }

      if (apiError.status >= 500) {
        notificationService.error('Erro no servidor', apiError.message);
        return;
      }

      notificationService.error('Erro', apiError.message);
    },
    [],
  );

  /**
   * Recebe qualquer erro (Axios, Error, desconhecido), tenta fazer parse
   * e mostra a notificação. Retorna o ApiErrorResponse se disponível.
   */
  const handleError = useCallback(
    (err: unknown, options?: UseErrorHandlerOptions): ApiErrorResponse | null => {
      const apiError = parseApiError(err);

      if (!options?.silent) {
        if (apiError) {
          showApiError(apiError, options);
        } else if (err instanceof Error) {
          notificationService.error('Erro', err.message);
        } else {
          notificationService.error('Erro', 'Ocorreu um erro inesperado. Tente novamente.');
        }
      }

      return apiError;
    },
    [showApiError],
  );

  return { handleError, showApiError };
}
