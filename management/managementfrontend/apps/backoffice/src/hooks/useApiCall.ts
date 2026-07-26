// src/hooks/useApiCall.ts
import { useState } from 'react';
import { ErrorHandler } from '@/errors/errorHandler';
import type { ErrorConfig } from '@/errors/error.types';

interface UseApiCallOptions<T> {
  onSuccess?: (data: T) => void;
  onError?: (error: unknown) => void;
  errorConfig?: ErrorConfig;
}

export function useApiCall<T = any>() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const execute = async (
    apiCall: () => Promise<T>,
    options: UseApiCallOptions<T> = {}
  ): Promise<T | null> => {
    setLoading(true);
    setError(null);

    try {
      const result = await apiCall();
      options.onSuccess?.(result);
      return result;
    } catch (err) {
      setError(err);
      ErrorHandler.handle(err, options.errorConfig);
      options.onError?.(err);
      return null;
    } finally {
      setLoading(false);
    }
  };

  return { execute, loading, error };
}