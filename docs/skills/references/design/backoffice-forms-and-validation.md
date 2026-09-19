# Backoffice — Formulários e Validação

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Baseado em `ConstructionStageUpsertDrawer.tsx`, `ConstructionSubStageUpsertDrawer.tsx`, `ConstructionExpenseUpsertDrawer.tsx`, `CreateEnterpriseDrawer.tsx` (+ `create/*Section.tsx`), `enterprise/edit/Edit*Card.tsx`, `CreateEmployeeDrawer.tsx`, `TaskFormDrawer.tsx`, `TaskDetailDrawer.tsx`, `MyProfileDrawer.tsx`, `ProfileView.tsx`, `AcceptInvitePage.tsx`. Auditoria 2026-08-05, ponto 1 atualizado a 2026-09-16.

O [[../../frontend/skill-frontend-design-system]] prescreve **React Hook Form + Zod** para todos os formulários ("Regras de base" nº 5). A auditoria mostra duas convenções a competir — e o corte não é "código antigo vs novo", é **por domínio**.

## 1. Biblioteca de formulário: RHF+Zod no domínio de construção/criação, AntD Form no resto

- **RHF + Zod** (`zodResolver`): `ConstructionStageUpsertDrawer.tsx:4,47-48`, `ConstructionSubStageUpsertDrawer.tsx:4,47-48`, `ConstructionExpenseUpsertDrawer.tsx:4,54-55`, `CreateEnterpriseDrawer.tsx:5,38-39`, e agora também `EditEnterpriseOverviewCard.tsx`, `EditDatesAndAreasCard.tsx`, `EditFinancialCard.tsx` (2026-09-16 — ver nota abaixo).
- **AntD `Form.useForm()`**: `CreateEmployeeDrawer.tsx:24`, `TaskFormDrawer.tsx:30`, `TaskDetailDrawer.tsx:24`, `ProfileView.tsx:76`, `MyProfileDrawer.tsx` (três formulários no mesmo drawer, um por separador), `AcceptInvitePage.tsx:20`.
- **Estado local próprio (`useState`), sem Form nenhum**: `EditEnterpriseLocationCard.tsx` e `EditEnterpriseGalleryCard.tsx` — de propósito, não é drift por preguiça (ver nota abaixo).

Só existem dois schemas Zod no projeto: `components/construction/constructionFormSchemas.ts` e `components/enterprise/create/enterpriseFormSchema.ts`.

> ✅ **Migrado a 2026-09-16**: os três `Edit*Card` de empreendimento que partilhavam campos 1:1 com a
> criação (Overview, Datas & Áreas, Financeiro) passaram a **reutilizar as próprias secções da
> criação** (`BasicInfoSection`, `TimelineMetricsSection`, `FinancialSection`) dentro de um
> `FormProvider` próprio, com `EnterpriseFormSchema.pick({...})` a recortar só os campos de cada
> card. Cada wrapper mapeia `camelCase` (DTO) ↔ `snake_case` (schema) à entrada e à saída — não há
> mapper partilhado, é explícito em cada ficheiro, como já era em `CreateEnterpriseDrawer.onSubmit`.
> Duas secções partilhadas ganharam um prop para a diferença de comportamento entre criar e editar:
> `BasicInfoSection({ showActiveToggle })` (o `PATCH .../overview` não tem `isActive` — é o
> ciclo de eliminação/restauro que o mantém) e `TimelineMetricsSection({ totalUnitsReadOnly })`
> (o `PATCH .../dates-areas` não aceita `totalUnits` — é derivado). Ambas continuam a funcionar sem
> a prop (o valor por omissão é o comportamento da criação).
>
> **Localização e Galeria ficaram de fora, por decisão, não por falta de tempo**: `EditEnterpriseLocationCard`
> fala com um endpoint próprio (`POST .../location/upsert`, forma diferente do `newLocation`/
> `existingLocationId` embutido no payload da criação) — meteu-se `MapLocationPickerDrawer` (a
> mesma da criação) com estado local em vez de RHF, porque a secção da criação está presa ao
> `EnterpriseFormSchema` inteiro. `EditEnterpriseGalleryCard` mexe em ficheiros e três endpoints
> distintos (banner imediato, galeria diferida com "Guardar", `PATCH` de `altText`) — RHF não
> encaixava no padrão de guardar em passos; ficou só o restilo (`BlueprintCard`, tokens `--ind-*`,
> `useConfirm()` em vez de `Popconfirm`).

## 2. Mensagens de erro do Zod: i18n keys num schema, string PT fixa no outro

- `constructionFormSchemas.ts:7,13,19,21-22,28,31` usa **chaves i18n** (`"constructionStages.formErrors.nameRequired"`), renderizadas com `t(errors.name.message as string)` — `ConstructionStageUpsertDrawer.tsx:115`, `ConstructionSubStageUpsertDrawer.tsx:115`, `ConstructionExpenseUpsertDrawer.tsx:134,160`.
- `enterpriseFormSchema.ts:44` usa **string PT literal** (`"O nome é obrigatório"`), renderizada com `String(errors.name.message)` — `BasicInfoSection.tsx:47`. Não passa pelo i18n, logo não traduz em EN.

**Convenção**: mensagem de Zod é sempre uma **chave i18n**, renderizada com `t(...)` — o padrão do `constructionFormSchemas.ts`. O projeto tem i18n pt/en ativo (`i18n.ts`); uma string fixa é um buraco de tradução silencioso.

## 3. 🐛 `MediaItemSchema.type` está tipado com o enum errado

`enterpriseFormSchema.ts:27` declara `type: EnterpriseTypeEnum` — ou seja, `"residential" | "commercial" | "industrial" | "mixed_use"`. Mas o valor real atribuído em runtime é `"banner"` (`CreateEnterpriseDrawer.tsx:160`) e é isso que todos os consumidores procuram: `EditEnterpriseGalleryCard.tsx:43,580` e `EnterpriseViewDrawer.tsx:263` fazem `m.type === "banner"`.

O erro passa despercebido porque o array `media` é preenchido à mão e enviado como `FormData` (`CreateEnterpriseDrawer.tsx:225`), sem nunca ser validado contra o schema. O comentário `// ← UPPERCASE` na linha 160 também não corresponde a nada (o valor é minúsculo).

**A corrigir**: dar ao media o seu próprio enum (`"banner" | "photo" | …`, conforme o que o backend aceita) em vez de reutilizar `EnterpriseTypeEnum`. Registado em [[../../../notes/refactoring.md]].

## 4. Submit/loading: duas estratégias, ambas dentro do próprio RHF

- **Só `loading`, botão sempre clicável**: os três drawers de construção — `ConstructionStageUpsertDrawer.tsx:97`, `ConstructionSubStageUpsertDrawer.tsx:97`, `ConstructionExpenseUpsertDrawer.tsx:116` (`loading={isSubmitting}`, sem `disabled`). O cancelar usa `disabled={isSubmitting}` (`:94`/`:113`).
- **Gate por validade**: `CreateEnterpriseDrawer.tsx:300-301` — `loading={isSubmitting}` + `disabled={!isValid}`.

**Convenção escolhida**: `disabled={!isValid}` + `loading={isSubmitting}` (o padrão do `CreateEnterpriseDrawer`, que já declara `mode: "onChange"` em `:40` — necessário para `isValid` atualizar enquanto se escreve). Os drawers de construção teriam de passar a declarar esse `mode` ao adotar o gate.

## 5. 🐛 Erro por campo: bloco copiado campo a campo — e um deles nem é vermelho

O mesmo bloco condicional está repetido campo a campo, **com markup diferente conforme o ficheiro**:

- `ConstructionStageUpsertDrawer.tsx:113-116`, `ConstructionSubStageUpsertDrawer.tsx:113-116`, `ConstructionExpenseUpsertDrawer.tsx:132-136,158-162` → `<Text type="danger" style={{ fontSize: 12 }}>` (Typography do antd, cor semântica de erro).
- `BasicInfoSection.tsx:45-48` → `<p style={{ color: "var(--ind-neutral-700)", fontSize: 12, marginTop: 4 }}>` — **cinzento neutro, não uma cor de erro**. A mensagem de validação do formulário de criação de empreendimento não se lê como erro.

**Convenção a adotar**: extrair um `<FieldError name="x" errors={errors} />` partilhado (em `components/common/`, junto de `Label`), usando `<Text type="danger">`. Resolve os três problemas de uma vez — markup único, cor de erro correta, e um sítio só para o `t(...)` do ponto 2.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]] — estrutura de pastas, naming, regra RHF+Zod
- [[backoffice-drawers-and-modals]] — Drawer vs Modal, larguras
- [[backoffice-services-and-error-handling]] — erros vindos da API (distinto de validação de campo)
- [[../../frontend/skill-frontend-design-system]] → "Regras de base" — a regra "Zod + React Hook Form sempre"
