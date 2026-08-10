import { useEffect, useRef, useState } from "react";
import type { FC } from "react";
import { Button, Drawer, Space, Upload } from "antd";
import { CloseOutlined, InboxOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { uploadInvoice } from "@/services/invoiceService";
import { validateInvoiceFile } from "@/components/construction/constructionFormSchemas";
import { getErrorMessage } from "@/utils/apiError";
import { formatCurrency, formatDate } from "@/utils/formatters";

/** Bastante para o browser não engasgar e para o servidor não levar tudo de rajada. */
const CONCURRENCY = 3;

type Status = "queued" | "compressing" | "uploading" | "done" | "failed";

interface Row {
  key: string;
  filename: string;
  status: Status;
  /** Resumo do que o QR trouxe — "Betão Liz · 1.234,56 €". */
  summary?: string;
  needsReview?: boolean;
  warnings?: string[];
  error?: string;
}

interface Props {
  open: boolean;
  enterpriseId: string;
  onClose: () => void;
  /** Disparado quando pelo menos uma fatura entrou, para a lista recarregar. */
  onUploaded: () => void;
}

/**
 * Carregamento em massa de faturas.
 *
 * O ponto todo é não haver formulário: largam-se os ficheiros, o QR da AT
 * preenche fornecedor, número, data e total, e a classificação fica para
 * depois. Cada ficheiro tem o seu estado — um QR ilegível não interrompe os
 * restantes, entra como "por rever" e alguém o completa mais tarde.
 */
export const InvoiceUploadDrawer: FC<Props> = ({ open, enterpriseId, onClose, onUploaded }) => {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Row[]>([]);
  const [running, setRunning] = useState(false);
  /** Evita disparar o refresh da lista várias vezes durante a mesma leva. */
  const uploadedAny = useRef(false);
  /**
   * Referência do último `fileList` já processado. O AntD chama `beforeUpload`
   * uma vez por ficheiro do lote, sempre com o mesmo array (por referência) no
   * segundo argumento — sem esta guarda, arrastar 10 ficheiros chamava
   * `handleFiles` 10 vezes, cada uma com os 10, e disparava 100 uploads.
   */
  const processedBatch = useRef<File[] | null>(null);
  /**
   * Espelha `running` mas fica disponível de imediato — o `useState` só se
   * reflete no próximo render, e o AntD chama `beforeUpload` de forma síncrona
   * antes de qualquer render acontecer. Segunda linha de defesa, para o caso de
   * o truque da referência do `fileList` falhar nalgum fluxo do AntD.
   */
  const runningRef = useRef(false);

  useEffect(() => {
    if (open) {
      setRows([]);
      setRunning(false);
      runningRef.current = false;
      uploadedAny.current = false;
      processedBatch.current = null;
    }
  }, [open]);

  const patch = (key: string, changes: Partial<Row>) =>
    setRows((prev) => prev.map((row) => (row.key === key ? { ...row, ...changes } : row)));

  /** Só faz sentido para linhas paradas — a meio do envio, tirá-la da lista não cancela nada. */
  const removeRow = (key: string) => setRows((prev) => prev.filter((row) => row.key !== key));

  /**
   * Aceita um novo lote de ficheiros. Ignora silenciosamente ficheiros cuja
   * chave já esteja na lista (mesmo nome+tamanho+data), para que arrastar o
   * mesmo ficheiro duas vezes não crie linhas duplicadas nem duplique o upload
   * — quem quiser tentar de novo remove a linha (❌) e larga outra vez.
   */
  const handleFiles = (fileList: File[]) => {
    if (runningRef.current) return;

    // Lê `rows` do closure, não de um updater funcional do `setRows`: um
    // updater deve ser puro (o React pode invocá-lo mais que uma vez, p.ex.
    // em StrictMode), e `runQueue` dispara pedidos de rede — um efeito
    // secundário não pode viver aí dentro. `runningRef`/`processedBatch`
    // já garantem que só há uma chamada a `handleFiles` por lote, por isso
    // o `rows` deste closure está sempre atualizado.
    const existingKeys = new Set(rows.map((row) => row.key));
    const accepted: { row: Row; file: File }[] = [];

    for (const file of fileList) {
      const key = `${file.name}-${file.size}-${file.lastModified}`;
      if (existingKeys.has(key)) continue;
      existingKeys.add(key);

      const invalid = validateInvoiceFile(file);
      if (invalid) {
        // `validateInvoiceFile` devolve a chave de tradução, não a frase.
        accepted.push({
          row: { key, filename: file.name, status: "failed", error: t(invalid) },
          file,
        });
        continue;
      }
      accepted.push({ row: { key, filename: file.name, status: "queued" }, file });
    }

    if (accepted.length === 0) return;

    setRows((prev) => [...prev, ...accepted.map((entry) => entry.row)]);
    void runQueue(accepted.filter((entry) => entry.row.status === "queued"));
  };

  const runQueue = async (queue: { row: Row; file: File }[]) => {
    if (queue.length === 0) return;
    runningRef.current = true;
    setRunning(true);

    try {
      let next = 0;
      const worker = async () => {
        while (next < queue.length) {
          const { row, file } = queue[next++];
          try {
            const result = await uploadInvoice(enterpriseId, file, (phase) =>
              patch(row.key, { status: phase })
            );
            uploadedAny.current = true;

            const invoice = result.invoice;
            patch(row.key, {
              status: "done",
              needsReview: invoice.needsReview,
              warnings: result.warnings,
              summary: [
                invoice.supplierNif ? `NIF ${invoice.supplierNif}` : null,
                invoice.invoiceNumber,
                invoice.invoiceDate ? formatDate(invoice.invoiceDate) : null,
                invoice.totalAmount != null ? formatCurrency(invoice.totalAmount) : null,
              ]
                .filter(Boolean)
                .join(" · "),
            });
          } catch (error) {
            // Não usa o ErrorHandler global: uma notificação por ficheiro numa
            // leva de dez seria ruído. O erro fica na linha respetiva. Apanha
            // qualquer erro — Axios, rede, ou falha na compressão do browser —
            // nenhum ficheiro trava os restantes da fila.
            patch(row.key, { status: "failed", error: getErrorMessage(error) });
          }
        }
      };

      await Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker));
    } finally {
      // Sempre — mesmo que algo rebente fora do try/catch por ficheiro — para
      // o drawer nunca ficar bloqueado em "A carregar…".
      runningRef.current = false;
      setRunning(false);
    }

    if (uploadedAny.current) onUploaded();
  };

  const done = rows.filter((r) => r.status === "done").length;
  const failed = rows.filter((r) => r.status === "failed").length;
  const review = rows.filter((r) => r.needsReview).length;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      maskClosable={!running}
      closable={!running}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
          <h2 style={{ margin: 0 }}>Carregar faturas</h2>
        </div>
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "space-between" }}>
          <span style={{ fontSize: 12, opacity: 0.65 }}>
            {rows.length === 0
              ? "Nenhum ficheiro selecionado"
              : `${done} de ${rows.length} carregadas${failed ? ` · ${failed} com erro` : ""}${
                  review ? ` · ${review} por rever` : ""
                }`}
          </span>
          <Button type="primary" onClick={onClose} disabled={running}>
            {running ? "A carregar…" : "Concluir"}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Upload.Dragger
          accept=".pdf,.jpg,.jpeg,.png"
          multiple
          showUploadList={false}
          disabled={running}
          beforeUpload={(file, fileList) => {
            // `beforeUpload` corre uma vez por ficheiro do lote, sempre com o
            // mesmo `fileList` (por referência) — só processar no último
            // ficheiro, e só uma vez por lote, evita processar o lote inteiro
            // N vezes.
            const isLastOfBatch = file === fileList[fileList.length - 1];
            const alreadyProcessed = processedBatch.current === fileList;
            if (isLastOfBatch && !alreadyProcessed) {
              processedBatch.current = fileList as unknown as File[];
              handleFiles(fileList as unknown as File[]);
            }
            return Upload.LIST_IGNORE;
          }}
        >
          <p style={{ margin: 0 }}>
            <InboxOutlined style={{ fontSize: 24, color: "var(--ind-color-accent)" }} />
          </p>
          <p style={{ fontSize: 13, margin: "8px 0 0" }}>
            Largue aqui as faturas — PDF, JPEG ou PNG
          </p>
          <p style={{ fontSize: 11, opacity: 0.6, margin: "4px 0 0" }}>
            As fotos são comprimidas antes de subir e o QR da AT preenche os campos.
            A rubrica escolhe-se depois.
          </p>
        </Upload.Dragger>

        {rows.map((row) => (
          <div
            key={row.key}
            className="ind-card"
            style={{
              padding: "10.2px",
              gap: 4,
              borderColor:
                row.status === "failed" ? "#b53333" : "var(--ind-color-divider)",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
              <span style={{ fontSize: 13, wordBreak: "break-all" }}>{row.filename}</span>
              <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <StatusTag row={row} />
                {(row.status === "done" || row.status === "failed") && (
                  <Button
                    type="text"
                    size="small"
                    icon={<CloseOutlined />}
                    onClick={() => removeRow(row.key)}
                    aria-label="Remover da lista"
                  />
                )}
              </span>
            </div>

            {row.summary && <div style={{ fontSize: 12, opacity: 0.8 }}>{row.summary}</div>}

            {row.status === "done" && row.needsReview && (
              <div style={{ fontSize: 11, opacity: 0.7 }}>
                Sem QR legível — a fatura entrou, mas falta preencher a data e o total.
              </div>
            )}

            {row.error && <div style={{ fontSize: 11, color: "#b53333" }}>{row.error}</div>}

            {row.warnings?.map((warning) => (
              <div key={warning} style={{ fontSize: 11, opacity: 0.7 }}>
                {warning}
              </div>
            ))}

          </div>
        ))}
      </div>
    </Drawer>
  );
};

const STATUS_LABEL: Record<Status, string> = {
  queued: "em fila",
  compressing: "a comprimir",
  uploading: "a ler QR",
  done: "carregada",
  failed: "erro",
};

const StatusTag: FC<{ row: Row }> = ({ row }) => {
  const className =
    row.status === "done" && !row.needsReview
      ? "ind-tag-accent"
      : row.status === "failed"
        ? "ind-tag-outline"
        : "ind-tag-neutral";

  const label =
    row.status === "done" && row.needsReview ? "por rever" : STATUS_LABEL[row.status];

  return (
    <span className={`ind-tag ${className}`} style={{ whiteSpace: "nowrap" }}>
      {label}
    </span>
  );
};
