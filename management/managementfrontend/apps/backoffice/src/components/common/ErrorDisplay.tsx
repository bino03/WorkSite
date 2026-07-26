// src/components/common/ErrorDisplay.tsx
import React from 'react';
import { Result, Button } from 'antd';
import type { AppError } from '@/errors/error.types';
import { ErrorCategory } from '@/errors/error.types';

type ResultStatus = '404' | '403' | '500' | 'warning' | 'error' | 'info' | 'success';

function getResultStatus(category: ErrorCategory): ResultStatus {
  switch (category) {
    case ErrorCategory.NOT_FOUND:
      return '404';
    case ErrorCategory.PERMISSION:
      return '403';
    case ErrorCategory.SERVER:
    case ErrorCategory.DATABASE:
    case ErrorCategory.STORAGE:
      return '500';
    default:
      return 'warning';
  }
}

interface ErrorDisplayProps {
  error: AppError;
  onRetry?: () => void;
  onBack?: () => void;
}

export function ErrorDisplay({ error, onRetry, onBack }: ErrorDisplayProps) {
  const status = getResultStatus(error.category);

  const extra: React.ReactNode[] = [];
  if (onRetry) {
    extra.push(
      <Button key="retry" type="primary" onClick={onRetry}>
        Tentar novamente
      </Button>,
    );
  }
  if (onBack) {
    extra.push(
      <Button key="back" onClick={onBack}>
        Voltar
      </Button>,
    );
  }

  return (
    <Result
      status={status}
      title={error.userMessage}
      subTitle={
        import.meta.env.DEV
          ? `[${error.code}] ${error.message}`
          : undefined
      }
      extra={extra.length > 0 ? extra : undefined}
    />
  );
}
