# 🗺️ Mapa do código — onde vive cada funcionalidade

Este ficheiro responde a **"onde está o código disto?"**. Não explica como funciona nem porquê —
para isso há os outros:

| Pergunta | Onde |
|---|---|
| **Onde está?** | este ficheiro |
| Que rotas e regras de acesso tem? | [[api.md]] |
| Que tabelas e colunas? | [[database.md]] |
| Como se faz uma alteração aqui? | [[skills/SKILLS-INDEX]] (skills) · [[backend-conventions]] · [[skills/references/frontend-visual-consistency]] |
| Como se chama isto? | [[skills/references/project-vocabulary]] |

> Mantém-se **grosso de propósito**: portas de entrada e ficheiros-chave, não listas
> exaustivas. Um mapa que tenta listar tudo fica errado à primeira semana.

---

## Faturas de obra

O documento que vem da obra: carregar, ler o QR da AT, corrigir à mão, classificar em rubricas.
Uma fatura tem um **âmbito** (`scope`, `V26`): `PROJECT` (de uma obra), `COMPANY` (despesa da
empresa, sem obra) ou `UNIDENTIFIED` (quarentena — ainda não se sabe de quem é).

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos/:enterpriseId/invoices` → `pages/backoffice/enterprise/EnterpriseInvoicesPage.tsx` |
| **Classificar** | rota `/backoffice/empreendimentos/:enterpriseId/classify` → `pages/backoffice/enterprise/ClassifyInvoicesPage.tsx` (botão "Classificar" na página das faturas): repartir a fatura por rubricas (`V32`) · `components/invoices/RubricSearchField.tsx` · sugestão em `GET /construction-invoices/{id}/rubric-suggestion` |
| **Fora das obras** | dropdown **Faturas** da nav do espaço Obras (`layouts/AppLayout.tsx`): "Por identificar" → `/backoffice/invoices/unidentified` (`pages/backoffice/invoices/UnidentifiedInvoicesPage.tsx`) · "Despesas da empresa" → `/backoffice/invoices/company` (`CompanyInvoicesPage.tsx`) — as duas são o mesmo `ScopedInvoicesPage.tsx` com `scope` diferente, a ler `GET /construction-invoices/unidentified` e `/company` |
| **Frontend** | `components/invoices/` — `InvoicesList`, `InvoiceFiltersModal` (pesquisa avançada: modal centrado, ícone de filtro com contagem + "Limpar filtros" na página, sem chips — 2026-09-21; helpers em `invoiceFilters.ts`), `OutstandingTotalBadge`, `InvoiceUploadDrawer` (2 fases), `InvoiceRegisterDrawer` (sem ficheiro), `InvoiceDetailDrawer` (correção manual, líquido e NC ligadas), `InvoiceDocumentGallery`, `CreditNoteDrawer` (fase 3), `BudgetItemPickerModal`, `invoiceNumber.ts` (tipo + série), `invoiceFormSchema.ts`/`creditNoteFormSchema.ts` · `components/construction/InvoicePreviewModal.tsx` · `services/invoiceService.ts` · `types/invoice.ts` |
| **Backend** | `enterprises/controller/ConstructionInvoiceController` · `service/ConstructionInvoiceService` (o núcleo — upload, duplicados, correção, notas de crédito, repartição, transferência) · `AtInvoiceQrService` + `WeChatQrCodeService` (leitura do QR) · `InvoiceThumbnailService` · `InvoiceCompressionService` · `DespesasExcelImportService` (fase 6) · `repository/ConstructionInvoiceRepository` |
| **Base de dados** | `worksite.construction_invoice` — `V16`, `V17` (ATCUD único), `V18` (checksum), `V26` (`scope`), `V29` (unicidade global) · `construction_invoice_document` (o ficheiro deixa de viver na fatura) — `V24`, `V25`, `V27` (estado), `V28` (tipo) · repartição por várias rubricas — `V32` |
| **Detalhe** | [[api.md]] → "Faturas de obra", "Notas de crédito", "Classificar faturas em rubricas" · [[database.md]] · [[faturas-modelo-alvo]] |

## Pagamentos

Fase 2 da paridade com o Excel: "dar como pago" é um movimento (`payment`) ligado a uma ou
várias faturas (`invoice_payment`) — o caso normal e o pagamento agregado são o mesmo modelo.

| Camada | Ficheiros |
|---|---|
| **Entrada** | "Marcar como paga" no `InvoiceDetailDrawer` (uma fatura) · "Registar pagamento" na barra de seleção de `EnterpriseInvoicesPage` (várias faturas, um movimento) |
| **Frontend** | `components/invoices/MarkPaidDrawer.tsx` + `AggregatePaymentDrawer.tsx` · `paymentFormSchema.ts` · `services/paymentService.ts` · os campos `paymentStatus`/`payments` em `types/invoice.ts` |
| **Backend** | `enterprises/controller/PaymentController` (`POST /construction-invoices/{id}/payments`, `POST /construction-invoices/payments`, `DELETE /construction-invoices/payments/{paymentId}`) · `service/PaymentService` · `model/Payment` + `InvoicePayment` (+ `InvoicePaymentId`) · `enums/PaymentMethod`, `PaymentStatus` · `dto/payment/` · `repository/PaymentRepository` + `InvoicePaymentRepository` |
| **Base de dados** | `worksite.payment` + `invoice_payment` — `V31` |
| **Detalhe** | [[api.md]] → "Pagamentos" · [[faturas-modelo-alvo]] §2.3 |

## Inconsistências & transferências

Fase 5. Mudar uma fatura de obra ou de âmbito é uma **transferência com razão** (nunca edição do
`scope`); o que ficou por conciliar depois disso vira uma **inconsistência** — nota livre sobre
uma ou mais faturas, aberta à mão, resolvida à mão.

| Camada | Ficheiros |
|---|---|
| **Entrada** | menu lateral, grupo **Faturas** → "Inconsistências" → `/backoffice/invoices/incidents` (`pages/backoffice/invoices/InvoiceIncidentsPage.tsx`) · a ação "Transferir" no detalhe de uma fatura |
| **Frontend** | `components/invoices/TransferInvoiceDrawer.tsx` + `transferFormSchema.ts` (depois de transferir, sugere abrir uma inconsistência) · `IncidentDrawer.tsx` + `incidentFormSchema.ts` + `toIncidentInvoiceRef.ts` · `services/incidentService.ts` · `types/incident.ts` |
| **Backend** | transferência: `ConstructionInvoiceService` via `POST /construction-invoices/{id}/transfer` (`dto/invoice/request/InvoiceTransferDTO`, `response/InvoiceTransferResultDTO`) · inconsistências: `enterprises/controller/InvoiceIncidentController` (`/invoice-incidents`) · `service/InvoiceIncidentService` · `model/InvoiceIncident` · `dto/incident/` · `repository/InvoiceIncidentRepository` |
| **Base de dados** | `worksite.invoice_incident` + `invoice_incident_invoice` — `V33` · o check `ck_invoice_scope_enterprise` de `V26` é o que impede uma fatura `COMPANY`/`UNIDENTIFIED` de ficar com obra |
| **Detalhe** | [[api.md]] → "Transferir faturas", "Inconsistências" · [[faturas-modelo-alvo]] §4 |

## Fornecedores

Catálogo NIF → nome da empresa. Existe porque o QR da AT identifica o emitente só pelo NIF.

| Camada | Ficheiros |
|---|---|
| **Entrada** | menu do utilizador → grupo **Definições** (`layouts/shell/UserMenu.tsx`, nos dois espaços) → `components/suppliers/SuppliersDrawer.tsx` |
| **Frontend** | `services/supplierService.ts` · `types/supplier.ts` · evento `SUPPLIERS_CHANGED_EVENT` ouvido por `EnterpriseInvoicesPage` |
| **Backend** | `enterprises/controller/SupplierController` · `service/SupplierService` · `repository/SupplierRepository` · `model/Supplier` · `mapper/SupplierMapper` · as três queries de agregação em `ConstructionInvoiceRepository` |
| **Base de dados** | `worksite.supplier` — `V19` |
| **Detalhe** | [[api.md]] → "Fornecedores" |

## Provedores de email (SMTP)

As credenciais com que a plataforma envia convites e recuperações de password. Antes da `V21` a
tabela só era lida e a configuração entrava por `INSERT` à mão.

| Camada | Ficheiros |
|---|---|
| **Entrada** | menu do utilizador → grupo **Definições** → "Provedores de email" (`layouts/shell/UserMenu.tsx`, só `ADMIN`) → `components/settings/EmailProvidersDrawer.tsx` |
| **Frontend** | `services/emailProviderService.ts` · `types/emailProvider.ts` · `components/settings/emailProviderFormSchema.ts` |
| **Backend** | `controller/EmailProviderController` · `service/email/EmailProviderService` · `service/email/EmailService` (envio) · `repository/email/EmailProviderRepository` · `model/email/EmailProvider` · `mapper/email/EmailProviderMapper` |
| **Base de dados** | `settings.email_providers` — `V7`, `V21` (trigger de `updated_at`, índice de predefinido único, `entity_type`) |
| **Detalhe** | [[api.md]] → "Provedores de email" · [[environment.md]] |

## Orçamento de obra

A árvore de rubricas, importada do Excel do empreiteiro, e as despesas lançadas nela.

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos/:enterpriseId/budget` → `pages/backoffice/enterprise/ConstructionBudgetPage.tsx` |
| **Frontend** | `components/budget/` — drawers de despesas/detalhe/formulário, `BudgetItemDrawer` (criar/editar rubrica, com datas), `BudgetMoveToModal`, `BudgetRecycleBinDrawer` (zona de recuperação), `BudgetImportModal`, `budgetTree.ts`, `budgetFormSchemas.ts` · `services/budgetService.ts` · `types/budget.ts` |
| **Backend** | `enterprises/controller/ConstructionBudgetItemController` (inclui os lotes, `model/ConstructionBudget` + `ConstructionBudgetRepository`, V39) + `ConstructionExpenseController` · `service/ConstructionBudgetItemService` · `ConstructionExpenseService` · `BudgetExcelImportService` / `BudgetExcelExportService` / `DespesasExcelImportService` (Apache POI) · `InvoiceDocumentsExportService` (a `Faturas/Lançadas/` do zip, nomes §7) |
| **Base de dados** | `worksite.construction_budget_item` (auto-referenciada) + `construction_expense` — `V15`; soft delete (`deleted_at`) — `V36` |
| **Detalhe** | [[api.md]] → "Orçamento de Construção" |

## Projetos (Enterprises)

A obra. O nome `enterprises` ficou do Property-Management — ver [[architecture.md]].

| Camada | Ficheiros |
|---|---|
| **Entrada** | rota `/backoffice/empreendimentos` → `pages/enterprises/EnterprisesList.tsx` — ações por linha: "Ver detalhes" (drawer), "Ver orçamento" (→ `/backoffice/empreendimentos/{id}/budget`), "Eliminar"; **botão direito** na linha abre `EnterpriseContextMenu` com as três mais "Ver faturas" (→ `/backoffice/empreendimentos/{id}/invoices`) e "Exportar" (abre o `BudgetExportModal` sem navegar) — desde 2026-09-27 |
| **Frontend** | `components/enterprise/` — `create/` (secções + `enterpriseFormSchema.ts`), `edit/` (cards), `CreateEnterpriseDrawer`, `EnterpriseViewDrawer`, `EnterpriseContextMenu` (mesmo padrão do `components/employees/EmployeeContextMenu`) · `services/enterpriseService.ts` |
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

## Assiduidade — horários, emprego e picagens

Fase 1 do módulo de assiduidade (`V42`, `V43`). **Backend só** — o frontend está por estruturar.
Desenho das 5 fases, com as decisões e o porquê, em [[../notes/roadmap/assiduidade]].

| Camada | Ficheiros |
|---|---|
| **Entrada** | `controller/attendance/` — `WorkScheduleController` (`/attendance/work-schedules`), `EmploymentController` (`/attendance/employments`), `TimeEntryController` (`/attendance/time-entries`), `AttendanceSummaryController` (`/attendance/summary`), `AttendanceReportController` (`/attendance/reports`), `HolidayController` (`/attendance/holidays`), `AbsenceController` (`/attendance/absences`). Tudo `ADMIN` |
| **Cálculo** | `service/attendance/AttendanceCalculator` — **puro e estático**, recebe picagens + horário + feriado + ausência já carregados e devolve um `DayAttendance`. É onde um erro é silencioso (devolve o número errado, não lança), por isso tem os testes mais densos · `AttendanceSummaryService` junta as peças e agrega semana/mês · `AttendanceReportService` — o mês por funcionário e por obra (reagrupa os dias do resumo, sem cálculo novo) · `AttendanceExcelExportService` — o mesmo num `.xlsx` de 5 folhas, com picagens anuladas e correções · `VacationBalanceService` — o saldo, em **dias úteis** (precisa do horário e dos feriados, e é por isso que não é um método na entidade `Absence`) |
| **Backend** | `service/attendance/` — `WorkScheduleService`, `EmploymentService`, `TimeEntryService`, `AbsenceService`, `HolidayService`, **`AttendanceZone`** (o único sítio que sabe o fuso) · `repository/attendance/` · `mapper/attendance/` (o `AbsenceMapper` é um `@Component` e não MapStruct, porque precisa do `SignedUrlService`) · `model/attendance/` — `WorkSchedule`, `WorkScheduleDay`, `Employment`, `EmploymentTerm`, `TimeEntry`, `TimeEntryRevision`, `Holiday`, `Absence`, `AbsenceDocument` · `dto/attendance/{request,response}/` |
| **Enums** | `model/enums/` — `TimeDirection`, `TimeEntrySource`, `TimeEntryChange`, `HolidayScope`, `AbsenceType`, `AbsenceHalfDay`, `AbsenceStatus` · `DayAttendance.DayStatus` (WORKED/MISSING/NOT_SCHEDULED/NO_SCHEDULE/HOLIDAY/ON_LEAVE) |
| **Base de dados** | schema `attendance` — `V42` (horários), `V43` (emprego, picagens, revisões), `V44` (feriados, ausências, justificativos). **A fase 2 não tem migração**: horas, atrasos, faltas e saldo são derivados, nunca guardados |
| **Testes** | `service/attendance/` — `AttendanceZoneTest` (5, fuso e horário de verão), `WorkScheduleServiceTest` (12), `EmploymentServiceTest` (9, histórico), `TimeEntryServiceTest` (12, sequência e revisões), `AttendanceCalculatorTest` (22, aritmética do dia + feriados/ausências + horas por obra), `AttendanceSummaryServiceTest` (12), `VacationBalanceServiceTest` (10, dias úteis), `AbsenceServiceTest` (14), `HolidayServiceTest` (6), `AttendanceReportServiceTest` (2), `AttendanceExcelExportServiceTest` (2). **106 no total**  |
| **Frontend** | espaço **Equipa** (`/team/*`, só `ADMIN`): `layouts/TeamLayout.tsx` + `layouts/shell/` (header, lançador, menu do utilizador, partilhados com Obras) · páginas em `pages/team/` (`settings/WorkSchedulesPage`, `settings/HolidaysPage`) · drawers e schemas em `components/attendance/{schedules,holidays,employment}/` (a ficha de emprego é um cartão na `EmployeeProfilePage`, fora do `ProfileView`, que também serve a vista rápida e a "Minha Conta") · **todos** os tipos e chamadas do módulo em `types/attendance.ts` e `services/attendanceService.ts`, escritos de uma vez (os ecrãs que faltam só precisam de páginas) · ver [[skills/references/design/backoffice-app-shell-and-auth]] §5 |

> ⚠️ **O fuso é a armadilha deste módulo.** `AttendanceZone` existe porque o resto do projeto é todo
> UTC e nunca precisou de outra coisa: uma fatura tem data, não hora. Uma entrada às 08:00 em Lisboa é
> 07:00 UTC no verão e 08:00 UTC no inverno — contar dias em UTC poria picagens no dia errado durante
> metade do ano. Nunca derivar "o dia" de um `happenedAt` sem passar por aqui.

## Equipa, perfis e convites

| Camada | Ficheiros |
|---|---|
| **Entrada** | rotas `/backoffice/funcionarios` e `/funcionarios/:id` · convite público em `/accept-invite` |
| **Frontend** | `pages/backoffice/EmployeesList.tsx` · `employee/EmployeeProfilePage.tsx` · `pages/AcceptInvitePage.tsx` · `components/employees/`, `components/profile/`, `components/invites/InvitesDrawer` · `services/profileService.ts` + `adminService.ts` |
| **Backend** | `controller/EmployeesController` · `ProfileController` · `AdminAuthController` (envio de convites) · `AuthController` + `service/InviteService` (aceitação) · `service/employee/` · `ProfileService` · `service/email/` |
| **Base de dados** | `worksite.profile` (`V3`) · `settings.pending_invites` + `email_providers` (`V7`) |

## Notificações

| Camada | Ficheiros |
|---|---|
| **Entrada** | o sino do cabeçalho — `components/notifications/NotificationBell.tsx` (`iconFor` escolhe o ícone por `type`; sem polling) |
| **Frontend** | `services/notificationInboxService.ts` · `types/notification.ts` |
| **Backend** | `notifications/controller/NotificationController` · `service/NotificationService` (escrita chamada pelos serviços de domínio, na mesma transação) · `service/BudgetItemDeadlineNotifier` (o gatilho por job) · `repository/NotificationRepository` · `model/Notification` (constantes `TYPE_*`) |
| **Jobs agendados** | `enterprises/config/BudgetItemDeadlineNotifierConfig` (07:00 + arranque) — os outros dois jobs do projeto são `security/RevokedTokenCleanupConfig` (limpeza de tokens) e `enterprises/config/ConstructionBudgetItemPurgeConfig` (purga às 3h) |
| **Base de dados** | `worksite.notification` — `V20` |
| **Detalhe** | [[api.md]] → "Quem gera notificações" |

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
| a lista diz "0 resultado(s)" com linhas, ou um cartão diz "undefined" | a página do Spring vem `{content, page:{…}}` (`VIA_DTO`) e o código lê `totalElements` no topo — usar `utils/springPage.ts` `normalizeSpringPage` |
| a importação do Excel do orçamento falha | `BudgetExcelImportService` (procura a linha de cabeçalho "Art"/"Artº"/"Rubrica" em **qualquer** coluna, na primeira folha que a tenha — e não na folha 0 às cegas) |
| o orçamento importa "a zero euros" ou com um total que não bate certo | o mesmo serviço, mas é quase sempre o **nome das colunas de preço** (`P.U.`, `VALOR`, …) ou uma **fórmula arrastada** numa linha vazia — os três layouts conhecidos e as armadilhas estão em [[excel-parity]] |
| a exportação para Excel sai mal (folhas, tabelas, fórmulas, nome do ficheiro) | `BudgetExcelExportService` — o contrato é [[excel-parity]] §9 |
| o download de **um lote** sai com o nome errado, ou com folhas a mais | `BudgetExcelExportService.exportLot` (endpoint `/budgets/{budgetId}/export`) — existe a par do export da obra, que é o do vault e não se toca; a escolha do lote está no `BudgetExportModal` |
| um `ClassCastException` entre dois endpoints, ou um deles devolve os campos do outro | `CacheConfig.keyGenerator` — a chave tem de incluir o método, senão dois `@Cacheable` no mesmo cache com o mesmo argumento partilham a entrada ([[backend-conventions]]) |
| o zip da pasta da obra sai com nomes de ficheiro errados, `_2` a mais, ou sem um documento | `InvoiceDocumentsExportService` (`enterprises/service/`, endpoint `/export/zip` em `ConstructionBudgetItemController`) — o contrato é [[excel-parity]] §7; o `_EM-FALTA.txt` dentro do zip diz o que o Storage não devolveu |
| o download dos **PDFs** traz as faturas erradas (de outro lote, ou nenhumas) | o mesmo serviço, no `plan(…, scope, budgetId, …)` — o lote vem das rubricas onde a fatura está classificada, não de um campo dela; endpoints `/construction-invoices/enterprise/{id}/documents/{summary,zip}` e UI no `InvoiceDocumentsDownloadModal` ([[api]]) |
| a importação da folha "Despesas" do vault lista erros ou perguntas que não fazem sentido | `DespesasExcelImportService` (`enterprises/service/`, endpoint em `ConstructionInvoiceController` `/import-excel`) — o contrato é [[excel-parity]] §9; as frases das observações que ele lê de volta são as que o exportador gera (§4) |
| o email de convite ou de recuperação não sai | falta um provedor predefinido **ativo** em *Definições → Provedores de email* — o erro é `EMAIL_002`/`EMAIL_003`, não `ERR_001` |
| o link do email aponta para `localhost` | `APP_FRONTEND_URL` não está definido no ambiente — ver [[environment.md]] |
| aceitar um convite dá erro | `InviteService#accept` — `USER_013` (desconhecido/usado/cancelado) ou `USER_012` (fora do prazo) |
| o aviso de prazo de rubrica não aparece / aparece a dobrar | `BudgetItemDeadlineNotifier` — só `ITEM` vivas de obras não `completed/archived/deleted`, `end_date` em [hoje, hoje+N]; dedupe por `(recipient, type, entity)`, por isso adiar a data **não** reavisa |
| recuperar a password não faz nada | `PasswordResetService` — o `204` do `forgot-password` é sempre igual, exista ou não a conta; confirmar no log se saiu email |
| preciso de acrescentar um campo à fatura | migração → `ConstructionInvoice` → DTOs de `dto/invoice/` → `ConstructionInvoiceService` → `types/invoice.ts` → `InvoiceDetailDrawer` |
| a fatura está `PARTIAL` e devia estar `PAID` (ou o contrário) | `paymentStatus` é derivado da soma de `invoice_payment.amount` vs. o líquido — `PaymentService` (registo/anulação) e `InvoicePaymentSummaryDTO`; uma NC ligada baixa o líquido |
| a fatura não aparece na obra onde devia | olhar ao `scope`: `COMPANY`/`UNIDENTIFIED` não têm obra por construção (`ck_invoice_scope_enterprise`, `V26`) — muda-se com "Transferir" (`TransferInvoiceDrawer`), nunca pelo `PUT /{id}` |
| a transferência não abriu uma inconsistência | não abre sozinha: `suggestIncident` no `InvoiceTransferResultDTO` só sugere, o `IncidentDrawer` é sempre à mão |
| a fatura não deixa ser classificada / a repartição não fecha | `ClassifyInvoicesPage` — as linhas têm de somar o total da fatura (`V32`, `InvoiceSplitServiceTest` tem os casos) |

---

## Relacionado

- [[architecture.md]] — como as duas apps comunicam
- [[api.md]] — contrato de cada endpoint
- [[database.md]] — schema e migrações
- [[skills/SKILLS-INDEX]] — como fazer alterações seguindo as convenções do projeto
