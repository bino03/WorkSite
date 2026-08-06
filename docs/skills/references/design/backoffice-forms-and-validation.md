# Backoffice — Formulários e Validação

> Parte de [[../frontend-visual-consistency]]. Só Backoffice. Baseado em `ConstructionStageUpsertDrawer.tsx`, `ConstructionSubStageUpsertDrawer.tsx`, `ConstructionExpenseUpsertDrawer.tsx`, `CreateEnterpriseDrawer.tsx` (+ `create/*Section.tsx`), `enterprise/edit/Edit*Card.tsx`, `CreateEmployeeDrawer.tsx`, `TaskFormDrawer.tsx`, `TaskDetailDrawer.tsx`, `MyProfileModal.tsx`, `ProfileView.tsx`, `AcceptInvitePage.tsx`. Auditoria 2026-08-05.

O [[../../frontend/skill-frontend-design-system]] prescreve **React Hook Form + Zod** para todos os formulários (e [[../code-best-practices]] repete a regra). A auditoria mostra duas convenções a competir — e o corte não é "código antigo vs novo", é **por domínio**.

## 1. Biblioteca de formulário: RHF+Zod no domínio de construção/criação, AntD Form no resto

- **RHF + Zod** (`zodResolver`): `ConstructionStageUpsertDrawer.tsx:4,47-48`, `ConstructionSubStageUpsertDrawer.tsx:4,47-48`, `ConstructionExpenseUpsertDrawer.tsx:4,54-55`, `CreateEnterpriseDrawer.tsx:5,38-39`.
- **AntD `Form.useForm()`**: `CreateEmployeeDrawer.tsx:24`, `TaskFormDrawer.tsx:30`, `TaskDetailDrawer.tsx:24`, `ProfileView.tsx:76`, `MyProfileModal.tsx:146-148` (três formulários no mesmo modal), `AcceptInvitePage.tsx:20`, e **todos** os cards de edição de empreendimento: `EditDatesAndAreasCard.tsx:40`, `EditEnterpriseOverviewCard.tsx:62`, `EditFinancialCard.tsx:58`.

Só existem dois schemas Zod no projeto: `components/construction/constructionFormSchemas.ts` e `components/enterprise/create/enterpriseFormSchema.ts`.

**O caso mais gritante é o empreendimento**: a **criação** (`CreateEnterpriseDrawer` + secções) usa RHF+Zod, mas a **edição** da mesma entidade (os três `Edit*Card`) usa AntD Form. Mesma entidade, mesmos campos, duas bibliotecas e duas definições de "campo obrigatório" — a de edição não passa pelo `EnterpriseFormSchema`.

**Convenção escolhida: RHF + Zod**, como o skill prescreve. Migrar oportunisticamente quando um destes ficheiros for tocado; prioridade aos `Edit*Card` do empreendimento, por serem os que divergem da criação da mesma entidade.

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
- [[../code-best-practices]] — regra geral "Zod + React Hook Form sempre"
