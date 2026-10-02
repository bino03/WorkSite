import { useEffect, useState } from "react";
import type { FC, ReactNode } from "react";
import { Alert, Button, Checkbox, Modal, Radio, Select, Space, Spin, Steps, Tooltip } from "antd";
import { useTranslation } from "react-i18next";

import {
  exportFolderZip,
  exportLotBudget,
  exportWorkbook,
  getExportSummary,
  listBudgetLots,
} from "@/services/budgetService";
import { getDocumentsSummary, type InvoiceDocumentsScope } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { downloadBlob } from "@/utils/downloadBlob";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetExportSheet, BudgetExportSummary, BudgetLot } from "@/types/budget";

interface Props {
  open: boolean;
  enterpriseId: string;
  /**
   * Os lotes da obra, para o download de um lote só. Opcional: a página do
   * orçamento já os tem carregados e passa-os para não os pedir outra vez; quem
   * abre o modal sem eles (a lista de obras) deixa o modal buscá-los.
   */
  lots?: BudgetLot[];
  /** O lote aberto na página: é o que o selector traz já escolhido. */
  currentLotId?: string | null;
  onClose: () => void;
}

/** O que se leva: a obra toda num livro, ou o orçamento de um lote só. */
type ExportScope = "ENTERPRISE" | "LOT";

interface SheetOption {
  value: BudgetExportSheet;
  label: string;
  hint: string;
  /** Precisa de rubricas vivas — sem orçamento fica desativada. */
  needsBudget: boolean;
}

/** A mesma ordem em que as folhas saem no livro. */
const SHEETS: SheetOption[] = [
  {
    value: "BUDGET",
    label: "Orçamento inicial",
    hint: "A árvore de rubricas, no formato que a importação volta a ler.",
    needsBudget: true,
  },
  {
    value: "EXPENSES",
    label: "Despesas",
    hint: "A TabelaDespesas do vault: faturas, pagamentos e rubrica, uma linha por despesa.",
    needsBudget: false,
  },
  {
    value: "COMPARISON",
    label: "Orçamento vs Gasto",
    hint: "O painel por capítulo, em fórmulas. Traz a folha Rubricas e obriga a incluir Despesas.",
    needsBudget: true,
  },
];

/**
 * Exportar a obra para o `Despesas - <Obra>.xlsx` do vault, em dois passos:
 * escolher as folhas, e ver o que vai sair (contagens e avisos) antes de
 * descarregar. O resumo carrega-se ao abrir, porque é ele que diz se a obra
 * tem orçamento — e sem isso duas das três folhas nem se podem escolher.
 *
 * "Incluir documentos" troca o `.xlsx` por um `<slug>.zip` com o livro e a
 * `Faturas/Lançadas/` (os documentos das faturas com o nome do vault, §7) —
 * a pasta da obra tal como vive em `Empreendimentos\<slug>\`.
 *
 * Em alternativa leva-se **um lote só**, num `Orçamento - <Obra> - <Lote>.xlsx`,
 * com as **mesmas folhas** recortadas a esse lote.
 *
 * Esse caminho não passa pelo resumo: o resumo é da obra inteira (faturas,
 * despesas, documentos) e os seus números não são os do lote — mostrá-lo antes de
 * um download de um lote era enganador. Os números do lote vão logo debaixo do
 * selector. Os documentos ficam de fora porque o `.zip` é da pasta da obra.
 */
export const BudgetExportModal: FC<Props> = ({
  open,
  enterpriseId,
  lots: lotsProp,
  currentLotId = null,
  onClose,
}) => {
  const { t } = useTranslation();
  const [step, setStep] = useState(0);
  const [selected, setSelected] = useState<BudgetExportSheet[]>(["BUDGET", "EXPENSES", "COMPARISON"]);
  const [summary, setSummary] = useState<BudgetExportSummary | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [withDocuments, setWithDocuments] = useState(false);
  const [scope, setScope] = useState<ExportScope>("ENTERPRISE");
  const [lotId, setLotId] = useState<string | null>(null);
  /** `FOLLOW` = os documentos acompanham o âmbito do livro; o resto é uma escolha explícita. */
  const [docsChoice, setDocsChoice] = useState<"FOLLOW" | InvoiceDocumentsScope>("FOLLOW");
  const [docsCount, setDocsCount] = useState<number | null>(null);
  /** Só se usa quando a página não passou os lotes. */
  const [fetchedLots, setFetchedLots] = useState<BudgetLot[]>([]);
  const lots = lotsProp ?? fetchedLots;

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setSummary(null);
    setWithDocuments(false);
    setScope("ENTERPRISE");
    setDocsChoice("FOLLOW");
    setDocsCount(null);
    // começa no lote que está aberto na página, não no primeiro da lista
    setLotId(currentLotId ?? lotsProp?.[0]?.id ?? null);
    if (!lotsProp) {
      setFetchedLots([]);
      // falhar a lista de lotes não estraga a exportação da obra, que é o caminho
      // principal deste modal — só desativa o "um lote só"
      listBudgetLots(enterpriseId)
        .then((loaded) => {
          setFetchedLots(loaded);
          setLotId((current) => current ?? loaded[0]?.id ?? null);
        })
        .catch(() => setFetchedLots([]));
    }
    let cancelled = false;
    setLoadingSummary(true);
    getExportSummary(enterpriseId)
      .then((loaded) => {
        if (cancelled) return;
        setSummary(loaded);
        // Sem orçamento, as folhas que dependem dele saem da seleção por omissão.
        setSelected(
          loaded.hasBudget
            ? ["BUDGET", "EXPENSES", "COMPARISON"]
            : ["EXPENSES"]
        );
      })
      .catch((error) => {
        if (cancelled) return;
        ErrorHandler.handle(error);
        onClose();
      })
      .finally(() => {
        if (!cancelled) setLoadingSummary(false);
      });
    return () => {
      cancelled = true;
    };
    // `onClose` é estável na página; reagir só ao abrir/mudar de obra.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, enterpriseId]);

  const hasBudget = summary?.hasBudget ?? false;
  const includesComparison = selected.includes("COMPARISON");

  const toggle = (sheet: BudgetExportSheet, checked: boolean) => {
    setSelected((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(sheet);
        // o painel é fórmulas sobre a TabelaDespesas — sem ela dava #NAME?
        if (sheet === "COMPARISON") next.add("EXPENSES");
      } else {
        next.delete(sheet);
      }
      return SHEETS.map((s) => s.value).filter((s) => next.has(s));
    });
  };

  const lot = lots.find((l) => l.id === lotId) ?? null;
  const lotScope = scope === "LOT";

  /**
   * Que documentos acompanham o livro. O padrão **segue o âmbito do livro** — é a
   * escolha que quase sempre se quer: o Excel de um lote com os PDFs desse lote, o
   * da obra com os da obra. Pode-se trocar, e trocar para outra coisa que não
   * "todos" tira ao zip o nome da pasta do vault, de propósito.
   */
  const docsScope: InvoiceDocumentsScope = docsChoice === "FOLLOW"
    ? (lotScope ? "ASSOCIATED" : "ALL")
    : docsChoice;
  const docsBudgetId = docsScope === "ASSOCIATED" && lotScope ? (lotId ?? undefined) : undefined;

  // quantos documentos o âmbito escolhido traz — só se pergunta quando se vão levar
  useEffect(() => {
    if (!open || !withDocuments) {
      setDocsCount(null);
      return;
    }
    let cancelled = false;
    getDocumentsSummary(enterpriseId, docsScope, docsBudgetId)
      .then((s) => {
        if (!cancelled) setDocsCount(s.documentCount);
      })
      .catch(() => {
        if (!cancelled) setDocsCount(null);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, withDocuments, enterpriseId, docsScope, docsBudgetId]);

  const download = async () => {
    if (!summary) return;
    setDownloading(true);
    try {
      const file = withDocuments
        ? await exportFolderZip(enterpriseId, selected, `${summary.enterpriseName}.zip`, {
            docs: docsScope,
            docsBudgetId,
          })
        : await exportWorkbook(enterpriseId, selected, summary.fileName);
      downloadBlob(file.blob, file.fileName);
      notificationService.success("Exportação", `Ficheiro "${file.fileName}" gerado.`);
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setDownloading(false);
    }
  };

  const wantsBudget = selected.includes("BUDGET") || includesComparison;
  const wantsExpenses = selected.includes("EXPENSES");

  const downloadLot = async () => {
    if (!lot) return;
    setDownloading(true);
    try {
      // com documentos é um zip (livro do lote + PDFs); sem eles, só o .xlsx
      const file = withDocuments
        ? await exportFolderZip(
            enterpriseId,
            selected,
            `${summary?.enterpriseName ?? ""} - ${lot.name}.zip`,
            { budgetId: lot.id, docs: docsScope, docsBudgetId }
          )
        : await exportLotBudget(
        lot.id,
        selected,
        `Orçamento - ${summary?.enterpriseName ?? ""} - ${lot.name}.xlsx`
      );
      downloadBlob(file.blob, file.fileName);
      notificationService.success("Exportação", `Ficheiro "${file.fileName}" gerado.`);
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      centered
      width={620}
      title="Exportar para Excel"
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          {step === 0 ? (
            <>
              <Button onClick={onClose}>{t("common.cancel")}</Button>
              {lotScope ? (
                <Button
                  type="primary"
                  onClick={downloadLot}
                  loading={downloading}
                  disabled={!lot || lot.itemCount === 0 || selected.length === 0}
                >
                  Descarregar
                </Button>
              ) : (
                <Button
                  type="primary"
                  onClick={() => setStep(1)}
                  disabled={loadingSummary || !summary || selected.length === 0}
                >
                  Continuar
                </Button>
              )}
            </>
          ) : (
            <>
              <Button onClick={() => setStep(0)} disabled={downloading}>
                Voltar
              </Button>
              <Button type="primary" onClick={download} loading={downloading} disabled={!summary}>
                Descarregar
              </Button>
            </>
          )}
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Steps
          size="small"
          current={step}
          items={[{ title: "Folhas" }, { title: "Resumo e download" }]}
        />

        {loadingSummary && (
          <div style={{ textAlign: "center", padding: "10.2px" }}>
            <Spin /> <span style={{ fontSize: 13, marginLeft: 8 }}>A preparar a exportação…</span>
          </div>
        )}

        {summary && step === 0 && (
          <>
            <Radio.Group
              value={scope}
              onChange={(e) => setScope(e.target.value as ExportScope)}
              style={{ display: "flex", flexDirection: "column", gap: "6.8px" }}
            >
              <Radio value="ENTERPRISE">
                <span style={{ fontWeight: 600 }}>A obra toda</span>
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  Um livro com as folhas que escolheres — é o ficheiro que vai para o vault.
                </div>
              </Radio>
              <Radio value="LOT" disabled={lots.length === 0}>
                <span style={{ fontWeight: 600 }}>Um lote só</span>
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  Um ficheiro por lote, com a obra e o lote no nome. As folhas são as mesmas,
                  recortadas a esse lote.
                </div>
              </Radio>
            </Radio.Group>

            {lotScope && (
              <div style={{ display: "flex", flexDirection: "column", gap: "6.8px" }}>
                <Select
                  value={lotId ?? undefined}
                  onChange={setLotId}
                  placeholder="Escolher o lote"
                  options={lots.map((l) => ({
                    value: l.id,
                    label: l.itemCount === 0 ? `${l.name} (sem rubricas)` : l.name,
                    disabled: l.itemCount === 0,
                  }))}
                />
                {lot && lot.itemCount > 0 && (
                  <div style={{ fontSize: 12, opacity: 0.6 }}>
                    {lot.itemCount} rubrica(s) · {formatCurrency(lot.budgetTotal)} ·{" "}
                    {`Orçamento - ${summary.enterpriseName} - ${lot.name}.xlsx`}
                  </div>
                )}
                {lot && lot.itemCount === 0 && (
                  <Alert
                    type="info"
                    showIcon
                    message="Este lote não tem rubricas — não há orçamento para exportar."
                  />
                )}
              </div>
            )}

            <div style={{ display: "flex", flexDirection: "column", gap: "6.8px" }}>
              {SHEETS.map((sheet) => {
                // no modo lote o que decide é o lote ter rubricas, não a obra
                const blocked = sheet.needsBudget && (lotScope ? !lot || lot.itemCount === 0 : !hasBudget);
                const forced = sheet.value === "EXPENSES" && includesComparison;
                const box = (
                  <Checkbox
                    checked={selected.includes(sheet.value)}
                    disabled={blocked || forced}
                    onChange={(e) => toggle(sheet.value, e.target.checked)}
                  >
                    <span style={{ fontWeight: 600 }}>{sheet.label}</span>
                    <div style={{ fontSize: 12, opacity: 0.6 }}>{sheet.hint}</div>
                  </Checkbox>
                );
                return (
                  <div key={sheet.value}>
                    {blocked ? (
                      <Tooltip title={lotScope ? "Este lote não tem rubricas." : "Esta obra não tem orçamento."}>
                        {box}
                      </Tooltip>
                    ) : forced ? (
                      <Tooltip title="Obrigatória com o painel Orçamento vs Gasto.">{box}</Tooltip>
                    ) : (
                      box
                    )}
                  </div>
                );
              })}
            </div>
            {!hasBudget && (
              <Alert
                type="info"
                showIcon
                message="Sem orçamento, só a folha Despesas pode ser exportada."
              />
            )}
            {lotScope && (
              <Alert
                type="info"
                showIcon
                message="Na Despesas e no painel entram só as rubricas deste lote — as dos outros
                  lotes e as faturas por classificar ficam de fora, por isso o total não é o da obra."
              />
            )}

            <div style={{ borderTop: "1px solid var(--ind-color-divider)", paddingTop: "6.8px" }}>
              <Checkbox
                checked={withDocuments}
                disabled={summary.documents.documentCount === 0}
                onChange={(e) => setWithDocuments(e.target.checked)}
              >
                <span style={{ fontWeight: 600 }}>Incluir os PDFs das faturas (tudo num .zip)</span>
                <div style={{ fontSize: 12, opacity: 0.6 }}>
                  {summary.documents.documentCount === 0
                    ? "Nenhuma fatura desta obra tem ficheiro — só o Excel."
                    : "O Excel mais os documentos em Faturas/Lançadas/, com o nome do vault."}
                </div>
              </Checkbox>

              {withDocuments && (
                <div style={{ marginLeft: 24, marginTop: "6.8px", display: "flex", flexDirection: "column", gap: "6.8px" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <span style={{ fontSize: 13, opacity: 0.7 }}>quais:</span>
                    <Select
                      value={docsChoice}
                      onChange={setDocsChoice}
                      style={{ minWidth: 280 }}
                      options={[
                        {
                          value: "FOLLOW",
                          label: lotScope
                            ? `as do lote escolhido (${lot?.name ?? "—"})`
                            : "todas as faturas da obra",
                        },
                        { value: "ALL", label: "todas as faturas da obra" },
                        { value: "ASSOCIATED", label: "só as já associadas a rubricas" },
                        { value: "UNCLASSIFIED", label: "só as que ainda não estão associadas" },
                      ]}
                    />
                  </div>
                  <div style={{ fontSize: 12, opacity: 0.6 }}>
                    {docsCount === null
                      ? "A contar…"
                      : docsCount === 0
                        ? "Este âmbito não tem documentos — o zip vai só com o Excel."
                        : `${docsCount} ficheiro(s).`}
                    {!lotScope && docsChoice === "FOLLOW" && (
                      <> Com tudo da obra, o zip é a pasta do vault (<code>&lt;obra&gt;.zip</code>).</>
                    )}
                  </div>
                </div>
              )}
            </div>
          </>
        )}

        {summary && step === 1 && (
          <>
            <div className="ind-card ind-blueprint" style={{ padding: "13.6px", gap: "10.2px" }}>
              <i className="ind-corner tl" />
              <i className="ind-corner tr" />
              <i className="ind-corner bl" />
              <i className="ind-corner br" />
              <span className="ind-card-kicker">
                {summary.fileName}
                {withDocuments && " + Faturas/Lançadas/ (num .zip)"}
              </span>
              <div style={{ fontSize: 12, opacity: 0.6 }}>
                Folhas: {SHEETS.filter((s) => selected.includes(s.value)).map((s) => s.label).join(" · ")}
                {includesComparison ? " · Rubricas" : ""}
              </div>

              {wantsBudget && (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10.2px", fontSize: 14 }}>
                  <Stat label="Rubricas">{summary.budgetItemCount}</Stat>
                  <Stat label="Capítulos">{summary.chapterCount}</Stat>
                  <Stat label="Orçamento total">
                    <strong style={{ fontFamily: "var(--ind-font-heading)" }}>
                      {formatCurrency(summary.budgetTotal)}
                    </strong>
                  </Stat>
                </div>
              )}

              {wantsExpenses && (
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "10.2px", fontSize: 14 }}>
                  <Stat label="Faturas">{summary.invoiceCount}</Stat>
                  <Stat label="Linhas na folha">{summary.expenseRowCount}</Stat>
                  <Stat label="Total lançado">
                    <strong style={{ fontFamily: "var(--ind-font-heading)" }}>
                      {formatCurrency(summary.expensesTotal)}
                    </strong>
                  </Stat>
                </div>
              )}
            </div>

            {wantsExpenses && (
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, opacity: 0.8 }}>
                {summary.unclassifiedInvoiceCount > 0 && (
                  <li>{summary.unclassifiedInvoiceCount} fatura(s) sem rubrica — saem com a coluna Rubrica vazia.</li>
                )}
                {summary.manualExpenseCount > 0 && (
                  <li>{summary.manualExpenseCount} despesa(s) registada(s) à mão — saem sem nº de fatura.</li>
                )}
                {summary.creditNoteCount > 0 && (
                  <li>{summary.creditNoteCount} nota(s) de crédito — saem com valor negativo.</li>
                )}
                {summary.partialPaymentCount > 0 && (
                  <li>{summary.partialPaymentCount} fatura(s) parcialmente paga(s) — saem por liquidar, com o pagamento nas observações.</li>
                )}
                {summary.missingNumberCount > 0 && (
                  <li>{summary.missingNumberCount} fatura(s) sem número.</li>
                )}
                {summary.needsReviewCount > 0 && (
                  <li>{summary.needsReviewCount} fatura(s) sem data ou sem total — por rever.</li>
                )}
              </ul>
            )}

            {withDocuments && (
              <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, opacity: 0.8 }}>
                <li>
                  {summary.documents.documentCount} documento(s) em Faturas/Lançadas/
                  {summary.documents.renamedCount > 0 &&
                    ` — ${summary.documents.renamedCount} com nome gerado (data_nº_Fornecedor), os outros mantêm o nome do vault`}
                  .
                </li>
                {summary.documents.invoicesWithoutDocument > 0 && (
                  <li>{summary.documents.invoicesWithoutDocument} fatura(s) sem ficheiro — não têm nada a exportar.</li>
                )}
              </ul>
            )}

            {summary.isTest && (
              <Alert
                type="info"
                showIcon
                message="Obra de teste"
                description="O ficheiro leva o prefixo TESTE e não deve entrar no vault."
              />
            )}

            {(summary.warnings.length > 0 || (withDocuments && summary.documents.warnings.length > 0)) && (
              <Alert
                type="warning"
                showIcon
                message={`${summary.warnings.length + (withDocuments ? summary.documents.warnings.length : 0)} aviso(s)`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, maxHeight: 160, overflowY: "auto" }}>
                    {[...summary.warnings, ...(withDocuments ? summary.documents.warnings : [])].map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                }
              />
            )}
          </>
        )}
      </div>
    </Modal>
  );
};

const Stat: FC<{ label: string; children: ReactNode }> = ({ label, children }) => (
  <div>
    <div style={{ fontSize: 11, opacity: 0.55 }}>{label}</div>
    {children}
  </div>
);
