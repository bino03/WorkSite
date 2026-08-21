/**
 * Provedores SMTP — espelha `dto/email` do backend (`settings.email_providers`).
 *
 * A password nunca vem do servidor: só `hasPassword` diz se já existe alguma
 * gravada. Enviá-la vazia numa edição significa "mantém a que está".
 */

export interface EmailProvider {
  id: string;
  providerName: string;
  host: string;
  port: number;
  username: string;
  fromEmail: string;
  fromName: string | null;
  encryption: EmailEncryption;
  /** O provedor usado nos emails do produto. Só um o pode ser. */
  isDefault: boolean;
  isActive: boolean;
  hasPassword: boolean;
}

export type EmailEncryption = "tls" | "ssl" | "none";

export interface EmailProviderUpsert {
  providerName: string;
  host: string;
  port: number;
  username: string;
  /** Obrigatória na criação; vazia na edição mantém a atual. */
  password?: string | null;
  fromEmail: string;
  fromName?: string | null;
  encryption: EmailEncryption;
  isDefault?: boolean;
  isActive?: boolean;
}
