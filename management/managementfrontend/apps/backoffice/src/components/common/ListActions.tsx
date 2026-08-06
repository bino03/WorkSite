import type { FC, ReactNode } from 'react';
import { Button } from 'antd';

/**
 * Coluna "Ações" das listas do Backoffice — padrão único da app.
 *
 * Botões empilhados na vertical, alinhados à esquerda, com rótulo visível
 * (sem depender de Tooltip para se perceber o que fazem). Hierarquia:
 *   - `ListActionPrimary`   → ação principal ("Ver detalhes"), botão default
 *   - `ListActionSecondary` → ação secundária ("Editar"), type="text"
 *   - `ListActionDanger`    → ação destrutiva ("Eliminar"), type="text" em cor de acento
 *
 * Usar sempre estes componentes em vez de repetir o objeto de estilo por
 * ficheiro. Ver docs/skills/references/design/backoffice-tables-and-lists.md.
 */

const baseStyle = { justifyContent: 'flex-start' as const };

export const ListActions: FC<{ children: ReactNode }> = ({ children }) => (
  // stopPropagation: em listas cuja linha é clicável (`onRow.onClick`), um clique
  // num botão de ação não deve também disparar a navegação da linha.
  <div
    style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 110 }}
    onClick={(e) => e.stopPropagation()}
  >
    {children}
  </div>
);

interface ActionProps {
  onClick: () => void;
  children: ReactNode;
  loading?: boolean;
  disabled?: boolean;
}

export const ListActionPrimary: FC<ActionProps> = ({ onClick, children, loading, disabled }) => (
  <Button size="small" onClick={onClick} loading={loading} disabled={disabled} style={baseStyle}>
    {children}
  </Button>
);

export const ListActionSecondary: FC<ActionProps> = ({ onClick, children, loading, disabled }) => (
  <Button type="text" size="small" onClick={onClick} loading={loading} disabled={disabled} style={baseStyle}>
    {children}
  </Button>
);

export const ListActionDanger: FC<ActionProps> = ({ onClick, children, loading, disabled }) => (
  <Button
    type="text"
    size="small"
    onClick={onClick}
    loading={loading}
    disabled={disabled}
    style={{ ...baseStyle, opacity: 0.75, color: 'var(--ind-color-accent)' }}
  >
    {children}
  </Button>
);
