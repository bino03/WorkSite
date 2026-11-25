-- Os avisos "Fatura a vencer" e "Fatura por classificar" deixaram de existir (spam).
-- Apaga os que ficaram na caixa de entrada.
DELETE FROM worksite.notification
WHERE type IN ('invoice_due', 'invoice_pending');
