import { useRef, useState } from "react";
import type { FC } from "react";
import { Button, Space, Tooltip } from "antd";
import { DeleteOutlined, FileTextOutlined, PlusOutlined, SwapOutlined } from "@ant-design/icons";

import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import { addInvoiceDocument, deleteInvoiceDocument, replaceInvoiceFile } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatBytes, formatDate, savingsPercent } from "@/utils/formatters";
import type { InvoiceDocument } from "@/types/invoice";

interface Props {
  invoiceId: string;
  documents: InvoiceDocument[];
  /** Só `ADMIN` remove ou substitui; juntar é de toda a gente. */
  canManage: boolean;
  /** Recarrega a fatura: o `documentStatus` muda no servidor, não aqui. */
  onChanged: () => void;
}

const ACCEPT = "application/pdf,image/jpeg,image/png";

/**
 * Os documentos de uma fatura, em grelha.
 *
 * Desde a V24 uma fatura tem 0..N ficheiros — a foto tirada na obra e o PDF que
 * o fornecedor mandou depois são o mesmo documento fiscal. As três ações não
 * são variações uma da outra e vale a pena não as confundir:
 *
 * - **juntar** acrescenta um documento;
 * - **remover** tira exatamente aquele;
 * - **substituir** larga *todos* e põe um só no lugar — é o comportamento
 *   antigo do `POST /{id}/file`, que fazia sentido quando a fatura era um
 *   ficheiro e hoje já não é óbvio pelo nome do botão. Daí a confirmação dizer
 *   quantos documentos vão desaparecer.
 */
const InvoiceDocumentGallery: FC<Props> = ({ invoiceId, documents, canManage, onChanged }) => {
  const confirm = useConfirm();
  const addInputRef = useRef<HTMLInputElement>(null);
  const replaceInputRef = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState<InvoiceDocument | null>(null);

  const handleAdd = async (file: File) => {
    setBusy(true);
    try {
      const result = await addInvoiceDocument(invoiceId, file);
      notificationService.success("Documento adicionado");
      // O backend nunca sobrepõe o que já lá estava: quando o QR do ficheiro
      // novo diverge, avisa e deixa os campos como estavam.
      result.warnings.forEach((warning) => notificationService.warning(warning));
      onChanged();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setBusy(false);
    }
  };

  const handleReplace = (file: File) => {
    const count = documents.length;
    confirm({
      title: "Substituir o ficheiro",
      // O nome do botão não chega para dizer o que isto faz quando há vários
      // documentos — o número tem de estar à vista antes de se carregar.
      message:
        count > 1
          ? `Isto apaga os ${count} documentos desta fatura e deixa só o novo. Continuar?`
          : "Isto apaga o documento atual desta fatura e deixa só o novo. Continuar?",
      actionLabel: "Substituir",
      onConfirm: async () => {
        setBusy(true);
        try {
          await replaceInvoiceFile(invoiceId, file);
          notificationService.success("Ficheiro substituído");
          onChanged();
        } catch (error) {
          ErrorHandler.handle(error);
        } finally {
          setBusy(false);
        }
      },
    });
  };

  const handleDelete = (document: InvoiceDocument) => {
    const remaining = documents.length - 1;
    confirm({
      title: "Remover documento",
      message:
        remaining === 0
          ? "É o último documento desta fatura — o registo fica sem ficheiro."
          : `Ficam ${remaining} documento(s) nesta fatura.`,
      actionLabel: "Remover",
      onConfirm: async () => {
        setBusy(true);
        try {
          await deleteInvoiceDocument(invoiceId, document.id);
          notificationService.success("Documento removido");
          onChanged();
        } catch (error) {
          ErrorHandler.handle(error);
        } finally {
          setBusy(false);
        }
      },
    });
  };

  return (
    <div className="ind-card ind-blueprint ind-elev-sm" style={{ padding: "13.6px" }}>
      <i className="ind-corner tl" />
      <i className="ind-corner tr" />
      <i className="ind-corner bl" />
      <i className="ind-corner br" />
      <span className="ind-card-kicker">
        {documents.length === 0
          ? "Documentos"
          : `Documentos · ${documents.length}`}
      </span>

      {documents.length === 0 ? (
        <p style={{ fontSize: 13, opacity: 0.6, margin: "8px 0 12px" }}>
          Esta fatura ainda não tem ficheiro.
        </p>
      ) : (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(96px, 1fr))",
            gap: "13.6px",
            margin: "8px 0 12px",
          }}
        >
          {documents.map((document) => {
            const savings = savingsPercent(document.originalSizeBytes, document.sizeBytes);
            return (
              <figure key={document.id} style={{ margin: 0, minWidth: 0 }}>
                <button
                  type="button"
                  onClick={() => setPreview(document)}
                  title={document.originalFilename ?? "documento"}
                  style={{
                    padding: 0,
                    width: "100%",
                    border: "1px solid var(--ind-color-divider)",
                    background: "var(--ind-color-surface)",
                    cursor: "pointer",
                    display: "block",
                  }}
                >
                  {document.thumbnailUrl ? (
                    <img
                      src={document.thumbnailUrl}
                      alt={document.originalFilename ?? "documento"}
                      style={{ width: "100%", height: 128, objectFit: "cover", display: "block" }}
                    />
                  ) : (
                    <span
                      style={{
                        height: 128,
                        display: "grid",
                        placeItems: "center",
                        color: "var(--ind-color-accent)",
                      }}
                    >
                      <FileTextOutlined style={{ fontSize: 28 }} />
                    </span>
                  )}
                </button>

                <figcaption style={{ fontSize: 11, opacity: 0.6, marginTop: 4 }}>
                  <div style={{ wordBreak: "break-all" }}>{document.originalFilename ?? "—"}</div>
                  <div>
                    {formatBytes(document.sizeBytes)}
                    {savings != null && <> · −{savings}%</>}
                  </div>
                  <div>{formatDate(document.uploadedAt)}</div>
                  {canManage && (
                    <Button
                      type="text"
                      size="small"
                      icon={<DeleteOutlined />}
                      disabled={busy}
                      onClick={() => handleDelete(document)}
                      style={{ opacity: 0.75, color: "var(--ind-color-accent)", paddingInline: 0 }}
                    >
                      Remover
                    </Button>
                  )}
                </figcaption>
              </figure>
            );
          })}
        </div>
      )}

      <Space size="small" wrap>
        <Button
          size="small"
          icon={<PlusOutlined />}
          loading={busy}
          onClick={() => addInputRef.current?.click()}
        >
          Juntar documento
        </Button>
        {canManage && documents.length > 0 && (
          <Tooltip title="Larga todos os documentos desta fatura e põe um só no lugar.">
            <Button
              size="small"
              type="text"
              icon={<SwapOutlined />}
              disabled={busy}
              onClick={() => replaceInputRef.current?.click()}
            >
              Substituir
            </Button>
          </Tooltip>
        )}
      </Space>

      <input
        ref={addInputRef}
        type="file"
        accept={ACCEPT}
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (file) void handleAdd(file);
        }}
      />
      <input
        ref={replaceInputRef}
        type="file"
        accept={ACCEPT}
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (file) handleReplace(file);
        }}
      />

      <InvoicePreviewModal
        open={preview !== null}
        onClose={() => setPreview(null)}
        invoiceUrl={preview?.fileUrl ?? null}
        mimeType={preview?.mimeType ?? null}
        filename={preview?.originalFilename ?? null}
      />
    </div>
  );
};

export default InvoiceDocumentGallery;
