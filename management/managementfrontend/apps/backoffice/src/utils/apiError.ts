// src/utils/apiError.ts
import type { AxiosError } from 'axios';
import type { ApiErrorResponse, AppError } from '@/errors/error.types';
import { ErrorCategory, ErrorSeverity } from '@/errors/error.types';
import {
  getUserFriendlyMessage,
  getCategoryFromStatus,
  getSeverityFromCategory,
  getCategoryFromCode,
} from '@/errors/errorMessages';

/**
 * Extrai o ApiErrorResponse estruturado de qualquer erro.
 * Retorna null se a resposta não tiver o formato esperado do backend.
 */
export function parseApiError(err: unknown): ApiErrorResponse | null {
  const axiosErr = err as AxiosError<ApiErrorResponse>;
  const data = axiosErr?.response?.data;
  if (data && typeof data === 'object' && 'errorCode' in data) {
    return data as ApiErrorResponse;
  }
  return null;
}

/**
 * Extrai a mensagem legível de um erro (fallback para mensagem genérica).
 */
export function getErrorMessage(
  err: unknown,
  fallback = 'Ocorreu um erro. Tente novamente.',
): string {
  const apiError = parseApiError(err);
  if (apiError?.message) return apiError.message;
  if (err instanceof Error) return err.message;
  return fallback;
}

/**
 * Converte qualquer erro (Axios, Error, desconhecido) para AppError normalizado.
 */
export function toAppError(err: unknown): AppError {
  const axiosErr = err as AxiosError<ApiErrorResponse>;

  // Erro com resposta estruturada do backend
  if (axiosErr?.response?.data && typeof axiosErr.response.data === 'object' && 'errorCode' in axiosErr.response.data) {
    const data = axiosErr.response.data as ApiErrorResponse;
    const category = getCategoryFromCode(data.errorCode) ?? getCategoryFromStatus(data.status);
    const severity = getSeverityFromCategory(category);

    return {
      code: data.errorCode,
      message: data.message,
      userMessage: getUserFriendlyMessage(data.errorCode),
      status: data.status,
      timestamp: new Date(data.timestamp),
      path: data.path,
      category,
      severity,
      fieldErrors: data.fieldErrors ?? undefined,
    };
  }

  // Erro de rede (sem resposta)
  if (axiosErr?.request && !axiosErr?.response) {
    return {
      code: 'NETWORK_ERROR',
      message: 'Sem resposta do servidor',
      userMessage: 'Sem ligação ao servidor. Verifique a sua conexão e tente novamente.',
      status: 0,
      timestamp: new Date(),
      path: axiosErr.config?.url ?? '',
      category: ErrorCategory.SERVER,
      severity: ErrorSeverity.CRITICAL,
    };
  }

  // Erro HTTP sem formato estruturado
  if (axiosErr?.response) {
    const status = axiosErr.response.status;
    const category = getCategoryFromStatus(status);
    return {
      code: `HTTP_${status}`,
      message: `HTTP ${status}`,
      userMessage: getUserFriendlyMessage(status === 404 ? 'ERR_003' : 'ERR_001'),
      status,
      timestamp: new Date(),
      path: axiosErr.config?.url ?? '',
      category,
      severity: getSeverityFromCategory(category),
    };
  }

  // Erro genérico JavaScript
  if (err instanceof Error) {
    return {
      code: 'CLIENT_ERROR',
      message: err.message,
      userMessage: 'Ocorreu um erro inesperado. Por favor, tente novamente.',
      status: 0,
      timestamp: new Date(),
      path: '',
      category: ErrorCategory.SERVER,
      severity: ErrorSeverity.ERROR,
    };
  }

  // Fallback
  return {
    code: 'UNKNOWN_ERROR',
    message: 'Erro desconhecido',
    userMessage: 'Ocorreu um erro inesperado. Por favor, tente novamente.',
    status: 0,
    timestamp: new Date(),
    path: '',
    category: ErrorCategory.SERVER,
    severity: ErrorSeverity.ERROR,
  };
}

/** Type guard: verifica se um objeto já é um AppError normalizado */
export function isAppError(err: unknown): err is AppError {
  return (
    typeof err === 'object' &&
    err !== null &&
    'code' in err &&
    'userMessage' in err &&
    'category' in err &&
    'severity' in err
  );
}
