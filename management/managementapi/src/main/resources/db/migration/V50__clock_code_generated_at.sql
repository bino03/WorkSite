-- Quando foi gerado o QR atual de cada obra (pedido do utilizador a 2026-11-20).
--
-- Não é o `created_at`: "Gerar novo QR" substitui o código na mesma linha, por isso
-- o `created_at` diz quando a obra teve o primeiro QR, não quando foi gerado o que
-- está colado na obra. Nem o `updated_at`, que muda com qualquer alteração à linha.
-- O backend escreve esta coluna sempre que gera um código.
alter table attendance.enterprise_clock_code
    add column if not exists generated_at timestamptz not null default now();

-- As linhas que já existem foram geradas quando foram criadas (ainda não houve
-- nenhum "Gerar novo" — a V49 é do mesmo dia).
update attendance.enterprise_clock_code set generated_at = created_at;
