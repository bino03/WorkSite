-- =============================================================
-- V34__email_provider_password_text.sql
-- A password SMTP passa a ser guardada cifrada (AES-256-GCM) pelo
-- EncryptedStringConverter. O valor cifrado é `gcm:<base64 iv>:<base64 ct+tag>`,
-- que não cabe nos VARCHAR(255) originais da V7. Alarga para `text`.
--
-- A cifra dos valores já existentes é feita pelo backend ao arrancar
-- (EmailProviderPasswordReEncryptRunner) — não aqui, porque a chave só existe
-- no processo da app, nunca na migração.
-- =============================================================

alter table settings.email_providers
    alter column password type text;
