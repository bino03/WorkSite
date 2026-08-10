import { useCallback, useEffect, useMemo, useState } from "react";
import type { CSSProperties, FC } from "react";
import { Button, Input, Modal, Spin } from "antd";
import { CloseOutlined, LeftOutlined, RightOutlined, SearchOutlined } from "@ant-design/icons";

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

/** Uma rubrica selecionável, já com o sítio onde vive resolvido. */
interface PickerItem {
  node: BudgetItemNode;
  /** Rubrica de topo do ramo — o "capítulo" do passo 1. */
  chapterId: string;
  /** Caminho entre o capítulo e a rubrica, sem nenhum dos dois. */
  context: string;
}

/**
 * Escolha da rubrica onde a fatura vai ser lançada.
 *
 * Percorre-se em dois passos — capítulo primeiro, rubrica depois — porque a
 * lista plana de um orçamento real são ~200 linhas em que "Betão" aparece em
 * três capítulos diferentes e nada as distingue à vista. O capítulo dá o
 * contexto de graça e reduz a escolha final a uma dúzia de linhas; quem sabe o
 * que procura escreve na pesquisa e salta o passo.
 *
 * A sugestão no topo é o que faz a diferença: a partir da segunda fatura do
 * mesmo fornecedor, associar continua a ser um clique, sem entrar em capítulo
 * nenhum.
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
  /** Capítulo aberto. `null` é o passo 1 (a lista de capítulos). */
  const [chapterId, setChapterId] = useState<string | null>(null);

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
    setChapterId(null);
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

  /**
   * Só as rubricas normais aceitam despesas — espelha
   * `BudgetRowKind.acceptsExpenses`, a mesma regra que o backend aplica em
   * `allocate` (`EXPENSE_ITEM_NOT_EXPENSABLE`).
   */
  const items = useMemo<PickerItem[]>(() => {
    return flattenTree(roots)
      .filter((node) => node.acceptsExpenses)
      .map((node) => {
        const trail = pathTo(roots, node.id);
        return {
          node,
          // Uma rubrica de topo é o seu próprio capítulo.
          chapterId: trail[0]?.id ?? node.id,
          // Fora o capítulo (já é o título) e a própria rubrica.
          context: trail.slice(1, -1).map((ancestor) => ancestor.name).join(" › "),
        };
      });
  }, [roots]);

  /** Capítulos com pelo menos uma rubrica lá dentro — os vazios não levam a lado nenhum. */
  const chapters = useMemo(
    () =>
      roots
        .map((root) => ({
          node: root,
          count: items.filter((item) => item.chapterId === root.id).length,
        }))
        .filter((chapter) => chapter.count > 0),
    [roots, items]
  );

  const visibleChapters = useMemo(
    () => chapters.filter((chapter) => matchesQuery(chapter.node, query)),
    [chapters, query]
  );

  const visibleItems = useMemo(
    () =>
      items.filter(
        (item) => item.chapterId === chapterId && matchesQuery(item.node, query)
      ),
    [items, chapterId, query]
  );

  const openChapter = (id: string) => {
    setChapterId(id);
    setQuery("");
  };

  const backToChapters = () => {
    setChapterId(null);
    setQuery("");
    setSelected(null);
  };

  const suggestedItem = useMemo(
    () => items.find((item) => item.node.id === suggestion?.budgetItemId) ?? null,
    [items, suggestion]
  );

  const chapterName = roots.find((root) => root.id === chapterId)?.name ?? "";
  const onChapters = chapterId === null;

  const isEmpty = onChapters ? visibleChapters.length === 0 : visibleItems.length === 0;
  const emptyMessage = onChapters
    ? items.length === 0
      ? "Este projeto ainda não tem orçamento importado."
      : "Nenhuma rubrica corresponde à pesquisa."
    : "Nenhuma sub-rubrica corresponde à pesquisa.";

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
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: "0 0 2px" }}>Associar</h6>
          {onChapters ? (
            <h2 style={{ margin: 0 }}>Escolher rubrica</h2>
          ) : (
            <>
              <button type="button" onClick={backToChapters} style={backLinkStyle}>
                <LeftOutlined style={{ fontSize: 12 }} />
                Rubricas
              </button>
              <h2 style={{ margin: 0 }}>{chapterName}</h2>
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
        {onChapters && suggestedItem && (
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
              item={suggestedItem}
              selected={selected?.id === suggestedItem.node.id}
              onSelect={() => setSelected(suggestedItem.node)}
            />
          </div>
        )}

        <Input
          placeholder={onChapters ? "Pesquisar rubrica…" : "Pesquisar sub-rubrica…"}
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
            {onChapters
              ? visibleChapters.map((chapter) => (
                  <ChapterRow
                    key={chapter.node.id}
                    node={chapter.node}
                    count={chapter.count}
                    onOpen={() => openChapter(chapter.node.id)}
                  />
                ))
              : visibleItems.map((item) => (
                  <ItemRow
                    key={item.node.id}
                    item={item}
                    selected={selected?.id === item.node.id}
                    onSelect={() => setSelected(item.node)}
                  />
                ))}

            {!loading && isEmpty && (
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
};

/** Os números da direita — iguais nos dois passos, por isso vivem num sítio só. */
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

const ChapterRow: FC<{ node: BudgetItemNode; count: number; onOpen: () => void }> = ({
  node,
  count,
  onOpen,
}) => (
  <button type="button" className="ind-picker-row" onClick={onOpen}>
    <div style={{ flex: 1, minWidth: 0 }}>
      <RowTitle node={node} />
      <div style={{ fontSize: 11, marginTop: 2, opacity: 0.55 }}>
        {count} sub-rubrica{count === 1 ? "" : "s"}
      </div>
    </div>
    <div style={{ display: "flex", alignItems: "center", gap: 8, flex: "none" }}>
      <RowFigures node={node} />
      <RightOutlined style={{ fontSize: 14, opacity: 0.5 }} />
    </div>
  </button>
);

const ItemRow: FC<{ item: PickerItem; selected: boolean; onSelect: () => void }> = ({
  item,
  selected,
  onSelect,
}) => (
  <button
    type="button"
    className="ind-picker-row"
    aria-selected={selected}
    onClick={onSelect}
  >
    <div style={{ flex: 1, minWidth: 0 }}>
      <RowTitle node={item.node} />
      {item.context && (
        <div style={{ fontSize: 11, marginTop: 2, opacity: 0.55 }}>{item.context}</div>
      )}
    </div>
    <RowFigures node={item.node} />
  </button>
);
