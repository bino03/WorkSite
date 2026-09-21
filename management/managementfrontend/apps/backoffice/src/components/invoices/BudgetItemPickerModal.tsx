import { useCallback, useEffect, useMemo, useState } from "react";
import type { CSSProperties, FC } from "react";
import { Button, Input, Modal, Spin } from "antd";
import { CloseOutlined, LeftOutlined, SearchOutlined } from "@ant-design/icons";

import { getBudgetTree } from "@/services/budgetService";
import { suggestBudgetItem } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { formatCurrency } from "@/utils/formatters";
import { flattenTree, matchesQuery, pathTo } from "@/components/budget/budgetTree";
import type { BudgetItemNode, BudgetTree } from "@/types/budget";
import type { BudgetItemSuggestion } from "@/types/invoice";

interface Props {
  open: boolean;
  enterpriseId: string;
  /** Quantas faturas vão ser associadas — o rodapé di-lo para não haver enganos. */
  count?: number;
  /** NIF do fornecedor; com ele consegue-se sugerir a rubrica de sempre. */
  supplierNif?: string | null;
  onClose: () => void;
  onPick: (item: BudgetItemNode) => void;
  saving?: boolean;
}

/** Uma linha da lista, já com o sítio onde vive resolvido. */
interface PickerRow {
  node: BudgetItemNode;
  /** Caminho entre o nível aberto e a rubrica, sem nenhum dos dois — só na pesquisa. */
  context: string;
  /** Quantas rubricas selecionáveis há por baixo; 0 = é uma folha. */
  subCount: number;
}

/** Uma rubrica onde se pode lançar, ou um título com rubricas lá dentro. */
function carriesExpenses(node: BudgetItemNode): boolean {
  return node.acceptsExpenses || node.children.some(carriesExpenses);
}

function countSelectable(node: BudgetItemNode): number {
  return node.children.reduce(
    (sum, child) => sum + (child.acceptsExpenses ? 1 : 0) + countSelectable(child),
    0
  );
}

/**
 * Escolha da rubrica onde a fatura vai ser lançada.
 *
 * Desce-se a árvore um nível de cada vez — os capítulos, depois as filhas do
 * que se abriu, depois as netas — como no ecrã de classificação: a `4.2.1` só
 * aparece depois de abrir a `4.2`, e a `4.2` só depois de abrir a `4`. A
 * versão anterior achatava o capítulo inteiro no segundo passo (`4.2`, `4.2.1`,
 * `4.2.2`, `4.3.1`… tudo junto) e ficava confusa num orçamento real
 * (apontado pelo utilizador a 2026-09-21). Clicar numa linha escolhe-a;
 * "ver sub-rubricas ›" abre-a. Quem sabe o que procura escreve na pesquisa,
 * que procura em todo o ramo aberto e salta os passos.
 *
 * A sugestão no topo é o que faz a diferença: a partir da segunda fatura do
 * mesmo fornecedor, associar continua a ser um clique, sem abrir nada.
 */
export const BudgetItemPickerModal: FC<Props> = ({
  open,
  enterpriseId,
  count = 1,
  supplierNif,
  onClose,
  onPick,
  saving,
}) => {
  const [tree, setTree] = useState<BudgetTree | null>(null);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [suggestion, setSuggestion] = useState<BudgetItemSuggestion | null>(null);
  const [selected, setSelected] = useState<BudgetItemNode | null>(null);
  /** Rubrica aberta. `null` é o topo (a lista de capítulos). */
  const [parentId, setParentId] = useState<string | null>(null);

  const fetchTree = useCallback(async () => {
    setLoading(true);
    try {
      setTree(await getBudgetTree(enterpriseId));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [enterpriseId]);

  useEffect(() => {
    if (!open) return;
    setQuery("");
    setSelected(null);
    setSuggestion(null);
    setParentId(null);
    fetchTree();

    if (supplierNif) {
      // Sem histórico o backend responde 204 e isto fica a null — não é erro,
      // é só a primeira fatura deste fornecedor.
      suggestBudgetItem(enterpriseId, supplierNif)
        .then(setSuggestion)
        .catch(() => setSuggestion(null));
    }
  }, [open, enterpriseId, supplierNif, fetchTree]);

  const roots = useMemo(() => tree?.roots ?? [], [tree]);

  /** Do topo até à rubrica aberta, inclusive — vazio no topo. */
  const trail = useMemo(() => (parentId ? pathTo(roots, parentId) : []), [roots, parentId]);
  const current = trail.length > 0 ? trail[trail.length - 1] : null;
  const level = current ? current.children : roots;
  const searching = query.trim().length > 0;

  /**
   * Sem pesquisa, só as filhas diretas do nível aberto (as que levam gasto —
   * as notas não). Com pesquisa, todas as rubricas selecionáveis do ramo, com o
   * caminho até elas, porque "Betão" aparece em três sítios e o nome sozinho
   * não os distingue. Só as rubricas normais aceitam despesas — espelha
   * `BudgetRowKind.acceptsExpenses`, a regra que o backend aplica em `allocate`.
   */
  const rows = useMemo<PickerRow[]>(() => {
    if (!searching) {
      return level
        .filter(carriesExpenses)
        .map((node) => ({ node, context: "", subCount: countSelectable(node) }));
    }
    return flattenTree(level)
      .filter((node) => node.acceptsExpenses && matchesQuery(node, query))
      .map((node) => {
        const path = pathTo(level, node.id);
        return {
          node,
          context: path.slice(0, -1).map((ancestor) => ancestor.name).join(" › "),
          subCount: countSelectable(node),
        };
      });
  }, [level, query, searching]);

  const openNode = (node: BudgetItemNode) => {
    setParentId(node.id);
    setQuery("");
  };

  const goUp = () => {
    setParentId(trail.length > 1 ? trail[trail.length - 2].id : null);
    setQuery("");
  };

  const suggestedRow = useMemo<PickerRow | null>(() => {
    const node = flattenTree(roots).find((n) => n.id === suggestion?.budgetItemId);
    if (!node) return null;
    const path = pathTo(roots, node.id);
    return {
      node,
      context: path.slice(0, -1).map((ancestor) => ancestor.name).join(" › "),
      subCount: countSelectable(node),
    };
  }, [roots, suggestion]);

  const atTop = current === null;
  const hasBudget = roots.some(carriesExpenses);

  const emptyMessage = !hasBudget
    ? "Este projeto ainda não tem orçamento importado."
    : searching
      ? "Nenhuma rubrica corresponde à pesquisa."
      : "Esta rubrica não tem sub-rubricas.";

  const footerLabel = selected
    ? `${selected.code ? `${selected.code} · ` : ""}${selected.name}`
    : `${count} fatura${count === 1 ? "" : "s"} por associar`;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      closable={false}
      footer={null}
      centered
      destroyOnClose
      width="min(640px, 94vw)"
      styles={{
        content: {
          background: "var(--ind-color-bg)",
          border: "1px solid var(--ind-color-divider)",
          boxShadow: "var(--ind-shadow-lg)",
          padding: 0,
          maxHeight: "88vh",
          display: "flex",
          flexDirection: "column",
        },
        body: { display: "flex", flexDirection: "column", minHeight: 0, flex: 1 },
        mask: { background: "color-mix(in srgb, #2b2b2d 50%, transparent)" },
      }}
    >
      <i className="ind-corner tl" />
      <i className="ind-corner tr" />
      <i className="ind-corner bl" />
      <i className="ind-corner br" />

      <div
        style={{
          padding: "20.4px 20.4px 13.6px",
          borderBottom: "1px solid var(--ind-color-divider)",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-start",
          flex: "none",
        }}
      >
        <div style={{ minWidth: 0 }}>
          <h6 style={{ color: "var(--ind-accent-700)", margin: "0 0 2px" }}>Associar</h6>
          {atTop ? (
            <h2 style={{ margin: 0 }}>Escolher rubrica</h2>
          ) : (
            <>
              <button type="button" onClick={goUp} style={backLinkStyle}>
                <LeftOutlined style={{ fontSize: 12 }} />
                {trail.length > 1 ? labelOf(trail[trail.length - 2]) : "Rubricas"}
              </button>
              <h2 style={{ margin: 0 }}>{labelOf(current)}</h2>
            </>
          )}
        </div>
        <Button type="text" icon={<CloseOutlined />} onClick={onClose} aria-label="Fechar" />
      </div>

      <div
        style={{
          padding: "13.6px 20.4px",
          display: "flex",
          flexDirection: "column",
          gap: "10.2px",
          overflow: "hidden",
          flex: 1,
          minHeight: 0,
        }}
      >
        {atTop && !searching && suggestedRow && (
          <div
            className="ind-card ind-blueprint"
            style={{ padding: "10.2px", gap: 6, borderColor: "var(--ind-color-accent)", flex: "none" }}
          >
            <i className="ind-corner tl" />
            <i className="ind-corner tr" />
            <i className="ind-corner bl" />
            <i className="ind-corner br" />
            <span className="ind-card-kicker">Habitual deste fornecedor</span>
            <ItemRow
              row={suggestedRow}
              showContext
              selected={selected?.id === suggestedRow.node.id}
              onSelect={() => setSelected(suggestedRow.node)}
              onOpen={null}
            />
          </div>
        )}

        <Input
          placeholder={atTop ? "Pesquisar rubrica…" : `Pesquisar em ${labelOf(current)}…`}
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          allowClear
          style={{ flex: "none" }}
        />

        <Spin spinning={loading} wrapperClassName="ind-picker-spin">
          <div
            style={{
              overflowY: "auto",
              flex: 1,
              minHeight: 0,
              display: "flex",
              flexDirection: "column",
              gap: 2,
              paddingRight: 4,
            }}
          >
            {rows.map((row) => (
              <ItemRow
                key={row.node.id}
                row={row}
                showContext={searching}
                selected={selected?.id === row.node.id}
                onSelect={row.node.acceptsExpenses ? () => setSelected(row.node) : null}
                onOpen={row.subCount > 0 ? () => openNode(row.node) : null}
              />
            ))}

            {!loading && rows.length === 0 && (
              <div style={{ padding: "20.4px 0", textAlign: "center" }}>
                <p style={{ fontSize: 13, margin: 0, opacity: 0.55 }}>{emptyMessage}</p>
              </div>
            )}
          </div>
        </Spin>
      </div>

      <div
        style={{
          padding: "13.6px 20.4px",
          borderTop: "1px solid var(--ind-color-divider)",
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          gap: "10.2px",
          flex: "none",
        }}
      >
        <span style={{ fontSize: 13, opacity: 0.55, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {footerLabel}
        </span>
        <div style={{ display: "flex", gap: "6.8px", flex: "none" }}>
          <Button onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button
            type="primary"
            loading={saving}
            disabled={!selected}
            onClick={() => selected && onPick(selected)}
          >
            Associar
          </Button>
        </div>
      </div>
    </Modal>
  );
};

function labelOf(node: BudgetItemNode | null): string {
  if (!node) return "";
  return node.code ? `${node.code} ${node.name}` : node.name;
}

const backLinkStyle: CSSProperties = {
  display: "inline-flex",
  alignItems: "center",
  gap: 4,
  marginBottom: 2,
  padding: 0,
  background: "none",
  border: "none",
  cursor: "pointer",
  fontSize: 12,
  fontFamily: "inherit",
  color: "var(--ind-accent-700)",
  maxWidth: "100%",
  overflow: "hidden",
  textOverflow: "ellipsis",
  whiteSpace: "nowrap",
};

const RowFigures: FC<{ node: BudgetItemNode }> = ({ node }) => (
  <div style={{ textAlign: "right", flex: "none", fontSize: 12 }}>
    <div>Orç. {formatCurrency(node.rolledUpBudget)}</div>
    <div style={{ opacity: 0.55 }}>Gasto {formatCurrency(node.spentTotal)}</div>
    {node.overBudget && (
      <div style={{ color: "var(--error)", fontSize: 11, marginTop: 2 }}>acima do orçamento</div>
    )}
  </div>
);

const RowTitle: FC<{ node: BudgetItemNode }> = ({ node }) => (
  <div style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, fontSize: 14 }}>
    {node.code && <span style={{ color: "var(--ind-accent-700)", marginRight: 4 }}>{node.code}</span>}
    {node.name}
  </div>
);

/**
 * Uma linha. Clicar escolhe (quando a rubrica aceita despesas) ou abre (um
 * título só se pode abrir). "ver sub-rubricas ›" é um `span` dentro do botão,
 * com `stopPropagation` para abrir não contar como escolher — o mesmo gesto do
 * `RubricSearchField`.
 */
const ItemRow: FC<{
  row: PickerRow;
  showContext: boolean;
  selected: boolean;
  onSelect: (() => void) | null;
  onOpen: (() => void) | null;
}> = ({ row, showContext, selected, onSelect, onOpen }) => {
  const { node, context, subCount } = row;
  const primary = onSelect ?? onOpen ?? undefined;
  return (
    <button
      type="button"
      className="ind-picker-row"
      aria-selected={selected}
      onClick={primary}
      disabled={!primary}
    >
      <div style={{ flex: 1, minWidth: 0 }}>
        <RowTitle node={node} />
        {showContext && context && (
          <div style={{ fontSize: 11, marginTop: 2, opacity: 0.55 }}>{context}</div>
        )}
        {subCount > 0 && (
          <div style={{ fontSize: 11, marginTop: 2, display: "flex", gap: 8, alignItems: "center" }}>
            <span style={{ opacity: 0.55 }}>
              {subCount} sub-rubrica{subCount === 1 ? "" : "s"}
            </span>
            {onOpen && (
              <span
                role="link"
                style={{ color: "var(--ind-color-accent)", cursor: "pointer" }}
                onClick={(e) => {
                  e.stopPropagation();
                  onOpen();
                }}
              >
                ver sub-rubricas ›
              </span>
            )}
          </div>
        )}
      </div>
      <RowFigures node={node} />
    </button>
  );
};
