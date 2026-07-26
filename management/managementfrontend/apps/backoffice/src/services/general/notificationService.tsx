// src/services/notificationService.ts
import { notification } from 'antd';
import type { NotificationPlacement } from 'antd/es/notification/interface';

type NotificationType = 'success' | 'info' | 'warning' | 'error';

interface NotificationConfig {
  message: string;
  description?: string;
  duration?: number;
  placement?: NotificationPlacement;
}

class NotificationService {
  private defaultDuration = 4.5;
  private defaultPlacement: NotificationPlacement = 'topRight';

  show(
    type: NotificationType,
    config: NotificationConfig
  ) {
    notification[type]({
      message: config.message,
      description: config.description,
      duration: config.duration ?? this.defaultDuration,
      placement: config.placement ?? this.defaultPlacement,
    });
  }

  success(message: string, description?: string) {
    this.show('success', { message, description });
  }

  error(message: string, description?: string) {
    this.show('error', { message, description });
  }

  warning(message: string, description?: string) {
    this.show('warning', { message, description });
  }

  info(message: string, description?: string) {
    this.show('info', { message, description });
  }

  // Para erros de validação com múltiplos campos
  validationError(fieldErrors: Array<{ field: string; message: string }>) {
    const description = (
      <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
        {fieldErrors.map((error, index) => (
          <li key={index}>
            <strong>{error.field}:</strong> {error.message}
          </li>
        ))}
      </ul>
    );

    notification.error({
      message: 'Erro de Validação',
      description,
      duration: 6,
      placement: this.defaultPlacement,
    });
  }
}

export const notificationService = new NotificationService();