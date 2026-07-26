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

Drawer é o padrão dominante (25 ficheiros usam `<Drawer>` contra 12 com `<Modal>`), mas `Modal` não está reservado só para confirmações — está a ser usado para formulários e conteúdo completo, o que compete diretamente com o papel do Drawer:

- `StatusChangeModal.tsx:86` — Modal usado para um **formulário** de mudança de estado, não uma confirmação simples.
- `MyProfileModal.tsx:310` (`width={760}`) — formulário completo de edição de perfil num Modal, enquanto `ProfileDrawer`/`ProfileView` (usados a partir das listas) mostram o mesmo tipo de conteúdo num Drawer — o mesmo conceito, dois contentores diferentes.
- `PropertySearchModal.tsx`, `DownloadHistoryModal.tsx`, `EditGalleryCard.tsx`, `EditPhotoOrder.tsx`, `EditDivisionOrder.tsx`, `SeeLicense.tsx` — Modal usado para edição/navegação secundária conceptualmente semelhante ao que os Drawers tratam noutros sítios.

**Convenção daqui em diante**:
- **Drawer** — qualquer criação/edição/visualização de uma entidade (o padrão já estabelecido e maioritário).
- **Modal** — só para utilitários autocontidos e curtos: um seletor de pesquisa (`PropertySearchModal`), um visualizador de documento, um histórico (`DownloadHistoryModal`), ou reordenação de itens (`EditPhotoOrder`, `EditDivisionOrder`). Nunca um formulário completo de edição de entidade — isso é sempre Drawer.
- `MyProfileModal.tsx` e `StatusChangeModal.tsx` são os dois exemplos claros a **não copiar**: são formulários de entidade que deviam ser Drawer. Migrar oportunisticamente, não é preciso reescrever já.
- Confirmações simples continuam a usar `Popconfirm` (já é o padrão dominante); `Modal.confirm` só quando a confirmação precisa de mais contexto/explicação do que cabe num popover.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-cards]]
- [[backoffice-buttons-and-icons]] — sobre `Popconfirm` em ações destrutivas
