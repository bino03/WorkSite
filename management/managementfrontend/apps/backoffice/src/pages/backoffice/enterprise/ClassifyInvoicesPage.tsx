import { useCallback, useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Button, Empty, Spin, Tooltip } from "antd";
import { ArrowLeftOutlined, BulbOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import RubricSearchField from "@/components/invoices/RubricSearchField";
import InvoiceSplitEditor from "@/components/invoices/InvoiceSplitEditor";
import { rubricLabel, splitIsValid } from "@/components/invoices/invoiceSplit";
import type { DraftSplitLine } from "@/components/invoices/invoiceSplit";
import {
  allocateInvoice,
  getInvoice,
  getRubricSuggestion,
  listInvoices,
  splitInvoice,
} from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency, formatDate } from "@/utils/formatters";
import type { BudgetItemSearchResult } from "@/types/budget";
import type { ConstructionInvoice, RubricSuggestion } from "@/types/invoice";

/** Quantas faturas se trazem de uma vez — a fila é para despachar, não para paginar. */
const QUEUE_SIZE = 50;

const emptyLine: DraftSplitLine = { budgetItemId: null, label: null, amount: null };

const labelOf = (item: BudgetItemSearchResult) => rubricLabel(item.code, item.name);

/**
 * O ecrã de despachar faturas por classificar, uma obra de cada vez.
 *
 * É uma **fila**, não uma tabela: a fatura à esquerda com o documento à vista, a
 * rubrica à direita, e a seguinte assim que se confirma. A página de faturas
 * continua a existir para procurar e arquivar; aqui só se faz uma coisa.
 *
 * O que faz a diferença face a associar pela lista:
 *
 * - **o documento está sempre ao lado** — classificar sem ver o papel é adivinhar;
 * - **a sugestão diz porquê** (`explanation` vem pronta do backend), e por isso
 *   pode ser aceite com confiança ou recusada com conhecimento;
 * - **"igual à anterior"** cobre o caso real de despachar um lote do mesmo
 *   fornecedor, sem repetir a pesquisa;
 * - **nada é gravado sem confirmação** (decisão 8 do Vilatro).
 */
const ClassifyInvoicesPage: FC = () => {
  const { enterpriseId = "" } = useParams<{ enterpriseId: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [queue, setQueue] = useState<ConstructionInvoice[]>([]);
  const [index, setIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  /** A fatura aberta, com `fileUrl` assinado — só o detalhe o traz. */
  const [invoice, setInvoice] = useState<ConstructionInvoice | null>(null);
  const [suggestion, setSuggestion] = useState<RubricSuggestion | null>(null);

  const [lines, setLines] = useState<DraftSplitLine[]>([emptyLine]);
  const [editingIndex, setEditingIndex] = useState(0);
  /** A rubrica da última fatura confirmada nesta sessão — o "igual à anterior". */
  const [previous, setPrevious] = useState<{ id: string; label: string } | null>(null);

  const current = queue[index] ?? null;
  const total = invoice?.totalAmount ?? null;
  const splitting = lines.length > 1;

  const loadQueue = useCallback(async () => {
    setLoading(true);
    try {
      const page = await listInvoices(enterpriseId, {
        allocated: false,
        needsReview: null,
        outstanding: null,
        sentToAccountant: null,
        from: null,
        to: null,
        q: "",
        page: 0,
        size: QUEUE_SIZE,
      });
      setQueue(page.content);
      setIndex(0);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [enterpriseId]);

  useEffect(() => {
    void loadQueue();
  }, [loadQueue]);

  // Ao mudar de fatura: traz o detalhe (para o documento) e pede a sugestão.
  useEffect(() => {
    if (!current) {
      setInvoice(null);
      setSuggestion(null);
      return;
    }
    let cancelled = false;
    // Limpa já a fatura anterior. Sem isto há um instante — entre trocar de
    // fatura e o `getInvoice` responder — em que o `total` ainda é o da fatura
    // de trás e as linhas já são as da nova: o aviso dizia "a repartição tem de
    // somar 150 €" numa fatura de 300 €. É a mesma armadilha que o
    // `InvoiceDetailDrawer` já documentava.
    setInvoice(null);
    setSuggestion(null);
    setLines([{ ...emptyLine, amount: current.totalAmount }]);
    setEditingIndex(0);

    void (async () => {
      try {
        const [detail, hint] = await Promise.all([
          getInvoice(current.id),
          getRubricSuggestion(current.id),
        ]);
        if (cancelled) return;
        setInvoice(detail);
        setSuggestion(hint);
        // A sugestão vem **pré-selecionada**, nunca gravada: poupa o gesto sem
        // tirar a decisão a ninguém.
        if (hint) {
          setLines([
            {
              budgetItemId: hint.budgetItemId,
              label: rubricLabel(hint.code, hint.name),
              amount: detail.totalAmount,
            },
          ]);
        }
      } catch (error) {
        if (!cancelled) ErrorHandler.handle(error);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [current]);

  const pickRubric = (item: BudgetItemSearchResult) =>
    setLines((prev) =>
      prev.map((line, i) =>
        i === editingIndex ? { ...line, budgetItemId: item.id, label: labelOf(item) } : line
      )
    );

  const usePrevious = () => {
    if (!previous) return;
    setLines([{ budgetItemId: previous.id, label: previous.label, amount: total }]);
    setEditingIndex(0);
  };

  const advance = () => {
    setIndex((i) => i + 1);
  };

  const confirm = async () => {
    if (!invoice) return;
    setSaving(true);
    try {
      // Uma só rubrica passa pelo `allocate` de sempre; N pelo `split`. Não é
      // só elegância: o `allocate` é o caminho que o resto da app já usa, e
      // mandar tudo pelo `split` mudaria o comportamento do caso normal.
      if (splitting) {
        await splitInvoice(
          invoice.id,
          lines.map((line) => ({ budgetItemId: line.budgetItemId as string, amount: line.amount }))
        );
      } else {
        await allocateInvoice(invoice.id, lines[0].budgetItemId as string);
      }

      const chosen = lines[0];
      if (!splitting && chosen.budgetItemId && chosen.label) {
        setPrevious({ id: chosen.budgetItemId, label: chosen.label });
      }
      notificationService.success(t("invoices.classify.confirmed"));
      advance();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const canConfirm = useMemo(
    () => !saving && invoice != null && splitIsValid(lines, total),
    [saving, invoice, lines, total]
  );

  const done = index >= queue.length;

  return (
    <div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-end",
          marginBottom: "20.4px",
        }}
      >
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
          <h1 style={{ margin: 0 }}>{t("invoices.classify.title")}</h1>
        </div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          {queue.length > 0 && (
            <span style={{ fontSize: 13, opacity: 0.7 }}>
              {t("invoices.classify.queue", {
                done: Math.min(index + 1, queue.length),
                total: queue.length,
              })}
            </span>
          )}
          <Button
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/invoices`)}
          >
            Voltar às faturas
          </Button>
        </div>
      </div>

      {/* Também gira entre faturas: a fatura foi limpa e a seguinte ainda não
          chegou, e um ecrã em branco a meio da fila parece uma avaria. */}
      <Spin spinning={loading || (!done && queue.length > 0 && invoice === null)}>
        {!loading && queue.length === 0 && (
          <Empty description={t("invoices.classify.empty")} />
        )}

        {!loading && queue.length > 0 && done && (
          <Empty description={t("invoices.classify.empty")}>
            <Button type="primary" onClick={() => void loadQueue()}>
              Recarregar
            </Button>
          </Empty>
        )}

        {!done && invoice && (
          <div style={{ display: "flex", gap: "20.4px", alignItems: "flex-start" }}>
            {/* O documento — sempre à vista, não atrás de um clique. */}
            <div
              style={{
                flex: "1 1 55%",
                position: "sticky",
                top: 0,
                maxHeight: "calc(100vh - 200px)",
                overflowY: "auto",
                borderRadius: 2,
                background: "var(--ind-color-surface)",
              }}
            >
              {invoice.fileUrl ? (
                invoice.mimeType?.startsWith("image/") ? (
                  <img
                    src={invoice.fileUrl}
                    alt={invoice.originalFilename ?? "fatura"}
                    style={{ width: "100%", display: "block" }}
                  />
                ) : (
                  <embed
                    src={invoice.fileUrl}
                    type="application/pdf"
                    style={{ width: "100%", height: "calc(100vh - 200px)", display: "block" }}
                  />
                )
              ) : (
                <div style={{ padding: 24, fontSize: 13, opacity: 0.6 }}>
                  Esta fatura não tem documento — foi registada à mão.
                </div>
              )}
            </div>

            <div style={{ flex: "1 1 45%", minWidth: 0, display: "flex", flexDirection: "column", gap: "13.6px" }}>
              <div className="ind-card" style={{ padding: "13.6px", gap: 6 }}>
                <span className="ind-card-kicker">
                  {invoice.supplierName ?? invoice.supplierNif ?? "sem fornecedor"}
                </span>
                <div style={{ fontSize: 15, fontWeight: 600 }}>
                  {invoice.invoiceNumber ?? "sem número"} ·{" "}
                  {total != null ? formatCurrency(total) : "sem total"}
                </div>
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  {invoice.invoiceDate ? formatDate(invoice.invoiceDate) : "sem data"}
                  {invoice.description ? ` · ${invoice.description}` : ""}
                </div>
              </div>

              {/* Sugestão, com o porquê — o que a torna aceitável sem pensar duas vezes. */}
              {suggestion ? (
                <Alert
                  type="info"
                  showIcon
                  icon={<BulbOutlined />}
                  message={
                    <span>
                      <strong>{t("invoices.classify.suggestion")}</strong>{" "}
                      {/* Cortado como os botões: a descrição da rubrica no
                          orçamento real é um parágrafo de especificação, e em
                          bruto enchia o painel todo. O nome completo fica no
                          `title`, e o caminho aparece na lista de pesquisa. */}
                      <span title={suggestion.name}>
                        {rubricLabel(suggestion.code, suggestion.name, 70)}
                      </span>{" "}
                      <span className="ind-tag ind-tag-neutral" style={{ fontSize: 10 }}>
                        {t(`invoices.classify.source${suggestion.source}`)}
                      </span>
                    </span>
                  }
                  description={suggestion.explanation}
                />
              ) : (
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  {t("invoices.classify.noSuggestion")}
                </div>
              )}

              <InvoiceSplitEditor
                lines={lines}
                total={total}
                editingIndex={editingIndex}
                onEditLine={setEditingIndex}
                onChange={setLines}
              />

              <RubricSearchField
                enterpriseId={enterpriseId}
                selectedId={lines[editingIndex]?.budgetItemId}
                onPick={pickRubric}
              />

              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <Button type="primary" onClick={() => void confirm()} loading={saving} disabled={!canConfirm}>
                  {t("invoices.classify.confirm")}
                </Button>
                <Button onClick={advance} disabled={saving}>
                  {t("invoices.classify.skip")}
                </Button>
                {previous && (
                  <Tooltip title={t("invoices.classify.sameAsPreviousHint")}>
                    <Button onClick={usePrevious} disabled={saving}>
                      {t("invoices.classify.sameAsPrevious")} · {previous.label}
                    </Button>
                  </Tooltip>
                )}
              </div>
            </div>
          </div>
        )}
      </Spin>
    </div>
  );
};

export default ClassifyInvoicesPage;
