# scripts/

Ferramentas de manutenção que se correm à mão. Não fazem parte do build.

## Apagar faturas de obra

Uma fatura vive em **dois sítios**: o ficheiro no Supabase Storage (documento +
miniatura, bucket `documents`, chave `construction-invoices/{enterpriseId}/…`) e
a linha em `worksite.construction_invoice`. Apagar só um dos lados deixa lixo:
linhas a apontar para ficheiros que já não existem, ou ficheiros que ninguém
mais consegue alcançar.

| Ficheiro | Trata de |
|---|---|
| `purge-invoices.mjs` | os ficheiros de faturas no Storage |
| `purge-invoices.sql` | as linhas na tabela |
| `purge-storage.mjs` | qualquer bucket, ou todos (ver secção própria) |

### Ordem

**Storage primeiro, tabela depois.** Se apagares as linhas primeiro perdes a
lista de chaves — o script contorna isso listando a partir do próprio Storage,
mas a ordem certa continua a ser esta, porque entre um passo e o outro a app
mostra faturas cujo ficheiro já não abre (mais fácil de explicar do que o
contrário).

```bash
cd management/managementapi

node scripts/purge-invoices.mjs                 # simulação: diz o que apagaria
node scripts/purge-invoices.mjs --yes           # apaga mesmo
```

Depois cola o bloco 3 de `purge-invoices.sql` no SQL editor do Supabase.

Só um projeto:

```bash
node scripts/purge-invoices.mjs --enterprise <uuid> --yes
```

…e o `where enterprise_id = …` no SQL.

### O que isto mexe no orçamento

`construction_expense.invoice_id` tem `ON DELETE CASCADE`
(`V16__construction_invoice.sql`). Apagar uma fatura **apaga o lançamento que
dela nasceu** — o gasto sai do orçamento e as rubricas afetadas passam a mostrar
menos despesa. As despesas lançadas à mão (`invoice_id IS NULL`) não são
tocadas.

O passo 1 do SQL diz-te, antes de apagares, quantos lançamentos vão atrás e que
valor sai do orçamento. Corre-o primeiro.

## Esvaziar o Storage

Duas formas, e a diferença entre elas não é de gosto.

### `delete from storage.objects` não funciona

A Supabase bloqueia com um trigger:

```
ERROR: Direct deletion from storage tables is not allowed.
       Use the Storage API instead.        (storage.protect_delete)
```

E faz bem: apagar a linha removia os metadados mas deixava os bytes no backend
S3, órfãos e a contar para a quota. **Qualquer caminho tem de passar pela
Storage API** — a única escolha é de onde a chamas.

### `purge-storage.mjs` — o caminho simples

```bash
node scripts/purge-storage.mjs --list                    # o que há, e quanto
node scripts/purge-storage.mjs --bucket documents --yes
node scripts/purge-storage.mjs --all --yes               # esvazia TUDO
```

Sem dependências, sem extensões, lê o `.env`, simulação por omissão. **Esvazia,
não remove** os buckets: removê-los partia os uploads da app, que assumem que já
existem.

Depois corre o passo 3 de `purge-storage.sql` para as referências.

### `purge-storage.sql` — se tiver mesmo de ser no SQL editor

Faz a mesma chamada à Storage API, mas de dentro do Postgres, através da
extensão `http`: lê os nomes de `storage.objects` (ler é permitido) e envia-os
para a API em lotes de 100.

Preço a pagar: precisa de `create extension http`, e a service_role key fica no
texto da query — logo, no histórico do SQL editor. Num projeto teu não é grave;
não partilhes o screenshot.

### Só um bucket se auto-limpa

Apagar ficheiros não apaga as linhas que apontam para eles, e só as faturas têm
cascade. Por isso é que o `.sql` trata dos dois lados de uma vez:

| Bucket | Quem aponta para lá | Se apagares só os ficheiros |
|---|---|---|
| `documents` | `worksite.construction_invoice` (`bucket` + `storage_key` + `thumbnail_key`) | faturas a apontar para o vazio |
| `media` | `worksite.enterprises_media` (uma linha por banner/foto/vídeo) | galeria e banner partidos |
| `private` | `worksite.profile.photo_bucket` + `photo_key` | avatares partidos |

Não há erro visível imediato: as URLs assinadas continuam a ser geradas, só que
não há nada do outro lado. O passo 5 do `.sql` tem as queries para confirmar que
não ficou nada pendurado — dos dois lados.

### Requisitos

- Node 18+ (usa `fetch` nativo — sem dependências para instalar)
- `.env` em `management/managementapi/` com `SUPABASE_URL` e
  `SUPABASE_SERVICE_ROLE_KEY`

O script usa a **service role key**, que ignora RLS. Não o corras contra
produção sem teres a certeza do prefixo que lhe deste.
