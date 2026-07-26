# Backoffice — Buttons & Icons

> Parte de [[../frontend-visual-consistency]]. Só Backoffice.

## Tipos de botão

Contagem no código: `type="primary"` 118×, `type="text"` 57×, `type="default"` 10×, `type="dashed"` 5×, `type="link"` 9×, `danger` 87×.

`type="primary"` está razoavelmente reservado para a ação principal de criar/gravar (ex. "Adicionar propriedade") — **manter**.

### `type="text"` com estilo inline a simular um botão preenchido — intencional, não drift

Os botões de ação (ex. "Ver detalhes" nas colunas de ação) usam `type="text"` mas com `background`/`border` inline via `actionButtonBaseStyle` (`src/config/entityColors.ts`) a simular um botão preenchido terracota/contorno warmSand. **Isto é agora o padrão escolhido** (ver [[backoffice-tables-and-lists]] → coluna de ações), não um drift a corrigir — o `type="text"` é só o ponto de partida do AntD para poder sobrepor `background`/`border`/`boxShadow` livremente via `style`.

**Convenção**: não repetir o objeto de estilo por ficheiro — importar `actionButtonBaseStyle` e `D` de `src/config/entityColors.ts` e só variar `color`/`background`/`border` conforme a variante (`view` = preenchido terracota, secundária = contorno warmSand, destrutiva = contorno warmSand + texto `#b53333`). Se um quarto ficheiro precisar do mesmo padrão, considerar extrair um componente partilhado `<ActionButton variant="view"|"edit"|"delete">` só nessa altura — não antes, para não abstrair prematuramente.

## Confirmação antes de apagar — regra sem exceções

`Popconfirm` já é o padrão dominante (29 ficheiros no projeto), mas não é universal. Exceções encontradas que apagam sem confirmação:
- `EditLicense.tsx:744` e `:797`
- `TransactionDetailCard.tsx:59`
- `MyProfileModal.tsx:384` (apagar foto)

**Convenção sem exceções**: qualquer ação destrutiva (`danger`) tem sempre `Popconfirm` (ou, para casos que precisem de mais contexto, `Modal.confirm` — ver [[backoffice-drawers-and-modals]]). Os 4 casos acima são bugs a corrigir oportunisticamente, não um padrão a copiar.

## Ícone + texto vs. só ícone

- **Ícone + texto, botão de largura total**: convenção para colunas de ação (ver [[backoffice-tables-and-lists]] — botões verticais empilhados), e para ações principais/secundárias fora de tabelas.
- **Só ícone + `Tooltip`**: aceitável fora de colunas de ação — barras de ferramentas, cabeçalhos de card, ações inline dentro de texto — onde espaço horizontal é limitado e a ação é óbvia pelo contexto.

## Biblioteca de ícones

O `CLAUDE.md` do monorepo já prescreve **Lucide React**, mas o código real tem `@ant-design/icons` em 81 ficheiros contra `lucide-react` em apenas 30 — o Ant Design "ganhou" por acidente/inércia, não por decisão.

**Convenção**: honrar a decisão já documentada — **Lucide React para ícones novos**. `@ant-design/icons` só quando for exigido pelo próprio componente Ant Design (ex. um ícone posicionado dentro de um `Input`/`Select` do AntD que espera um `React.ReactNode` da família AntD). Não é preciso reescrever o que já existe, mas não continuar a introduzir mais `@ant-design/icons` em código novo.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tables-and-lists]]
- [[backoffice-drawers-and-modals]]
