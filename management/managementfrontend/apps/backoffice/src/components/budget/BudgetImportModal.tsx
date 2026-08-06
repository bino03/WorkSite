import { useEffect, useState } from "react";
import type { FC } from "react";
import { Alert, Button, Checkbox, Modal, Space, Spin, Upload } from "antd";
import { InboxOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { importBudget } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetImportResult } from "@/types/budget";

const ACCEPTED = [".xlsx", ".xls"];
const MAX_BYTES = 25 * 1024 * 1024;

interface Props {
  open: boolean;
  enterpriseId: string;
  /** Quantas rubricas o projeto já tem — decide se é preciso substituir. */
  existingItemCount: number;
  onClose: () => void;
  onImported: () => void;
}

export const BudgetImportModal: FC<Props> = ({
  open,
  enterpriseId,
  existingItemCount,
  onClose,
  onImported,
}) => {
  const { t } = useTranslation();
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<BudgetImportResult | null>(null);
  const [analysing, setAnalysing] = useState(false);
  const [importing, setImporting] = useState(false);
  const [replace, setReplace] = useState(false);

  const hasExisting = existingItemCount > 0;

  useEffect(() => {
    if (open) {
      setFile(null);
      setPreview(null);
      setReplace(false);
    }
  }, [open]);

  /** Escolher o ficheiro corre logo o dryRun — não grava nada. */
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
    setAnalysing(true);
    try {
      setPreview(await importBudget(enterpriseId, picked, true));
    } catch (error) {
      ErrorHandler.handle(error);
      setFile(null);
    } finally {
      setAnalysing(false);
    }
  };

  const confirm = async () => {
    if (!file) return;
    setImporting(true);
    try {
      const result = await importBudget(enterpriseId, file, false, replace);
      notificationService.success(
        "Importação",
        `${result.itemCount} rubricas importadas de "${result.sheetName}".`
      );
      onImported();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setImporting(false);
    }
  };

  const chapters = preview?.rows.filter((r) => r.depth === 0) ?? [];
  const totalRows = preview ? preview.itemCount + preview.headingCount + preview.noteCount : 0;
  // O Excel soma floats por arredondar e nós arredondamos cada célula: uns
  // cêntimos são normais, só acima de 1 € é que vale a pena chamar a atenção.
  const materialDifference =
    preview?.totalDifference != null && Math.abs(preview.totalDifference) > 1;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      width={620}
      title="Importar orçamento"
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={importing}>
            {t("common.cancel")}
          </Button>
          <Button
            type="primary"
            onClick={confirm}
            loading={importing}
            disabled={!preview || (hasExisting && !replace)}
          >
            Importar {preview ? `${totalRows} linhas` : ""}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Upload.Dragger
          accept=".xlsx,.xls"
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
            {file ? file.name : "Clique ou arraste o Excel do orçamento"}
          </p>
          <p style={{ fontSize: 11, opacity: 0.6, margin: "4px 0 0" }}>
            Colunas esperadas: Art · Descrição · Un. · Quant · Preço Un · Preço total · Obs.
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
              <span className="ind-card-kicker">Folha "{preview.sheetName}"</span>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr 1fr",
                  gap: "10.2px",
                  fontSize: 14,
                }}
              >
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Rubricas</div>
                  {preview.itemCount}
                </div>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Sub-títulos</div>
                  {preview.headingCount}
                </div>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Notas</div>
                  {preview.noteCount}
                </div>
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px", fontSize: 14 }}>
                <div>
                  <div style={{ fontSize: 11, opacity: 0.55 }}>Soma das rubricas</div>
                  <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                    {formatCurrency(preview.parsedTotal)}
                  </span>
                </div>
                {preview.excelTotal != null && (
                  <div>
                    <div style={{ fontSize: 11, opacity: 0.55 }}>TOTAL no Excel</div>
                    <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                      {formatCurrency(preview.excelTotal)}
                    </span>
                    {materialDifference && (
                      <div style={{ fontSize: 11, color: "#b53333" }}>
                        difere {formatCurrency(preview.totalDifference ?? 0)}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Os capítulos são a verificação estrutural: 20 linhas em vez de 198. */}
            {chapters.length > 0 && (
              <div>
                <div style={{ fontSize: 11, opacity: 0.55, marginBottom: 4 }}>
                  Capítulos encontrados ({chapters.length})
                </div>
                <div
                  style={{
                    maxHeight: 180,
                    overflowY: "auto",
                    border: "1px solid var(--ind-color-divider)",
                  }}
                >
                  {chapters.map((c) => (
                    <div
                      key={c.excelRow}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        gap: 12,
                        padding: "4px 8px",
                        fontSize: 13,
                        borderBottom: "1px solid color-mix(in srgb, var(--ind-color-text) 6%, transparent)",
                      }}
                    >
                      <span style={{ opacity: 0.6, minWidth: 34 }}>{c.code ?? "—"}</span>
                      <span
                        style={{
                          flex: 1,
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {c.name}
                      </span>
                      <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                        {c.totalPrice == null ? "—" : formatCurrency(c.totalPrice)}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {preview.warnings.length > 0 && (
              <Alert
                type="warning"
                showIcon
                message={`${preview.warnings.length} aviso(s) na leitura`}
                description={
                  <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12 }}>
                    {preview.warnings.map((w, i) => (
                      <li key={i}>{w}</li>
                    ))}
                  </ul>
                }
              />
            )}

            {hasExisting && (
              <Alert
                type="error"
                showIcon
                message="Este projeto já tem orçamento"
                description={
                  <div style={{ fontSize: 13 }}>
                    <p style={{ margin: "0 0 6px" }}>
                      Importar substitui as {existingItemCount} rubricas atuais — <strong>e as despesas
                      lançadas nelas</strong>. Não há como desfazer.
                    </p>
                    <Checkbox checked={replace} onChange={(e) => setReplace(e.target.checked)}>
                      Substituir o orçamento existente
                    </Checkbox>
                  </div>
                }
              />
            )}
          </>
        )}
      </div>
    </Modal>
  );
};
