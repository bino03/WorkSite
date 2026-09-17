import { useEffect, useState } from "react";
import type { FC, ReactNode } from "react";
import { Alert, Button, Checkbox, Modal, Space, Spin, Steps, Tooltip } from "antd";
import { useTranslation } from "react-i18next";

import { exportWorkbook, getExportSummary } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { downloadBlob } from "@/utils/downloadBlob";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetExportSheet, BudgetExportSummary } from "@/types/budget";

interface Props {
  open: boolean;
  enterpriseId: string;
  onClose: () => void;
}

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
 */
export const BudgetExportModal: FC<Props> = ({ open, enterpriseId, onClose }) => {
  const { t } = useTranslation();
  const [step, setStep] = useState(0);
  const [selected, setSelected] = useState<BudgetExportSheet[]>(["BUDGET", "EXPENSES", "COMPARISON"]);
  const [summary, setSummary] = useState<BudgetExportSummary | null>(null);
  const [loadingSummary, setLoadingSummary] = useState(false);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    if (!open) return;
    setStep(0);
    setSummary(null);
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

  const download = async () => {
    if (!summary) return;
    setDownloading(true);
    try {
      const file = await exportWorkbook(enterpriseId, selected, summary.fileName);
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
              <Button
                type="primary"
                onClick={() => setStep(1)}
                disabled={loadingSummary || !summary || selected.length === 0}
              >
                Continuar
              </Button>
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
            <div style={{ display: "flex", flexDirection: "column", gap: "6.8px" }}>
              {SHEETS.map((sheet) => {
                const blocked = sheet.needsBudget && !hasBudget;
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
                      <Tooltip title="Esta obra não tem orçamento.">{box}</Tooltip>
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
          </>
        )}

        {summary && step === 1 && (
          <>
            <div className="ind-card ind-blueprint" style={{ padding: "13.6px", gap: "10.2px" }}>
              <i className="ind-corner tl" />
              <i className="ind-corner tr" />
              <i className="ind-corner bl" />
              <i className="ind-corner br" />
              <span className="ind-card-kicker">{summary.fileName}</span>
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

            {summary.isTest && (
              <Alert
                type="info"
                showIcon
                message="Obra de teste"
                description="O ficheiro leva o prefixo TESTE e não deve entrar no vault."
              />
            )}

            {summary.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message={`${summary.warnings.length} aviso(s)`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12 }}>
                    {summary.warnings.map((w, i) => (
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
