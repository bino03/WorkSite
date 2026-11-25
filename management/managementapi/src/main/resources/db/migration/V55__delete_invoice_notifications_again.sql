-- Repete a V51. A remoção dos avisos (1aa0ab7) ficou só no dev até 2026-11-25, e a app online,
-- ainda com o código antigo, voltou a criar avisos "Fatura a vencer" e "Fatura por classificar"
-- depois da V51 os apagar. Desde a release v19 nada os cria; isto apaga os que ficaram.
DELETE FROM worksite.notification
WHERE type IN ('invoice_due', 'invoice_pending');
