# Backoffice — Drawers & Modals

> Parte de [[../frontend-visual-consistency]]. Só Backoffice.

## Drawers

Larguras observadas no código atual variam livremente: `600`, `640`, `800`, `850`, `900`, `1300`, `"75%"`, `"80%"` — sem constante partilhada, cada domínio escolheu o seu número.

**Convenção daqui em diante** (nova — ainda não existe no código, adotar em componentes novos e migrar os antigos oportunisticamente):

| Tamanho | Largura | Uso |
|---|---|---|
| Small | `600` | Formulário simples (contacto, funcionário) |
| Medium | `900` | Criar/editar (empreendimento, edifício, licença) |
| Large | `1300` ou `"80%"` | Visualização completa (detalhe de propriedade) |

- **`maskClosable`**: deixar o valor por omissão do Ant Design (`true`) — não o definir explicitamente. Isto já é o que ~17 dos ~19 drawers atuais fazem.
- Botões de rodapé: sempre alinhados à direita (`<Space style={{justifyContent:'flex-end'}}>`), ação primária mais à direita, cancelar/fechar primeiro.
- **Texto dos botões**: sempre via `t('common.cancel')` / `t('common.save')` / `t('common.create')` — **nunca strings em português hardcoded**. `ContactUpsertDrawer.tsx` mistura as duas formas no mesmo ficheiro (usa `t(...)` no modo edição mas `"Fechar"`/`"Editar"` hardcoded no modo visualização) — é drift, não copiar.

## Modals — quando usar em vez de Drawer

Drawer é o padrão dominante (24 ficheiros usam `<Drawer>` contra 12 com `<Modal>`), mas `Modal` não está reservado só para confirmações — está a ser usado para formulários e conteúdo completo, o que compete diretamente com o papel do Drawer:

- `StatusChangeModal.tsx:86` — Modal usado para um **formulário** de mudança de estado, não uma confirmação simples.
- `PropertySearchModal.tsx`, `DownloadHistoryModal.tsx`, `EditGalleryCard.tsx`, `EditPhotoOrder.tsx`, `EditDivisionOrder.tsx`, `SeeLicense.tsx` — Modal usado para edição/navegação secundária conceptualmente semelhante ao que os Drawers tratam noutros sítios.

> ✅ **Migrado a 2026-09-16**: `MyProfileModal.tsx` (`width={760}`, formulário completo de edição
> da própria conta num Modal) passou a `MyProfileDrawer.tsx` — `Drawer` de `640`, kicker + `h2`,
> `Tabs` (Geral/Email/Segurança) em vez do sidebar escuro à parte, cada separador com o seu
> Cancelar/Guardar. Era o exemplo mais citado deste documento; deixou de ser drift.

**Convenção daqui em diante**:
- **Drawer** — qualquer criação/edição/visualização de uma entidade (o padrão já estabelecido e maioritário).
- **Modal** — só para utilitários autocontidos e curtos: um seletor de pesquisa (`PropertySearchModal`, `BudgetItemPickerModal`), um visualizador de documento, um histórico (`DownloadHistoryModal`), ou reordenação de itens (`EditPhotoOrder`, `EditDivisionOrder`). Nunca um formulário completo de edição de entidade — isso é sempre Drawer.

**Exemplo de aplicação (2026-08-09)**: o seletor de rubrica era `BudgetItemPickerDrawer` (600, à direita) e passou a `BudgetItemPickerModal` (centrado, `min(640px, 94vw)`, `max-height:88vh`) ao ser redesenhado. Escolher uma rubrica é um seletor de pesquisa, não a edição de uma entidade — cai do lado do Modal. O modal traz cabeçalho/rodapé fixos e uma lista que faz scroll no meio, e navega em **dois passos** (capítulo → rubrica) em vez de uma lista plana de ~200 linhas.
- `StatusChangeModal.tsx` continua a ser o exemplo claro a **não copiar**: é um formulário de entidade que devia ser Drawer. Migrar oportunisticamente, não é preciso reescrever já — `MyProfileModal.tsx` era o outro exemplo, migrado a 2026-09-16 (ver acima).
- Confirmações de ações destrutivas usam o diálogo partilhado `useConfirm()` (`context/ConfirmDialogContext`) — ver [[backoffice-buttons-and-icons]]. `Popconfirm` só sobrevive em três ficheiros por migrar; não o uses em código novo. `Modal.confirm` só quando a confirmação precisa de mais contexto do que o diálogo partilhado permite.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-cards]]
- [[backoffice-buttons-and-icons]] — sobre `Popconfirm` em ações destrutivas
