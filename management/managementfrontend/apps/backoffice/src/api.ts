// src/api.ts
import axios from "axios";
import { notificationService } from "@/services/general/notificationService";
import type { ApiErrorResponse } from "@/errors/error.types";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL, // ex.: "http://localhost:8080"
  withCredentials: true, // ✅ CRITICAL: Automatically send cookies with every request
});

let isRefreshing = false;
let failedQueue: Array<{ resolve: () => void; reject: (err: unknown) => void }> = [];

function processQueue(error: unknown) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) reject(error);
    else resolve();
  });
  failedQueue = [];
}

function clearSessionAndRedirect() {
  // Show session expired notification before redirecting
  notificationService.warning('Sessão expirada', 'A sua sessão foi expirada. Por favor, faça login novamente.');

  // Small delay to allow user to see the notification
  setTimeout(() => {
    window.location.href = '/login';
  }, 1500);
}

function isFormData(data: unknown): data is FormData {
  return typeof FormData !== "undefined" && data instanceof FormData;
}

function dumpFormData(fd: FormData) {
  const out: Record<string, unknown>[] = [];
  for (const [k, v] of fd.entries()) {
    if (v instanceof File) {
      out.push({ field: k, type: "file", name: v.name, size: v.size, mime: v.type });
    } else {
      out.push({ field: k, type: "text", valuePreview: String(v).slice(0, 120), contentType: (v as Record<string, unknown>)?.type });
    }
  }
  return out;
}

// INTERCEPTOR DE REQUEST (FormData cleanup + blob Accept header)
api.interceptors.request.use((config) => {
  // LOGGING
  console.log('📤 Axios Request:', {
    method: config.method?.toUpperCase(),
    url: config.url,
    baseURL: config.baseURL,
    withCredentials: config.withCredentials,
    headers: config.headers,
  });

  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    if (config.headers) {
      // Remove Content-Type para FormData — deixar o browser definir como multipart/form-data com boundary
      // Usar o método .delete() da AxiosHeaders (case-insensitive) em vez do operador delete do JS
      config.headers.delete('Content-Type');
      console.log('✅ [Axios] FormData detectado - Content-Type removido para multipart/form-data');
    }
  }

  if (config.responseType === 'blob') {
    if (config.headers) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (config.headers as any).set?.('Accept', 'application/octet-stream, application/pdf, */*');
    }
  }

  return config;
});

// INTERCEPTOR DE RESPONSE — token refresh em 401 (com refresh_token cookie)
api.interceptors.response.use(
  (response) => {
    // LOGGING
    console.log('📥 Axios Response:', {
      status: response.status,
      statusText: response.statusText,
      url: response.config.url,
      data: response.data,
    });
    return response;
  },
  async (error) => {
    // LOGGING
    console.error('📥 Axios Response Error:', {
      status: error.response?.status,
      statusText: error.response?.statusText,
      url: error.config?.url,
      data: error.response?.data,
    });
    const originalRequest = error.config as any;

    // Skip refresh for auth check requests (noRefreshRetry flag)
    if (originalRequest.noRefreshRetry) {
      return Promise.reject(error);
    }

    // Only refresh on 401, and only once per request
    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    // If already refreshing, queue this request
    if (isRefreshing) {
      return new Promise<void>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      })
        .then(() => {
          // Retry with new token in cookie (automatically sent by browser)
          return api(originalRequest);
        })
        .catch((err) => Promise.reject(err));
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      // Call /api/auth/refresh
      // Backend reads refresh_token from cookie, returns new access_token in Set-Cookie header
      await api.post('/auth/refresh');

      // Signal AuthContext to fetch fresh user info (signed photoUrl expires every hour)
      window.dispatchEvent(new CustomEvent('auth:refresh-success'));

      // Reprocess all queued requests (they'll use the new access_token cookie)
      processQueue(null);

      // Retry original request with new token
      return api(originalRequest);
    } catch (err) {
      processQueue(err);
      clearSessionAndRedirect();
      return Promise.reject(err);
    } finally {
      isRefreshing = false;
    }
  }
);

// INTERCEPTOR GLOBAL DE ERROS — notificações automáticas
api.interceptors.response.use(
  (response) => response,
  (error) => {
    // 401 é tratado pelo interceptor de refresh acima — não duplicar
    if (error.response?.status === 401) {
      return Promise.reject(error);
    }

    const apiError = error?.response?.data as ApiErrorResponse | undefined;

    if (apiError?.errorCode === 'ERR_002' && apiError.fieldErrors?.length) {
      // Erros de validação com campos específicos
      notificationService.validationError(apiError.fieldErrors);
    } else if (apiError?.message) {
      notificationService.error('Erro', apiError.message);
    } else if (error.request && !error.response) {
      // Sem resposta do servidor (rede)
      notificationService.error('Sem ligação', 'Não foi possível contactar o servidor. Verifique a sua ligação.');
    } else {
      notificationService.error('Erro', 'Ocorreu um erro inesperado. Tente novamente.');
    }

    return Promise.reject(error);
  }
);

// Apenas logging em desenvolvimento (comentado para evitar spam)
if (import.meta.env.DEV && false) {
  api.interceptors.response.use(
    (res) => {
      // Não logar dados binários para downloads
      if (res.config.responseType === 'blob') {
        console.groupCollapsed(
          `[API:RESPONSE] ${res.config.method?.toUpperCase()} ${res.config.baseURL ?? ""}${res.config.url} → ${res.status} (BLOB)`
        );
        console.log("Headers:", res.headers);
        console.log("Data: [Blob data]", res.data);
        console.groupEnd();
      } else {
        console.groupCollapsed(
          `[API:RESPONSE] ${res.config.method?.toUpperCase()} ${res.config.baseURL ?? ""}${res.config.url} → ${res.status}`
        );
        console.log("Headers:", res.headers);
        console.log("Data:", res.data);
        console.groupEnd();
      }
      return res;
    },
    (err) => {
      const res = err.response;
      console.groupCollapsed(
        `[API:ERROR] ${err.config?.method?.toUpperCase()} ${err.config?.baseURL ?? ""}${err.config?.url} → ${res?.status}`
      );
      console.log("Request headers:", err.config?.headers);
      if (isFormData(err.config?.data)) {
        console.log("Request body (FormData):", dumpFormData(err.config.data));
      } else {
        console.log("Request body:", err.config?.data);
      }

      // Log específico para erros em downloads - SEM await
      if (err.config?.responseType === 'blob' && res?.data) {
        // Para blobs de erro, vamos apenas indicar que é um blob
        console.log("Error response: [Blob data - use handleDownload for details]");
      } else {
        console.log("Response data:", res?.data);
      }
      console.groupEnd();
      return Promise.reject(err);
    }
  );
}

export default api;