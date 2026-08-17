import { useEffect, useRef, useState } from "react";
import type { FC } from "react";
import { Button, Drawer, Space, Upload } from "antd";
import { CloseOutlined, InboxOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { previewInvoice, uploadInvoice } from "@/services/invoiceService";
import { validateInvoiceFile } from "@/components/construction/constructionFormSchemas";
import { getErrorMessage } from "@/utils/apiError";
import { formatCurrency, formatDate } from "@/utils/formatters";

/** Bastante para o browser não engasgar e para o servidor não levar tudo de rajada. */
const CONCURRENCY = 3;

/**
 * SHA-256 do ficheiro, em hex minúsculo — mesmo formato do `checksum_sha256`
 * calculado no servidor (`ConstructionInvoiceService.sha256Hex`).
 *
 * Serve para apanhar ficheiros idênticos dentro do próprio lote, antes de
 * chegarem a "Guardar": o servidor só compara com o que já está gravado
 * (`rejectIfDuplicate`), por isso duas cópias do mesmo documento em voo ao
 * mesmo tempo passam as duas o preview — nenhuma vê a outra ainda por
 * gravar — e só colidem mais tarde na constraint da base de dados.
 */
async function sha256Hex(file: File): Promise<string | null> {
  try {
    const buffer = await file.arrayBuffer();
    const digest = await crypto.subtle.digest("SHA-256", buffer);
    return Array.from(new Uint8Array(digest))
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
  } catch {
    // Web Crypto pode faltar em contexto não seguro (http sem ser localhost).
    // Sem checksum local não há deteção antecipada, mas o servidor continua
    // a apanhar o duplicado — só chega mais tarde e com pior mensagem.
    return null;
  }
}

/**
 * Duas fases, dois botões:
 *
 * 1. "Enviar" — `queued` → `previewing` → `ready` / `duplicate` / `preview-failed`.
 *    Só lê o QR e verifica duplicados; nada é gravado.
 * 2. "Guardar" — só as linhas `ready` seguem; `saving` → `done` / `failed`.
 *
 * Duplicadas nunca chegam à fase de guardar — ficam visíveis (para se
 * perceber porque não entraram), mas são excluídas automaticamente.
 */
type Status =
  | "queued"
  | "previewing"
  | "ready"
  | "duplicate"
  | "preview-failed"
  | "saving"
  | "done"
  | "failed";

/** Enquanto uma das fases corre, não se aceitam mais ficheiros nem se troca de fase. */
type Phase = "idle" | "previewing" | "saving";

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
  /** Disparado quando pelo menos uma fatura foi guardada, para a lista recarregar. */
  onUploaded: () => void;
}

/**
 * Carregamento em massa de faturas, em duas fases.
 *
 * Largam-se os ficheiros, prime-se "Enviar" para ler o QR de cada um e
 * verificar duplicados — nada sobe ao Storage ainda. Só depois de ver o que
 * foi lido (fornecedor, número, data, total, ou "duplicada") é que se prime
 * "Guardar", e aí sim os ficheiros ficam gravados. Uma duplicada é
 * descartada sozinha: nunca chega a "Guardar", não é preciso removê-la à mão.
 */
export const InvoiceUploadDrawer: FC<Props> = ({ open, enterpriseId, onClose, onUploaded }) => {
  const { t } = useTranslation();
  const [rows, setRows] = useState<Row[]>([]);
  const [phase, setPhase] = useState<Phase>("idle");
  /** Evita disparar o refresh da lista várias vezes durante a mesma leva. */
  const uploadedAny = useRef(false);
  /**
   * Referência do último `fileList` já processado. O AntD chama `beforeUpload`
   * uma vez por ficheiro do lote, sempre com o mesmo array (por referência) no
   * segundo argumento — sem esta guarda, arrastar 10 ficheiros chamava
   * `handleFiles` 10 vezes, cada uma com os 10, e disparava 100 previews.
   */
  const processedBatch = useRef<File[] | null>(null);
  /**
   * Espelha `phase` mas fica disponível de imediato — o `useState` só se
   * reflete no próximo render, e o AntD chama `beforeUpload` de forma síncrona
   * antes de qualquer render acontecer. Segunda linha de defesa, para o caso de
   * o truque da referência do `fileList` falhar nalgum fluxo do AntD.
   */
  const phaseRef = useRef<Phase>("idle");
  /**
   * Os `File` das linhas ainda por enviar ou por guardar. Não vive no `Row`
   * porque `Row` é estado do React (serializável, mostrado na lista) e o
   * `File` só é preciso no momento de chamar a API — guardá-lo aqui evita
   * re-render a carregar um objeto que a UI nunca lê.
   */
  const filesRef = useRef<Map<string, File>>(new Map());
  /**
   * Checksums "reivindicados" neste lote: checksum → nome do ficheiro que o
   * reivindicou primeiro. Uma linha reivindica ao ficar `ready` e liberta ao
   * ser removida ou ao falhar a gravação — só continua reivindicado enquanto
   * ainda pode vir a ser (ou já foi) guardada.
   */
  const claimedChecksums = useRef<Map<string, string>>(new Map());
  /** O checksum de cada linha, para se poder libertar a reivindicação acima. */
  const rowChecksums = useRef<Map<string, string>>(new Map());

  useEffect(() => {
    if (open) {
      setRows([]);
      setPhase("idle");
      phaseRef.current = "idle";
      uploadedAny.current = false;
      processedBatch.current = null;
      filesRef.current.clear();
      claimedChecksums.current.clear();
      rowChecksums.current.clear();
    }
  }, [open]);

  const patch = (key: string, changes: Partial<Row>) =>
    setRows((prev) => prev.map((row) => (row.key === key ? { ...row, ...changes } : row)));

  /** Liberta o checksum de uma linha — deixa de bloquear um ficheiro igual que apareça depois. */
  const releaseChecksum = (rowKey: string) => {
    const checksum = rowChecksums.current.get(rowKey);
    if (checksum) {
      claimedChecksums.current.delete(checksum);
      rowChecksums.current.delete(rowKey);
    }
  };

  /** Tira da lista e do mapa de ficheiros — a meio do envio, tirá-la não cancela nada. */
  const removeRow = (key: string) => {
    filesRef.current.delete(key);
    releaseChecksum(key);
    setRows((prev) => prev.filter((row) => row.key !== key));
  };

  /**
   * Aceita um novo lote de ficheiros e põe-nos "por enviar" — não lê nem
   * grava nada ainda. Isso só acontece quando se prime "Enviar".
   *
   * Ignora silenciosamente ficheiros cuja chave já esteja na lista (mesmo
   * nome+tamanho+data), para que arrastar o mesmo ficheiro duas vezes não
   * crie linhas duplicadas.
   */
  const handleFiles = (fileList: File[]) => {
    if (phaseRef.current !== "idle") return;

    // Lê `rows` do closure, não de um updater funcional do `setRows`: um
    // updater deve ser puro (o React pode invocá-lo mais que uma vez, p.ex.
    // em StrictMode). `phaseRef`/`processedBatch` já garantem que só há uma
    // chamada a `handleFiles` por lote, por isso o `rows` deste closure está
    // sempre atualizado.
    const existingKeys = new Set(rows.map((row) => row.key));
    const accepted: Row[] = [];

    for (const file of fileList) {
      const key = `${file.name}-${file.size}-${file.lastModified}`;
      if (existingKeys.has(key)) continue;
      existingKeys.add(key);

      const invalid = validateInvoiceFile(file);
      if (invalid) {
        // `validateInvoiceFile` devolve a chave de tradução, não a frase.
        accepted.push({ key, filename: file.name, status: "preview-failed", error: t(invalid) });
        continue;
      }
      filesRef.current.set(key, file);
      accepted.push({ key, filename: file.name, status: "queued" });
    }

    if (accepted.length === 0) return;

    setRows((prev) => [...prev, ...accepted]);
  };

  /** Resumo "Betão Liz · FT 123 · 12/03/2026 · 1.234,56 €" a partir do que se leu. */
  const buildSummary = (data: {
    supplierNif: string | null;
    invoiceNumber: string | null;
    invoiceDate: string | null;
    totalAmount: number | null;
  }) =>
    [
      data.supplierNif ? `NIF ${data.supplierNif}` : null,
      data.invoiceNumber,
      data.invoiceDate ? formatDate(data.invoiceDate) : null,
      data.totalAmount != null ? formatCurrency(data.totalAmount) : null,
    ]
      .filter(Boolean)
      .join(" · ");

  /** Fase 1: lê o QR e verifica duplicados de todas as linhas "por enviar" — não grava nada. */
  const handleSend = () => {
    const queue = rows
      .filter((row) => row.status === "queued")
      .map((row) => {
        const file = filesRef.current.get(row.key);
        return file ? { row, file } : null;
      })
      .filter((entry): entry is { row: Row; file: File } => entry !== null);

    void runPreviewQueue(queue);
  };

  /** Fase 2: grava só as linhas "prontas" — duplicadas e erros de leitura ficam de fora sozinhos. */
  const handleSave = () => {
    const queue = rows
      .filter((row) => row.status === "ready")
      .map((row) => {
        const file = filesRef.current.get(row.key);
        return file ? { row, file } : null;
      })
      .filter((entry): entry is { row: Row; file: File } => entry !== null);

    void runSaveQueue(queue);
  };

  const runPreviewQueue = async (queue: { row: Row; file: File }[]) => {
    if (queue.length === 0) return;
    phaseRef.current = "previewing";
    setPhase("previewing");

    try {
      let next = 0;
      const worker = async () => {
        while (next < queue.length) {
          const { row, file } = queue[next++];
          patch(row.key, { status: "previewing" });

          // Checksum local primeiro: apanha duas cópias do mesmo ficheiro
          // dentro do próprio lote (nomes diferentes incluído — é o caso real
          // de notes/bugs.md) sem gastar um pedido ao servidor. O `await`
          // acima do check-and-set não abre brecha: entre workers em
          // paralelo, o primeiro a retomar reivindica antes de qualquer outro
          // ter oportunidade — JavaScript não intercala síncrono.
          const checksum = await sha256Hex(file);
          if (checksum) {
            const clashName = claimedChecksums.current.get(checksum);
            if (clashName) {
              filesRef.current.delete(row.key);
              patch(row.key, {
                status: "duplicate",
                error: `Mesmo ficheiro que "${clashName}", já neste carregamento.`,
              });
              continue;
            }
            claimedChecksums.current.set(checksum, row.filename);
            rowChecksums.current.set(row.key, checksum);
          }

          try {
            const result = await previewInvoice(enterpriseId, file);

            if (result.duplicate) {
              // Nunca vai ser guardada — o ficheiro não faz falta em memória.
              releaseChecksum(row.key);
              filesRef.current.delete(row.key);
              patch(row.key, {
                status: "duplicate",
                error: result.duplicateMessage ?? "Esta fatura já está registada neste projeto.",
              });
              continue;
            }

            patch(row.key, {
              status: "ready",
              needsReview: result.needsReview,
              warnings: result.warnings,
              summary: buildSummary(result),
            });
          } catch (error) {
            releaseChecksum(row.key);
            patch(row.key, { status: "preview-failed", error: getErrorMessage(error) });
          }
        }
      };

      await Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker));
    } finally {
      phaseRef.current = "idle";
      setPhase("idle");
    }
  };

  const runSaveQueue = async (queue: { row: Row; file: File }[]) => {
    if (queue.length === 0) return;
    phaseRef.current = "saving";
    setPhase("saving");
    // Lido no fim, não em `rows`: o estado só se reflete no próximo render,
    // e aqui interessa saber, já dentro desta leva, se alguma falhou.
    let hadFailure = false;

    try {
      let next = 0;
      const worker = async () => {
        while (next < queue.length) {
          const { row, file } = queue[next++];
          patch(row.key, { status: "saving" });
          try {
            const result = await uploadInvoice(enterpriseId, file);
            uploadedAny.current = true;

            const invoice = result.invoice;
            patch(row.key, {
              status: "done",
              needsReview: invoice.needsReview,
              warnings: result.warnings,
              summary: buildSummary(invoice),
            });
          } catch (error) {
            // Não usa o ErrorHandler global: uma notificação por ficheiro numa
            // leva de dez seria ruído. O erro fica na linha respetiva. Apanha
            // qualquer erro — Axios ou rede — nenhum ficheiro trava os restantes.
            // Liberta o checksum: nunca chegou a ser gravado, por isso um
            // ficheiro igual já não deve ser bloqueado por causa dele.
            hadFailure = true;
            releaseChecksum(row.key);
            patch(row.key, { status: "failed", error: getErrorMessage(error) });
          } finally {
            // O ficheiro já foi gravado (ou falhou de vez) — não há retentativa
            // automática, por isso não há razão para o manter em memória.
            filesRef.current.delete(row.key);
          }
        }
      };

      await Promise.all(Array.from({ length: Math.min(CONCURRENCY, queue.length) }, worker));
    } finally {
      phaseRef.current = "idle";
      setPhase("idle");
    }

    if (uploadedAny.current) onUploaded();
    // Só fecha sozinho quando não sobra nenhuma falha para ver — se alguma
    // não guardou, o drawer fica aberto para se perceber porquê.
    if (!hadFailure) onClose();
  };

  const queued = rows.filter((r) => r.status === "queued").length;
  const ready = rows.filter((r) => r.status === "ready").length;
  const duplicate = rows.filter((r) => r.status === "duplicate").length;
  const previewFailed = rows.filter((r) => r.status === "preview-failed").length;
  const done = rows.filter((r) => r.status === "done").length;
  const failed = rows.filter((r) => r.status === "failed").length;
  const review = rows.filter((r) => (r.status === "ready" || r.status === "done") && r.needsReview).length;

  // Enquanto houver algo por enviar (ou a ser enviado), o botão principal é
  // "Enviar". Só quando não sobra nada nessa fase é que passa a "Guardar" —
  // ficheiros largados depois de um "Enviar" voltam a mostrar "Enviar",
  // sem afetar o que já ficou "pronto" para guardar.
  const showSendButton = queued > 0 || phase === "previewing";
  const primaryDisabled = phase !== "idle" || (showSendButton ? queued === 0 : ready === 0);
  const primaryLabel = showSendButton
    ? phase === "previewing"
      ? "A ler…"
      : `Enviar${queued ? ` (${queued})` : ""}`
    : phase === "saving"
      ? "A guardar…"
      : `Guardar${ready ? ` (${ready})` : ""}`;
  const primaryAction = showSendButton ? handleSend : handleSave;

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      maskClosable={phase === "idle"}
      closable={phase === "idle"}
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
              : [
                  queued ? `${queued} por enviar` : null,
                  ready ? `${ready} pronta${ready > 1 ? "s" : ""}` : null,
                  duplicate ? `${duplicate} duplicada${duplicate > 1 ? "s" : ""} (descartada${duplicate > 1 ? "s" : ""})` : null,
                  previewFailed ? `${previewFailed} com erro na leitura` : null,
                  done ? `${done} guardada${done > 1 ? "s" : ""}` : null,
                  failed ? `${failed} com erro ao guardar` : null,
                  review ? `${review} por rever` : null,
                ]
                  .filter(Boolean)
                  .join(" · ")}
          </span>
          <Space>
            <Button onClick={onClose} disabled={phase !== "idle"}>
              Fechar
            </Button>
            <Button type="primary" onClick={primaryAction} disabled={primaryDisabled}>
              {primaryLabel}
            </Button>
          </Space>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Upload.Dragger
          accept=".pdf,.jpg,.jpeg,.png"
          multiple
          showUploadList={false}
          disabled={phase !== "idle"}
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
            Escolha os ficheiros e prima "Enviar" para ler o QR de cada um — nada é
            gravado ainda. Reveja o resultado e prima "Guardar"; duplicadas são
            descartadas automaticamente.
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
                row.status === "failed" || row.status === "preview-failed" || row.status === "duplicate"
                  ? "#b53333"
                  : "var(--ind-color-divider)",
            }}
          >
            <div style={{ display: "flex", justifyContent: "space-between", gap: 10 }}>
              <span style={{ fontSize: 13, wordBreak: "break-all" }}>{row.filename}</span>
              <span style={{ display: "flex", alignItems: "center", gap: 4 }}>
                <StatusTag row={row} />
                {row.status !== "previewing" && row.status !== "saving" && (
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

            {(row.status === "ready" || row.status === "done") && row.needsReview && (
              <div style={{ fontSize: 11, opacity: 0.7 }}>
                Sem QR legível — a fatura entra, mas falta preencher a data e o total.
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
  queued: "por enviar",
  previewing: "a ler QR",
  ready: "pronta",
  duplicate: "duplicada",
  "preview-failed": "erro na leitura",
  saving: "a guardar",
  done: "guardada",
  failed: "erro ao guardar",
};

const StatusTag: FC<{ row: Row }> = ({ row }) => {
  const isProblem = row.status === "failed" || row.status === "preview-failed" || row.status === "duplicate";
  const className =
    row.status === "done" && !row.needsReview
      ? "ind-tag-accent"
      : isProblem
        ? "ind-tag-outline"
        : "ind-tag-neutral";

  const label =
    (row.status === "ready" || row.status === "done") && row.needsReview
      ? "por rever"
      : STATUS_LABEL[row.status];

  return (
    <span className={`ind-tag ${className}`} style={{ whiteSpace: "nowrap" }}>
      {label}
    </span>
  );
};
