# Verificação de importação de orçamento

Confronta o Excel do empreiteiro com a árvore de rubricas que ficou gravada, e reporta
**só o que não bate certo**. Serve para validar uma importação sem conferir 198 linhas à mão.

O `verificar-importacao.js` lê o `.xlsx` **por conta própria** — descomprime o ZIP e interpreta
o XML directamente, sem tocar no `BudgetExcelImportService`. É deliberado: reaproveitar o
parser do backend esconderia um erro dele. As duas leituras são independentes e só coincidem
se ambas estiverem certas.

## Instalar

```bash
cd management/managementapi/scripts/verificacao-orcamento
npm install
```

Fica contido nesta pasta — não mexe no `managementapi` nem no Backoffice.

## Usar

O `ler-arvore.js` faz apenas `SELECT` e vai buscar as credenciais ao `.env` do backend
(`management/managementapi/.env`). **Não precisa do servidor a correr.**

```bash
# 1. que projetos têm orçamento?
node ler-arvore.js

# 2. exportar a árvore de um deles
node ler-arvore.js <enterpriseId> [destino.json]     # por omissão: ./arvore.json

# 3. comparar
node verificar-importacao.js "caminho/para/orcamento.xlsx" arvore.json
```

## O que é verificado

| | |
|---|---|
| **Totais** | soma das folhas na BD contra a linha `TOTAL` do Excel, com tolerância de 1 € |
| **Índices** | quais estão no Excel e não ficaram gravados, e vice-versa |
| **Valores** | por índice: descrição, unidade, quantidade, preço unitário e preço total |
| **Linhas sem índice** | títulos, notas e alternativas — quais se perderam |
| **Alternativas** | as que têm preço, listadas e somadas |

## Resultados que parecem erros e não são

- **Índices repetidos.** O Excel de referência repete `8.2` e `13.2.1`. O importador guarda a
  primeira ocorrência com índice e a segunda sem — por isso o número de linhas sem índice na
  base de dados é maior do que no Excel, exactamente pelo número de repetições.
- **Diferença de cêntimos no total.** O Excel soma floats por arredondar e o importador
  arredonda cada célula a 2 casas. Abaixo de 1 € é normal.
- **Linhas ignoradas.** Uma linha sem índice, sem descrição e sem preço total não é uma
  rubrica — no ficheiro de referência é a linha 38, que tem uma anotação em texto na coluna do
  preço unitário.

## Relacionado

- [[../../../../docs/api.md]] — o endpoint de importação e o `dryRun`
- `BudgetExcelImportService` — o parser do backend, e a heurística de `ITEM`/`HEADING`/`NOTE`
