import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Button, Empty, Input, Spin, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { ArrowLeftOutlined, EditOutlined, SearchOutlined, UploadOutlined } from "@ant-design/icons";

import { getBudgetTree } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetItemNode, BudgetTree } from "@/types/budget";
import { BudgetExpensesDrawer } from "@/components/budget/BudgetExpensesDrawer";
import { BudgetDatesDrawer } from "@/components/budget/BudgetDatesDrawer";
import { BudgetImportModal } from "@/components/budget/BudgetImportModal";
import { collectAncestorsMissingDates, flattenTree, matchesQuery } from "@/components/budget/budgetTree";

/** Quantos caracteres de descrição mostrar antes de oferecer "ver mais". */
const DESC_CLAMP = 150;

const ConstructionBudgetPage: FC = () => {
  const { enterpriseId } = useParams<{ enterpriseId: string }>();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();

  const [tree, setTree] = useState<BudgetTree | null>(null);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [expandedDescs, setExpandedDescs] = useState<Set<string>>(new Set());

  const [expensesItem, setExpensesItem] = useState<BudgetItemNode | null>(null);
  const [datesItem, setDatesItem] = useState<BudgetItemNode | null>(null);
  const [importOpen, setImportOpen] = useState(false);

  const fetchTree = useCallback(async () => {
    if (!enterpriseId) return;
    setLoading(true);
    try {
      const data = await getBudgetTree(enterpriseId);
      setTree(data);
      // Abre só os capítulos: 198 linhas abertas de uma vez não se leem.
      setExpandedKeys(data.roots.map((r) => r.id));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [enterpriseId]);

  useEffect(() => {
    fetchTree();
  }, [fetchTree]);

  /**
   * A pesquisa mantém os ascendentes de qualquer nó que corresponda — sem isso
   * uma rubrica encontrada apareceria sem o capítulo a que pertence.
   */
  const visibleRoots = useMemo(() => {
    if (!tree) return [];
    if (!query.trim()) return tree.roots;

    const keep = (node: BudgetItemNode): BudgetItemNode | null => {
      const children = node.children.map(keep).filter(Boolean) as BudgetItemNode[];
      if (children.length > 0 || matchesQuery(node, query)) {
        return { ...node, children };
      }
      return null;
    };
    return tree.roots.map(keep).filter(Boolean) as BudgetItemNode[];
  }, [tree, query]);

  const visibleCount = useMemo(() => flattenTree(visibleRoots).length, [visibleRoots]);

  useEffect(() => {
    // Com pesquisa activa mostra-se tudo o que sobreviveu ao filtro.
    if (query.trim()) setExpandedKeys(flattenTree(visibleRoots).map((n) => n.id));
  }, [query, visibleRoots]);

  const toggleDesc = (id: string) =>
    setExpandedDescs((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const columns: ColumnsType<BudgetItemNode> = useMemo(
    () => [
      {
        title: "Art.",
        dataIndex: "code",
        width: 96,
        render: (code: string | null, row) =>
          row.rowKind === "NOTE" ? null : (
            <span
              style={{
                fontFamily: "var(--ind-font-heading)",
                fontWeight: 600,
                opacity: code ? 1 : 0.35,
              }}
            >
              {code ?? "—"}
            </span>
          ),
      },
      {
        title: "Descrição",
        dataIndex: "name",
        render: (name: string, row) => {
          if (row.rowKind === "NOTE") {
            return (
              <span style={{ fontStyle: "italic", color: "var(--ind-neutral-600)", fontSize: 13 }}>
                {name}
              </span>
            );
          }

          const isHeading = row.rowKind === "HEADING";
          const clamp = !isHeading && name.length > DESC_CLAMP && !expandedDescs.has(row.id);
          const shown = clamp ? `${name.slice(0, DESC_CLAMP)}…` : name;

          return (
            <div>
              <span
                style={
                  isHeading
                    ? {
                        fontFamily: "var(--ind-font-heading)",
                        fontWeight: 600,
                        textTransform: "uppercase",
                        letterSpacing: "0.04em",
                      }
                    : undefined
                }
              >
                {shown}
              </span>

              {/* Sem índice mas com dinheiro: é a variante escolhida da rubrica acima. */}
              {row.rowKind === "ITEM" && !row.code && (
                <span className="ind-tag ind-tag-outline" style={{ marginLeft: 6 }}>
                  alternativa
                </span>
              )}

              {isAdmin() && (
                <Button
                  type="text"
                  size="small"
                  icon={<EditOutlined />}
                  title="Editar datas"
                  style={{ opacity: 0.6, marginLeft: 4 }}
                  onClick={(e) => {
                    e.stopPropagation();
                    setDatesItem(row);
                  }}
                />
              )}

              {name.length > DESC_CLAMP && !isHeading && (
                <div>
                  <a
                    href="#"
                    style={{ fontSize: 11 }}
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      toggleDesc(row.id);
                    }}
                  >
                    {clamp ? "ver mais" : "ver menos"}
                  </a>
                </div>
              )}
            </div>
          );
        },
      },
      {
        title: "Un.",
        dataIndex: "unit",
        width: 64,
        render: (unit: string | null, row) => (row.rowKind === "ITEM" ? unit ?? "—" : null),
      },
      {
        title: "Quant.",
        dataIndex: "quantity",
        width: 92,
        render: (q: number | null, row) => (row.rowKind === "ITEM" ? q ?? "—" : null),
      },
      {
        title: "Preço Un.",
        dataIndex: "unitPrice",
        width: 112,
        render: (v: number | null, row) =>
          row.rowKind === "ITEM" ? (v == null ? "—" : formatCurrency(v)) : null,
      },
      {
        title: "Preço total",
        dataIndex: "totalPrice",
        width: 150,
        render: (_: number | null, row) => {
          // Títulos e notas não mostram valores — uma célula com "—" sugeriria zero.
          if (row.rowKind !== "ITEM") return null;

          const value = row.totalPrice ?? row.rolledUpBudget;
          return (
            <div
              style={{ cursor: row.acceptsExpenses ? "pointer" : undefined }}
              onClick={(e) => {
                e.stopPropagation();
                if (row.acceptsExpenses) setExpensesItem(row);
              }}
            >
              <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                {value == null ? "—" : formatCurrency(value)}
              </span>

              {/* O total escrito no Excel não bate certo com a soma do detalhe. */}
              {row.budgetMismatch && (
                <span
                  title={`Difere do detalhe em ${formatCurrency(row.budgetVariance ?? 0)}`}
                  style={{ color: "var(--ind-accent-700)", cursor: "help", marginLeft: 3 }}
                >
                  †
                </span>
              )}

              {row.overBudget && (
                <div style={{ fontSize: 11, color: "#b53333" }}>acima do orçamento</div>
              )}
              {!row.overBudget && row.spentTotal > 0 && (
                <div style={{ fontSize: 11, color: "#b53333" }}>
                  {formatCurrency(row.spentTotal)} gasto
                </div>
              )}
            </div>
          );
        },
      },
    ],
    [expandedDescs, isAdmin]
  );

  const totals = tree;

  return (
    <div>
      <a
        href="#"
        onClick={(e) => {
          e.preventDefault();
          navigate("/backoffice/empreendimentos");
        }}
        style={{
          fontSize: 12,
          display: "inline-flex",
          alignItems: "center",
          gap: 4,
          color: "var(--ind-accent-700)",
          marginBottom: "6.8px",
        }}
      >
        <ArrowLeftOutlined style={{ fontSize: 11 }} />
        {tree?.enterpriseName ?? "Projetos"}
      </a>

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-end",
          marginBottom: "20.4px",
        }}
      >
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Empreendimento</h6>
          <h1 style={{ margin: 0 }}>Orçamento de Obra</h1>
        </div>
        {isAdmin() && (
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Importar Excel
          </Button>
        )}
      </div>

      {/* Três números fixos; as excepções só aparecem quando existem. */}
      <div
        style={{
          display: "flex",
          gap: "10.2px",
          alignItems: "stretch",
          marginBottom: "20.4px",
          flexWrap: "wrap",
        }}
      >
        <MetricCard label="Orçamento" value={formatCurrency(totals?.budgetTotal ?? 0)} />
        <MetricCard label="Gasto" value={formatCurrency(totals?.spentTotal ?? 0)} />
        <MetricCard
          label="Restante"
          value={formatCurrency(totals?.remaining ?? 0)}
          meta={
            totals?.percentSpent != null ? `${totals.percentSpent.toFixed(1)}% do orçamento` : undefined
          }
        />

        {totals && (
          <div style={{ display: "flex", flexDirection: "column", gap: 6, justifyContent: "center" }}>
            {totals.overBudgetCount > 0 && (
              <span
                className="ind-tag ind-tag-outline"
                style={{ color: "#b53333", borderColor: "#b53333", whiteSpace: "nowrap" }}
              >
                {totals.overBudgetCount} rubrica(s) acima · {formatCurrency(totals.overBudgetAmount)}
              </span>
            )}
            {totals.missingInvoiceCount > 0 && (
              <span className="ind-tag ind-tag-outline" style={{ whiteSpace: "nowrap" }}>
                {totals.missingInvoiceCount} despesa(s) sem fatura
              </span>
            )}
            {totals.pendingAccountantCount > 0 && (
              <span className="ind-tag ind-tag-outline" style={{ whiteSpace: "nowrap" }}>
                {totals.pendingAccountantCount} por enviar ·{" "}
                {formatCurrency(totals.pendingAccountantTotal)}
              </span>
            )}
          </div>
        )}
      </div>

      <div style={{ display: "flex", gap: "10.2px", alignItems: "center", marginBottom: "13.6px" }}>
        <Input
          placeholder="Pesquisar rubricas…"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          allowClear
          style={{ maxWidth: 320 }}
        />
        <Button onClick={() => setExpandedKeys(flattenTree(visibleRoots).map((n) => n.id))}>
          Expandir tudo
        </Button>
        <Button onClick={() => setExpandedKeys([])}>Recolher tudo</Button>
      </div>

      <div style={{ borderTop: "1px solid var(--ind-color-divider)", overflowX: "auto" }}>
        <Spin spinning={loading}>
          <Table<BudgetItemNode>
            rowKey="id"
            columns={columns}
            dataSource={visibleRoots}
            pagination={false}
            size="small"
            style={{ minWidth: 900 }}
            expandable={{
              expandedRowKeys: expandedKeys,
              onExpandedRowsChange: (keys) => setExpandedKeys(keys as string[]),
            }}
            onRow={(row) => ({
              onClick: () => {
                if (row.acceptsExpenses) setExpensesItem(row);
              },
              style: row.acceptsExpenses ? { cursor: "pointer" } : undefined,
            })}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={query ? "Sem rubricas para esta pesquisa" : "Sem orçamento importado"}
                />
              ),
            }}
          />
        </Spin>
      </div>

      <p style={{ fontSize: 12, opacity: 0.6, marginTop: "10.2px" }}>
        {visibleCount} rubrica(s) visível(eis) de {tree?.itemCount ?? 0}
      </p>

      <BudgetExpensesDrawer
        item={expensesItem}
        enterpriseId={enterpriseId ?? ""}
        open={!!expensesItem}
        onClose={() => setExpensesItem(null)}
        onChanged={fetchTree}
      />

      <BudgetDatesDrawer
        item={datesItem}
        enterpriseId={enterpriseId ?? ""}
        ancestorsMissing={datesItem && tree ? collectAncestorsMissingDates(tree.roots, datesItem.id) : null}
        open={!!datesItem}
        onClose={() => setDatesItem(null)}
        onSaved={fetchTree}
      />

      <BudgetImportModal
        open={importOpen}
        enterpriseId={enterpriseId ?? ""}
        existingItemCount={tree?.itemCount ?? 0}
        onClose={() => setImportOpen(false)}
        onImported={fetchTree}
      />
    </div>
  );
};

const MetricCard: FC<{ label: string; value: string; meta?: string }> = ({ label, value, meta }) => (
  <div className="ind-card ind-blueprint ind-elev-sm" style={{ padding: "13.6px", flex: 1, minWidth: 150 }}>
    <i className="ind-corner tl" />
    <i className="ind-corner tr" />
    <i className="ind-corner bl" />
    <i className="ind-corner br" />
    <span className="ind-card-kicker">{label}</span>
    <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 28, lineHeight: 1.1 }}>{value}</div>
    {meta && <span className="ind-card-meta">{meta}</span>}
  </div>
);

export default ConstructionBudgetPage;
