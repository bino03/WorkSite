import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { Badge, Button, Empty, Input, Modal, Space, Spin, Table, Tabs, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownOutlined,
  DownloadOutlined,
  EditOutlined,
  FileTextOutlined,
  PlusOutlined,
  SearchOutlined,
  SwapOutlined,
  UndoOutlined,
  UploadOutlined,
  UpOutlined,
} from "@ant-design/icons";

import {
  createBudgetLot,
  deleteBudgetItem,
  deleteBudgetLot,
  getLotTree,
  listBudgetLots,
  listDeletedBudgetItems,
  moveBudgetItem,
  renameBudgetLot,
} from "@/services/budgetService";
import { getPendingInvoicesSummary } from "@/services/invoiceService";
import type { PendingInvoicesSummary } from "@/types/invoice";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetItemNode, BudgetLot, BudgetTree } from "@/types/budget";
import { BudgetExpensesDrawer } from "@/components/budget/BudgetExpensesDrawer";
import { BudgetItemDrawer } from "@/components/budget/BudgetItemDrawer";
import { BudgetMoveToModal } from "@/components/budget/BudgetMoveToModal";
import { BudgetRecycleBinDrawer } from "@/components/budget/BudgetRecycleBinDrawer";
import { BudgetImportModal } from "@/components/budget/BudgetImportModal";
import { BudgetExportModal } from "@/components/budget/BudgetExportModal";
import { flattenTree, matchesQuery, siblingsOf } from "@/components/budget/budgetTree";

/** Quantos caracteres de descrição mostrar antes de oferecer "ver mais". */
const DESC_CLAMP = 150;

const ConstructionBudgetPage: FC = () => {
  const { enterpriseId } = useParams<{ enterpriseId: string }>();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  // Um orçamento por lote (edifício). O lote aberto vive na URL (`?lote=`), para
  // o link da notificação de prazo e o "voltar" do browser caírem no lote certo.
  const [searchParams, setSearchParams] = useSearchParams();
  const [lots, setLots] = useState<BudgetLot[] | null>(null);
  const lotId = searchParams.get("lote") ?? lots?.[0]?.id ?? null;
  const currentLot = lots?.find((l) => l.id === lotId) ?? null;
  const [lotModal, setLotModal] = useState<{ lot: BudgetLot | null; name: string } | null>(null);

  const [tree, setTree] = useState<BudgetTree | null>(null);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<string[]>([]);
  const [expandedDescs, setExpandedDescs] = useState<Set<string>>(new Set());

  const [pending, setPending] = useState<PendingInvoicesSummary>({ count: 0, total: 0 });
  const [deletedCount, setDeletedCount] = useState(0);

  const [expensesItem, setExpensesItem] = useState<BudgetItemNode | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [recycleBinOpen, setRecycleBinOpen] = useState(false);
  const [movingItem, setMovingItem] = useState<BudgetItemNode | null>(null);
  const [itemDrawer, setItemDrawer] = useState<{ item: BudgetItemNode | null; parentId: string | null } | null>(
    null
  );

  const fetchLots = useCallback(async () => {
    if (!enterpriseId) return;
    try {
      setLots(await listBudgetLots(enterpriseId));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  }, [enterpriseId]);

  useEffect(() => {
    fetchLots();
  }, [fetchLots]);

  const fetchTree = useCallback(async () => {
    if (!lotId) {
      setTree(null);
      return;
    }
    setLoading(true);
    try {
      const data = await getLotTree(lotId);
      setTree(data);
      // Abre só os capítulos: 198 linhas abertas de uma vez não se leem.
      setExpandedKeys(data.roots.map((r) => r.id));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [lotId]);

  useEffect(() => {
    fetchTree();
  }, [fetchTree]);

  /** Depois de mexer nas rubricas: a árvore do lote e os totais das abas. */
  const refresh = useCallback(() => {
    fetchTree();
    fetchLots();
  }, [fetchTree, fetchLots]);

  const selectLot = (id: string) => {
    setQuery("");
    setSearchParams({ lote: id }, { replace: true });
  };

  const saveLot = async () => {
    if (!enterpriseId || !lotModal) return;
    const name = lotModal.name.trim();
    if (!name) return;
    try {
      const saved = lotModal.lot
        ? await renameBudgetLot(lotModal.lot.id, name)
        : await createBudgetLot(enterpriseId, name);
      setLotModal(null);
      await fetchLots();
      selectLot(saved.id);
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const confirmDeleteLot = (lot: BudgetLot) => {
    confirm({
      title: "Eliminar lote",
      message:
        lot.itemCount > 0
          ? `Eliminar "${lot.name}" e as suas ${lot.itemCount} rubrica(s)? Não vai para a zona de recuperação. Só é possível se nenhuma rubrica tiver despesas.`
          : `Eliminar "${lot.name}"? O lote não tem rubricas.`,
      onConfirm: async () => {
        try {
          await deleteBudgetLot(lot.id);
          notificationService.success("Lote", "Lote eliminado.");
          setSearchParams({}, { replace: true });
          fetchLots();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  useEffect(() => {
    if (!enterpriseId) return;
    // Falhar aqui só custa o contador — não vale um erro na cara de ninguém.
    getPendingInvoicesSummary(enterpriseId).then(setPending).catch(() => setPending({ count: 0, total: 0 }));
  }, [enterpriseId]);

  const refreshDeletedCount = useCallback(() => {
    if (!lotId || !isAdmin()) return;
    listDeletedBudgetItems(lotId)
      .then((items) => setDeletedCount(items.length))
      .catch(() => setDeletedCount(0));
  }, [lotId, isAdmin]);

  useEffect(() => {
    refreshDeletedCount();
  }, [refreshDeletedCount]);

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

  const moveSibling = async (row: BudgetItemNode, direction: "up" | "down") => {
    if (!tree) return;
    const siblings = siblingsOf(tree.roots, row.id);
    const index = siblings.findIndex((s) => s.id === row.id);
    const targetIndex = direction === "up" ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= siblings.length) return;

    try {
      // O backend lê `sortOrder` como a posição final entre os irmãos, por isso
      // vai o índice — o sortOrder do vizinho só coincide quando já está tudo
      // consecutivo.
      await moveBudgetItem(row.id, row.parentId, targetIndex);
      fetchTree();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const confirmDelete = (row: BudgetItemNode) => {
    confirm({
      title: "Eliminar rubrica",
      message:
        row.children.length > 0
          ? `Eliminar "${row.name}" e as suas ${row.children.length} sub-rubrica(s)? Ficam na zona de recuperação por 30 dias.`
          : `Eliminar "${row.name}"? Fica na zona de recuperação por 30 dias.`,
      onConfirm: async () => {
        try {
          await deleteBudgetItem(row.id);
          notificationService.success("Rubrica", "Rubrica eliminada — pode recuperá-la em \"Eliminadas\".");
          fetchTree();
          refreshDeletedCount();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

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
        width: 150,
        // O código nunca parte em duas linhas — com a indentação da árvore, a
        // "4.2.1" ficava "4.2." numa linha e "1" na outra e a hierarquia
        // deixava de se ler (apontado pelo utilizador a 2026-09-21). O prefixo
        // da mãe fica esbatido e só o último segmento a negrito: "4.2." + "1".
        render: (code: string | null, row) => {
          if (row.rowKind === "NOTE") return null;
          if (!code) {
            return (
              <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, opacity: 0.35 }}>
                —
              </span>
            );
          }
          const cut = code.lastIndexOf(".") + 1;
          return (
            <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, whiteSpace: "nowrap" }}>
              {cut > 0 && <span style={{ opacity: 0.45, fontWeight: 500 }}>{code.slice(0, cut)}</span>}
              {code.slice(cut)}
            </span>
          );
        },
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
      ...(isAdmin()
        ? [
            {
              title: "",
              key: "actions",
              width: 150,
              render: (_: unknown, row: BudgetItemNode) => {
                const siblings = tree ? siblingsOf(tree.roots, row.id) : [];
                const index = siblings.findIndex((s) => s.id === row.id);
                return (
                  <Space size={2} onClick={(e) => e.stopPropagation()}>
                    <Tooltip title="Editar">
                      <Button
                        type="text"
                        size="small"
                        icon={<EditOutlined />}
                        onClick={() => setItemDrawer({ item: row, parentId: row.parentId })}
                      />
                    </Tooltip>
                    {row.rowKind !== "NOTE" && (
                      <Tooltip title="Nova sub-rubrica">
                        <Button
                          type="text"
                          size="small"
                          icon={<PlusOutlined />}
                          onClick={() => setItemDrawer({ item: null, parentId: row.id })}
                        />
                      </Tooltip>
                    )}
                    <Tooltip title="Mover para…">
                      <Button
                        type="text"
                        size="small"
                        icon={<SwapOutlined />}
                        onClick={() => setMovingItem(row)}
                      />
                    </Tooltip>
                    <Tooltip title="Subir">
                      <Button
                        type="text"
                        size="small"
                        icon={<UpOutlined />}
                        disabled={index <= 0}
                        onClick={() => moveSibling(row, "up")}
                      />
                    </Tooltip>
                    <Tooltip title="Descer">
                      <Button
                        type="text"
                        size="small"
                        icon={<DownOutlined />}
                        disabled={index < 0 || index >= siblings.length - 1}
                        onClick={() => moveSibling(row, "down")}
                      />
                    </Tooltip>
                    <Tooltip title="Eliminar">
                      <Button
                        type="text"
                        size="small"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => confirmDelete(row)}
                      />
                    </Tooltip>
                  </Space>
                );
              },
            } as ColumnsType<BudgetItemNode>[number],
          ]
        : []),
    ],
    [expandedDescs, isAdmin, tree]
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
        <Space>
          {/* O contador é o que faz alguém lembrar-se de ir classificar. */}
          <Badge count={pending.count} overflowCount={99} offset={[-4, 2]}>
            <Button
              icon={<FileTextOutlined />}
              onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/invoices`)}
            >
              Faturas
            </Button>
          </Badge>
          {isAdmin() && (
            <Badge count={deletedCount} overflowCount={99} offset={[-4, 2]}>
              <Button icon={<UndoOutlined />} onClick={() => setRecycleBinOpen(true)}>
                Eliminadas
              </Button>
            </Badge>
          )}
          {isAdmin() && (
            <Button icon={<PlusOutlined />} onClick={() => setLotModal({ lot: null, name: "" })}>
              Novo lote
            </Button>
          )}
          {isAdmin() && tree && (
            <Button
              icon={<PlusOutlined />}
              onClick={() => setItemDrawer({ item: null, parentId: null })}
            >
              Nova rubrica
            </Button>
          )}
          {/* Importar por cima de um orçamento existente duplicava a árvore toda. O
              backend recusa na mesma (`BUDGET_IMPORT_NOT_EMPTY`); aqui tira-se o botão
              da frente. Com `tree` ainda por carregar não aparece, para não piscar. */}
          {isAdmin() && tree?.roots.length === 0 && (
            <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              Importar Excel
            </Button>
          )}
          {/* É leitura: quem vê o orçamento pode levá-lo. Sem `tree` não aparece,
              pelo mesmo motivo do botão de importar. */}
          {tree && (
            <Button icon={<DownloadOutlined />} onClick={() => setExportOpen(true)}>
              Exportar Excel
            </Button>
          )}
        </Space>
      </div>

      {/* Um orçamento por lote. Sem lotes, o projeto ainda não tem orçamento: cria-se
          o primeiro lote e importa-se para ele. */}
      {lots && lots.length === 0 && (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Este projeto ainda não tem orçamento. Crie um lote (edifício) e importe o orçamento para ele."
          style={{ marginBottom: "20.4px" }}
        >
          {isAdmin() && (
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setLotModal({ lot: null, name: "" })}>
              Criar lote
            </Button>
          )}
        </Empty>
      )}
      {lots && lots.length > 0 && (
        <Tabs
          activeKey={lotId ?? undefined}
          onChange={selectLot}
          items={lots.map((lot) => ({ key: lot.id, label: lot.name }))}
          tabBarExtraContent={
            isAdmin() && currentLot ? (
              <Space size={4}>
                <Tooltip title="Renomear lote">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => setLotModal({ lot: currentLot, name: currentLot.name })}
                  />
                </Tooltip>
                <Tooltip title="Eliminar lote">
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={() => confirmDeleteLot(currentLot)}
                  />
                </Tooltip>
              </Space>
            ) : undefined
          }
          style={{ marginBottom: "6.8px" }}
        />
      )}

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
        <MetricCard
          label={lots && lots.length > 1 ? `Orçamento · ${currentLot?.name ?? ""}` : "Orçamento"}
          value={formatCurrency(totals?.budgetTotal ?? 0)}
          meta={
            lots && lots.length > 1
              ? `${formatCurrency(lots.reduce((sum, l) => sum + l.budgetTotal, 0))} nos ${lots.length} lotes`
              : undefined
          }
        />
        {/* "Gasto" só conta o que já está numa rubrica; sem o resto ao lado parecia
            que o dinheiro das faturas por classificar tinha desaparecido. */}
        <MetricCard
          label="Gasto"
          value={formatCurrency(totals?.spentTotal ?? 0)}
          meta={pending.count > 0 ? `só o que está em rubricas` : undefined}
        />
        {pending.count > 0 && (
          <MetricCard
            label="Por classificar"
            value={formatCurrency(pending.total)}
            meta={`${pending.count} fatura${pending.count === 1 ? "" : "s"} fora do orçamento — total faturado ${formatCurrency(
              (totals?.spentTotal ?? 0) + pending.total
            )}`}
          />
        )}
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
              // 15px por nível (o default) engolia a coluna do código ao 3.º nível;
              // o código já diz a profundidade, o recuo só precisa de a sugerir.
              indentSize: 10,
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
        onChanged={refresh}
      />

      <BudgetItemDrawer
        open={!!itemDrawer}
        budgetId={lotId ?? ""}
        tree={tree}
        item={itemDrawer?.item ?? null}
        defaultParentId={itemDrawer?.parentId ?? null}
        onClose={() => setItemDrawer(null)}
        onSaved={() => {
          setItemDrawer(null);
          refresh();
        }}
      />

      <BudgetMoveToModal
        open={!!movingItem}
        tree={tree}
        item={movingItem}
        onClose={() => setMovingItem(null)}
        onMoved={() => {
          setMovingItem(null);
          fetchTree();
        }}
      />

      <BudgetRecycleBinDrawer
        open={recycleBinOpen}
        budgetId={lotId ?? ""}
        onClose={() => setRecycleBinOpen(false)}
        onRecovered={() => {
          refresh();
          refreshDeletedCount();
        }}
      />

      <BudgetImportModal
        open={importOpen}
        budgetId={lotId ?? ""}
        existingItemCount={tree?.itemCount ?? 0}
        onClose={() => setImportOpen(false)}
        onImported={refresh}
      />

      <Modal
        open={!!lotModal}
        title={lotModal?.lot ? "Renomear lote" : "Novo lote"}
        okText={lotModal?.lot ? "Guardar" : "Criar"}
        cancelText="Cancelar"
        okButtonProps={{ disabled: !lotModal?.name.trim() }}
        onOk={saveLot}
        onCancel={() => setLotModal(null)}
        destroyOnClose
      >
        <p style={{ fontSize: 12, opacity: 0.7 }}>
          Um lote é um edifício do empreendimento, com o seu próprio orçamento e numeração.
        </p>
        <Input
          autoFocus
          placeholder="Ex.: Lote A, Moradia 2"
          value={lotModal?.name ?? ""}
          maxLength={80}
          onChange={(e) => setLotModal((m) => (m ? { ...m, name: e.target.value } : m))}
          onPressEnter={saveLot}
        />
      </Modal>

      <BudgetExportModal
        open={exportOpen}
        enterpriseId={enterpriseId ?? ""}
        onClose={() => setExportOpen(false)}
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
