import { useEffect, useMemo, useState } from "react";
import type { FC } from "react";
import { Alert, Button, Modal, Select, Space, Spin, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { importExpensesExcel } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency, formatDate } from "@/utils/formatters";
import type {
  ExpensesImportAnswer,
  ExpensesImportInvoice,
  ExpensesImportResult,
  InvoiceScope,
} from "@/types/invoice";

const ACCEPTED = [".xlsx", ".xlsm"];
const MAX_BYTES = 25 * 1024 * 1024;

const PAYMENT_LABEL: Record<ExpensesImportInvoice["paymentStatus"], string> = {
  PAID: "Liquidada",
  PARTIAL: "Parcial",
  UNPAID: "Por liquidar",
};

interface Props {
  open: boolean;
  /** `PROJECT` exige `enterpriseId`; `COMPANY` é a folha "Despesas da empresa"; `UNIDENTIFIED` é a folha "Por identificar" da quarentena. */
  scope: InvoiceScope;
  enterpriseId?: string;
  onClose: () => void;
  onImported: () => void;
}

/**
 * Importa a folha "Despesas" do Excel do vault da Vilatro (contrato
 * `docs/excel-parity.md` §9). O mesmo desenho do `BudgetImportModal`: escolher
 * o ficheiro corre logo o `dryRun`, e só se grava depois de se ver o que vai
 * entrar — aqui com dois degraus a mais: os **erros** (que se corrigem no Excel
 * e bloqueiam) e as **perguntas** (a que fatura pertence uma nota de crédito,
 * se várias faturas foram pagas num só movimento) que só a pessoa sabe responder.
 *
 * Na quarentena lê a folha "Por identificar" (§6): as linhas com "Empreendimento"
 * preenchido entram e são transferidas logo para essa obra — a lista mostra o
 * destino de cada uma.
 */
export const ExpensesImportModal: FC<Props> = ({ open, scope, enterpriseId, onClose, onImported }) => {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<ExpensesImportResult | null>(null);
  const [answers, setAnswers] = useState<Record<string, string>>({});
  const [analysing, setAnalysing] = useState(false);
  const [importing, setImporting] = useState(false);

  useEffect(() => {
    if (open) {
      setFile(null);
      setPreview(null);
      setAnswers({});
    }
  }, [open]);

  const analyse = async (picked: File) => {
    const name = picked.name.toLowerCase();
    if (!ACCEPTED.some((ext) => name.endsWith(ext))) {
      notificationService.error("Importação", "O ficheiro tem de ser um Excel (.xlsx).");
      return;
    }
    if (picked.size > MAX_BYTES) {
      notificationService.error("Importação", "O ficheiro excede o tamanho máximo de 25 MB.");
      return;
    }

    setFile(picked);
    setPreview(null);
    setAnswers({});
    setAnalysing(true);
    try {
      setPreview(await importExpensesExcel(scope, enterpriseId ?? null, picked, true));
    } catch (error) {
      ErrorHandler.handle(error);
      setFile(null);
    } finally {
      setAnalysing(false);
    }
  };

  const answerList = useMemo<ExpensesImportAnswer[]>(
    () => Object.entries(answers).map(([questionId, value]) => ({ questionId, value })),
    [answers]
  );
  const unanswered = preview?.questions.filter((q) => !answers[q.id]) ?? [];
  const canImport = !!preview && preview.errors.length === 0 && unanswered.length === 0;

  const confirm = async () => {
    if (!file || !preview) return;
    setImporting(true);
    try {
      const result = await importExpensesExcel(scope, enterpriseId ?? null, file, false, answerList);
      notificationService.success(
        "Importação",
        `${result.invoiceCount} faturas importadas de "${result.sheetName}"` +
          (result.creditNoteCount > 0 ? `, ${result.creditNoteCount} nota(s) de crédito` : "") +
          (result.transferredCount > 0 ? `, ${result.transferredCount} transferida(s) para a obra indicada` : "") +
          "."
      );
      onImported();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setImporting(false);
    }
  };

  const materialDifference =
    preview?.totalDifference != null && Math.abs(preview.totalDifference) > 0.01;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      title={
        scope === "COMPANY"
          ? "Importar despesas da empresa do Excel"
          : scope === "UNIDENTIFIED"
            ? "Importar faturas por identificar do Excel"
            : "Importar despesas do Excel"
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={importing}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={confirm} loading={importing} disabled={!canImport}>
            Importar {preview ? `${preview.invoiceCount} faturas` : ""}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Upload.Dragger
          accept=".xlsx,.xlsm"
          maxCount={1}
          showUploadList={false}
          disabled={analysing || importing}
          beforeUpload={(picked) => {
            analyse(picked as File);
            return false;
          }}
        >
          <p style={{ margin: 0 }}>
            <InboxOutlined style={{ fontSize: 22, color: "var(--ind-color-accent)" }} />
          </p>
          <p style={{ fontSize: 13, margin: "6px 0 0" }}>
            {file
              ? file.name
              : scope === "UNIDENTIFIED"
                ? "Clique ou arraste o Excel da quarentena (Faturas por identificar.xlsx)"
                : "Clique ou arraste o Excel da obra (Despesas - <Obra>.xlsx)"}
          </p>
          <p style={{ fontSize: 11, opacity: 0.6, margin: "4px 0 0" }}>
            Lê só a folha "{scope === "UNIDENTIFIED" ? "Por identificar" : "Despesas"}" · colunas pelo nome: Nº
            Fatura · Data · Produto/Serviço · Valor · Liquidada · Metodo Pagamento · Bizdocs · Observações
            {scope === "PROJECT" && " · Rubrica"}
            {scope === "UNIDENTIFIED" && " · Empreendimento · Fornecedor · Obras possíveis · Perguntar a · Aqui desde"}
          </p>
        </Upload.Dragger>

        {analysing && (
          <div style={{ textAlign: "center", padding: "10.2px" }}>
            <Spin /> <span style={{ fontSize: 13, marginLeft: 8 }}>A analisar o ficheiro…</span>
          </div>
        )}

        {preview && (
          <>
            <div className="ind-card ind-blueprint" style={{ padding: "13.6px", gap: "10.2px" }}>
              <i className="ind-corner tl" />
              <i className="ind-corner tr" />
              <i className="ind-corner bl" />
              <i className="ind-corner br" />
              <span className="ind-card-kicker">
                Folha "{preview.sheetName}" · {preview.rowCount} linhas
              </span>
              <div
                style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr 1fr", gap: "10.2px", fontSize: 14 }}
              >
                <Stat label="Faturas" value={preview.invoiceCount} />
                <Stat label="Notas de crédito" value={preview.creditNoteCount} />
                <Stat label="Liquidadas" value={preview.paidCount} />
                <Stat
                  label="Por liquidar"
                  value={preview.unpaidCount}
                  hint={preview.partiallyPaidCount > 0 ? `${preview.partiallyPaidCount} parcial` : undefined}
                />
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px", fontSize: 14 }}>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Soma das linhas</div>
                  <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                    {formatCurrency(preview.parsedTotal)}
                  </span>
                </div>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>TOTAL na folha</div>
                  <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                    {preview.sheetTotal == null ? "—" : formatCurrency(preview.sheetTotal)}
                  </span>
                  {materialDifference && (
                    <div style={{ fontSize: 11, color: "#b53333" }}>
                      difere {formatCurrency(preview.totalDifference ?? 0)}
                    </div>
                  )}
                </div>
              </div>
              {preview.manualExpenseCount > 0 && (
                <div style={{ fontSize: 12, opacity: 0.7 }}>
                  + {preview.manualExpenseCount} despesa(s) sem fatura, lançadas direto na rubrica.
                </div>
              )}
              {preview.transferredCount > 0 && (
                <div style={{ fontSize: 12, opacity: 0.7 }}>
                  {preview.transferredCount} com "Empreendimento" preenchido — entram e são transferidas logo para
                  essa obra (ou para as despesas da empresa), com a razão no histórico.
                </div>
              )}
            </div>

            {preview.errors.length > 0 && (
              <Alert
                type="error"
                showIcon
                message={`${preview.errors.length} erro(s) a corrigir no Excel antes de importar`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12 }}>
                    {preview.errors.map((e, i) => (
                      <li key={i}>
                        {e.excelRow > 0 && <strong>Linha {e.excelRow}: </strong>}
                        {e.message}
                      </li>
                    ))}
                  </ul>
                }
              />
            )}

            {/* As decisões que o importador não toma sozinho: só se grava com todas respondidas. */}
            {preview.questions.length > 0 && (
              <div className="ind-card" style={{ padding: "13.6px", gap: "10.2px" }}>
                <span className="ind-card-kicker">
                  {preview.questions.length} pergunta(s) — {unanswered.length} por responder
                </span>
                {preview.questions.map((q) => (
                  <div key={q.id} style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <div style={{ fontSize: 13 }}>{q.text}</div>
                    <Select
                      size="small"
                      placeholder="Escolher…"
                      value={answers[q.id]}
                      onChange={(value: string) => setAnswers((prev) => ({ ...prev, [q.id]: value }))}
                      options={q.options}
                      showSearch={q.kind === "CREDIT_NOTE_ORIGIN"}
                      optionFilterProp="label"
                      style={{ width: "100%" }}
                    />
                  </div>
                ))}
              </div>
            )}

            <div>
              <div style={{ fontSize: 11, opacity: 0.55, marginBottom: 4 }}>
                O que vai entrar ({preview.invoices.length})
              </div>
              <div
                style={{
                  maxHeight: 220,
                  overflowY: "auto",
                  border: "1px solid var(--ind-color-divider)",
                }}
              >
                {preview.invoices.map((inv) => (
                  <div
                    key={inv.key}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      gap: 12,
                      padding: "4px 8px",
                      fontSize: 13,
                      borderBottom: "1px solid color-mix(in srgb, var(--ind-color-text) 6%, transparent)",
                      color: inv.duplicate ? "#b53333" : undefined,
                    }}
                  >
                    <span style={{ opacity: 0.6, minWidth: 34 }}>{inv.excelRows[0]}</span>
                    <span style={{ minWidth: 120, fontWeight: 500 }}>
                      {inv.manualExpense
                        ? "sem fatura"
                        : (inv.invoiceNumber ??
                          (inv.documentStatus === "TO_PRINT"
                            ? "Imprimir fatura"
                            : inv.documentStatus === "TO_REQUEST"
                              ? "Pedir fatura"
                              : "sem nº"))}
                      {inv.creditNote && " (NC)"}
                    </span>
                    <span
                      style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
                    >
                      {inv.description ?? "—"}
                      {inv.lines.some((l) => l.rubricCode) && (
                        <span style={{ opacity: 0.55 }}>
                          {" · "}
                          {inv.lines
                            .map((l) => l.rubricCode)
                            .filter(Boolean)
                            .join(", ")}
                        </span>
                      )}
                      {inv.transferTo && (
                        <span style={{ color: "var(--ind-accent-700)" }}>
                          {" → "}
                          {inv.transferTo}
                        </span>
                      )}
                    </span>
                    <span style={{ opacity: 0.6, minWidth: 74 }}>{formatDate(inv.invoiceDate)}</span>
                    <span style={{ opacity: 0.6, minWidth: 74 }}>
                      {inv.creditNote || inv.manualExpense ? "" : PAYMENT_LABEL[inv.paymentStatus]}
                    </span>
                    <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, minWidth: 80, textAlign: "right" }}>
                      {inv.totalAmount == null
                        ? "—"
                        : formatCurrency(inv.creditNote ? -inv.totalAmount : inv.totalAmount)}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {preview.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message={`${preview.warnings.length} aviso(s) na leitura`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, maxHeight: 160, overflowY: "auto" }}>
                    {preview.warnings.map((w, i) => (
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

const Stat: FC<{ label: string; value: number; hint?: string }> = ({ label, value, hint }) => (
  <div>
    <div style={{ fontSize: 11, opacity: 0.55 }}>{label}</div>
    {value}
    {hint && <span style={{ fontSize: 11, opacity: 0.55, marginLeft: 6 }}>({hint})</span>}
  </div>
);
