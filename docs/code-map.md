# 🗺️ Mapa do código — onde vive cada funcionalidade

Este ficheiro responde a **"onde está o código disto?"**. Não explica como funciona nem porquê —
para isso há os outros:

| Pergunta | Onde |
|---|---|
| **Onde está?** | este ficheiro |
| Que rotas e regras de acesso tem? | [[api.md]] |
| Que tabelas e colunas? | [[database.md]] |
| Como se faz uma alteração aqui? | [[../management/managementapi/CLAUDE.md]] · [[../management/managementfrontend/apps/backoffice/CLAUDE.md]] |
| Como se chama isto? | [[skills/references/project-vocabulary]] |

> Mantém-se **grosso de propósito**: portas de entrada e ficheiros-chave, não listas
> exaustivas. Um mapa que tenta listar tudo fica errado à primeira semana.

---

## Faturas de obra

O documento que vem da obra: carregar, ler o QR da AT, corrigir à mão, associar a uma rubrica.

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos/:enterpriseId/invoices` → `pages/backoffice/enterprise/EnterpriseInvoicesPage.tsx` |
| **Frontend** | `components/invoices/` — `InvoicesList`, `InvoiceUploadDrawer` (2 fases), `InvoiceDetailDrawer` (correção manual), `BudgetItemPickerModal`, `invoiceNumber.ts` (tipo + série) · `components/construction/InvoicePreviewModal.tsx` · `services/invoiceService.ts` · `types/invoice.ts` |
| **Backend** | `enterprises/controller/ConstructionInvoiceController` · `service/ConstructionInvoiceService` (o núcleo — upload, duplicados, correção) · `AtInvoiceQrService` + `WeChatQrCodeService` (leitura do QR) · `InvoiceThumbnailService` · `InvoiceCompressionService` · `repository/ConstructionInvoiceRepository` |
| **Base de dados** | `worksite.construction_invoice` — `V16`, `V17` (ATCUD único), `V18` (checksum) |
| **Detalhe** | [[api.md]] → "Faturas de obra" · [[database.md]] |

## Fornecedores

Catálogo NIF → nome da empresa. Existe porque o QR da AT identifica o emitente só pelo NIF.

| Camada | Ficheiros |
|---|---|
| **Entrada** | ⚙️ **Definições** no cabeçalho (`layouts/AppLayout.tsx`) → `components/suppliers/SuppliersDrawer.tsx` |
| **Frontend** | `services/supplierService.ts` · `types/supplier.ts` · evento `SUPPLIERS_CHANGED_EVENT` ouvido por `EnterpriseInvoicesPage` |
| **Backend** | `enterprises/controller/SupplierController` · `service/SupplierService` · `repository/SupplierRepository` · `model/Supplier` · `mapper/SupplierMapper` · as três queries de agregação em `ConstructionInvoiceRepository` |
| **Base de dados** | `worksite.supplier` — `V19` |
| **Detalhe** | [[api.md]] → "Fornecedores" |

## Provedores de email (SMTP)

As credenciais com que a plataforma envia convites e recuperações de password. Antes da `V21` a
tabela só era lida e a configuração entrava por `INSERT` à mão.

| Camada | Ficheiros |
|---|---|
| **Entrada** | menu do utilizador → grupo **Definições** → "Provedores de email" (`layouts/AppLayout.tsx`, só `ADMIN`) → `components/settings/EmailProvidersDrawer.tsx` |
| **Frontend** | `services/emailProviderService.ts` · `types/emailProvider.ts` · `components/settings/emailProviderFormSchema.ts` |
| **Backend** | `controller/EmailProviderController` · `service/email/EmailProviderService` · `service/email/EmailService` (envio) · `repository/email/EmailProviderRepository` · `model/email/EmailProvider` · `mapper/email/EmailProviderMapper` |
| **Base de dados** | `settings.email_providers` — `V7`, `V21` (trigger de `updated_at`, índice de predefinido único, `entity_type`) |
| **Detalhe** | [[api.md]] → "Provedores de email" · [[environment.md]] |

## Orçamento de obra

A árvore de rubricas, importada do Excel do empreiteiro, e as despesas lançadas nela.

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos/:enterpriseId/budget` → `pages/backoffice/enterprise/ConstructionBudgetPage.tsx` |
| **Frontend** | `components/budget/` — drawers de despesas/detalhe/formulário/datas, `BudgetImportModal`, `budgetTree.ts`, `budgetFormSchemas.ts` · `services/budgetService.ts` · `types/budget.ts` |
| **Backend** | `enterprises/controller/ConstructionBudgetItemController` + `ConstructionExpenseController` · `service/ConstructionBudgetItemService` · `ConstructionExpenseService` · `BudgetExcelImportService` (Apache POI) |
| **Base de dados** | `worksite.construction_budget_item` (auto-referenciada) + `construction_expense` — `V15` |
| **Detalhe** | [[api.md]] → "Orçamento de Construção" |

## Projetos (Enterprises)

A obra. O nome `enterprises` ficou do Property-Management — ver [[architecture.md]].

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos` → `pages/enterprises/EnterprisesList.tsx` |
| **Frontend** | `components/enterprise/` — `create/` (secções + `enterpriseFormSchema.ts`), `edit/` (cards), `CreateEnterpriseDrawer`, `EnterpriseViewDrawer` · `services/enterpriseService.ts` |
| **Backend** | `enterprises/controller/EnterpriseController` + `EntrepriseRelationsController` · `service/EnterpriseService` · `mapper/EnterpriseMapper` |
| **Base de dados** | `worksite.enterprises` (+ `enterprises_location`, `enterprises_media`) — `V4`, `V12` |

## Tarefas

Tarefas standalone, isoladas no seu próprio schema — sem ligação a obra nenhuma.

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/tasks` → `pages/backoffice/TasksPage.tsx` |
| **Frontend** | `components/tasks/` — `TasksList`, `TaskFormDrawer`, `TaskDetailDrawer` · `services/taskService.ts` |
| **Backend** | `controller/TaskController` · `service/TaskService` · `model/Task` + `TaskAssignee` |
| **Base de dados** | schema `tasks` — `V14` |

## Equipa, perfis e convites

| Camada | Ficheiros |
|---|---|
| **Entrada** | rotas `/backoffice/funcionarios` e `/funcionarios/:id` · convite público em `/accept-invite` |
| **Frontend** | `pages/backoffice/EmployeesList.tsx` · `employee/EmployeeProfilePage.tsx` · `pages/AcceptInvitePage.tsx` · `components/employees/`, `components/profile/`, `components/invites/InvitesDrawer` · `services/profileService.ts` + `adminService.ts` |
| **Backend** | `controller/EmployeesController` · `ProfileController` · `AdminAuthController` (envio de convites) · `AuthController` + `service/InviteService` (aceitação) · `service/employee/` · `ProfileService` · `service/email/` |
| **Base de dados** | `worksite.profile` (`V3`) · `settings.pending_invites` + `email_providers` (`V7`) |

## Autenticação

JWT do Supabase validado localmente, cookies HttpOnly. **Sem SDK do Supabase no frontend.**

| Camada | Ficheiros |
|---|---|
| **Entrada** | `/login` → `pages/Login.tsx` → `/loading` → `PrivateRoute.tsx` · recuperação em `/forgot-password` → `/reset-password` (`pages/ForgotPassword.tsx`, `pages/ResetPassword.tsx`) |
| **Frontend** | `context/AuthContext.tsx` · `hooks/useAuth.ts` · `services/authService.ts` (login, `requestPasswordReset`, `resetPassword`) · `api.ts` (refresh automático em 401) |
| **Backend** | `controller/AuthController` · `service/SupabaseAuthService` · `service/PasswordResetService` · `security/` (`SecurityConfig`, `AuthContext`, `AccountLockFilter`, `TokenRevocationFilter`) |
| **Base de dados** | `worksite.revoked_token` (`V6`), `profile.role`, `profile.last_token_reset_at`, `settings.password_reset_tokens` (`V22`) |
| **Detalhe** | [[security.md]] |

---

## Onde procurar, por sintoma

A tabela que poupa mais tempo: o problema como se descreve em voz alta → o ficheiro onde
começar a olhar.

| "O que se passa é que…" | Começa em |
|---|---|
| o QR de uma fatura não é lido | `AtInvoiceQrService` (escalada de 4 degraus) → `WeChatQrCodeService` |
| a fatura entrou mas sem fornecedor | não é bug: o QR não traz o nome — `SupplierService` / `SuppliersDrawer` |
| a fatura entrou "por rever" | `needsReview` é derivado (falta data **ou** total) — `ConstructionInvoice.needsReview()` |
| diz que a fatura é duplicada e não devia | `ConstructionInvoiceService#rejectIfDuplicate` — três chaves, ver [[api.md]] → "Duplicados" |
| o erro que aparece no ecrã não diz nada | `errors/errorMessages.ts` (espelha `dto/error/ErrorCode.java` 1:1) |
| o pedido devolve 401/403 | `security/SecurityConfig` + o `@PreAuthorize` do controller |
| o ficheiro não abre / a imagem não aparece | signed URLs — `integrations/supabase/SignedUrlService`, `components/image/AuthenticatedImage` |
| a cor/espaçamento está fora do sistema | tokens `--ind-*` em `index.css` (espelhados em `theme.ts`) — ver [[skills/references/design/backoffice-tokens-and-colors]] |
| a lista não recarrega depois de gravar | o `onChanged`/`reload` da página que a contém (as drawers não recarregam nada sozinhas) |
| a importação do Excel do orçamento falha | `BudgetExcelImportService` (procura a linha de cabeçalho "Art") |
| o email de convite ou de recuperação não sai | falta um provedor predefinido **ativo** em *Definições → Provedores de email* — o erro é `EMAIL_002`/`EMAIL_003`, não `ERR_001` |
| o link do email aponta para `localhost` | `APP_FRONTEND_URL` não está definido no ambiente — ver [[environment.md]] |
| aceitar um convite dá erro | `InviteService#accept` — `USER_013` (desconhecido/usado/cancelado) ou `USER_012` (fora do prazo) |
| recuperar a password não faz nada | `PasswordResetService` — o `204` do `forgot-password` é sempre igual, exista ou não a conta; confirmar no log se saiu email |
| preciso de acrescentar um campo à fatura | migração → `ConstructionInvoice` → DTOs de `dto/invoice/` → `ConstructionInvoiceService` → `types/invoice.ts` → `InvoiceDetailDrawer` |

---

## Relacionado

- [[architecture.md]] — como as duas apps comunicam
- [[api.md]] — contrato de cada endpoint
- [[database.md]] — schema e migrações
- [[skills/SKILLS-INDEX]] — como fazer alterações seguindo as convenções do projeto
