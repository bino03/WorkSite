import { useEffect, useState } from "react";
import type { FC } from "react";
import { Alert, Button, Modal, Radio, Select, Space, Spin } from "antd";

import {
  downloadDocumentsZip,
  getDocumentsSummary,
  type InvoiceDocumentsScope,
} from "@/services/invoiceService";
import { listBudgetLots } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { downloadBlob } from "@/utils/downloadBlob";
import type { BudgetLot, DocumentsExportSummary } from "@/types/budget";

interface Props {
  open: boolean;
  enterpriseId: string;
  enterpriseName: string;
  /** As faturas marcadas na lista — habilitam o âmbito "as N selecionadas". */
  selectedInvoiceIds: string[];
  onClose: () => void;
  /** Levar à fila de classificação — a saída quando um lote não tem nada por ninguém ter classificado. */
  onGoToClassify: () => void;
}

/** Valor do selector de lote que significa "não filtrar por lote". */
const ANY_LOT = "__any__";

/**
 * Descarregar os PDFs e fotos das faturas num `.zip`.
 *
 * As opções seguem os **dois eixos** que decidem a que faturas um documento
 * pertence, em vez de uma lista plana deles:
 *
 * 1. **está associada a uma rubrica?** — as três primeiras opções;
 * 2. **de que lote?** — um selector *dentro* de "já associadas", porque o lote
 *    vem da rubrica onde a fatura é classificada e **não se aplica** às que não
 *    estão associadas.
 *
 * Achatar os dois numa lista foi o que confundiu o utilizador a 2026-10-02:
 * escolhia "um lote", vinha vazio, e nada dizia que era por nenhuma fatura estar
 * classificada. A forma passou a ensinar a regra.
 *
 * A quarta opção — "as N selecionadas na lista" — está fora dos eixos de
 * propósito. A lista já filtra por fornecedor, datas e estado de pagamento, por
 * isso deixar escolher "estas" dá todos os outros cortes de graça, em vez de se
 * inventar aqui um âmbito por cada um.
 *
 * Não confundir com o `.zip` da pasta da obra (no modal de exportação do Excel):
 * esse leva o livro e põe os documentos em `Faturas/Lançadas/`, porque é a
 * estrutura do vault. Este leva só os documentos, na raiz.
 */
export const InvoiceDocumentsDownloadModal: FC<Props> = ({
  open,
  enterpriseId,
  enterpriseName,
  selectedInvoiceIds,
  onClose,
  onGoToClassify,
}) => {
  const [scope, setScope] = useState<InvoiceDocumentsScope>("ALL");
  const [lots, setLots] = useState<BudgetLot[]>([]);
  const [lotId, setLotId] = useState<string>(ANY_LOT);
  const [summary, setSummary] = useState<DocumentsExportSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  /**
   * Quantos ficheiros há em faturas por classificar. Pede-se **uma vez** ao abrir,
   * independentemente do âmbito escolhido: é o número de que a mensagem de "este
   * lote não tem nada" precisa para poder oferecer a alternativa.
   */
  const [unclassifiedCount, setUnclassifiedCount] = useState(0);

  useEffect(() => {
    if (!open) return;
    setScope("ALL");
    setSummary(null);
    setLotId(ANY_LOT);
    setUnclassifiedCount(0);
    listBudgetLots(enterpriseId)
      .then(setLots)
      .catch(() => setLots([]));
    getDocumentsSummary(enterpriseId, "UNCLASSIFIED")
      .then((s) => setUnclassifiedCount(s.documentCount))
      .catch(() => setUnclassifiedCount(0));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, enterpriseId]);

  const budgetId = scope === "ASSOCIATED" && lotId !== ANY_LOT ? lotId : undefined;

  // a contagem muda com o âmbito e com o lote, por isso recarrega-se a cada troca
  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    setLoading(true);
    getDocumentsSummary(
      enterpriseId,
      scope,
      budgetId,
      scope === "SELECTED" ? selectedInvoiceIds : undefined
    )
      .then((s) => {
        if (!cancelled) setSummary(s);
      })
      .catch((error) => {
        if (!cancelled) {
          setSummary(null);
          ErrorHandler.handle(error);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, enterpriseId, scope, budgetId]);

  const lot = lots.find((l) => l.id === lotId);
  const count = summary?.documentCount ?? 0;
  const hasSelection = selectedInvoiceIds.length > 0;

  const sufixo =
    scope === "ALL"
      ? ""
      : scope === "UNCLASSIFIED"
        ? " - Por classificar"
        : scope === "SELECTED"
          ? ` - ${count} selecionadas`
          : lotId === ANY_LOT
            ? " - Associadas"
            : ` - ${lot?.name ?? ""}`;
  const nomeEsperado = `Faturas - ${enterpriseName}${sufixo}.zip`;

  const download = async () => {
    setDownloading(true);
    try {
      const file = await downloadDocumentsZip(
        enterpriseId,
        scope,
        budgetId,
        scope === "SELECTED" ? selectedInvoiceIds : undefined,
        nomeEsperado
      );
      downloadBlob(file.blob, file.fileName);
      notificationService.success("Download", `Ficheiro "${file.fileName}" gerado.`);
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setDownloading(false);
    }
  };

  /** A contagem ao lado de cada opção, quando é essa que está escolhida. */
  const badge = (value: InvoiceDocumentsScope) =>
    scope === value && !loading && summary ? (
      <span style={{ fontWeight: 400, opacity: 0.6 }}> · {count} ficheiro(s)</span>
    ) : null;

  return (
    <Modal
      open={open}
      onCancel={onClose}
      centered
      width={580}
      title="Descarregar documentos das faturas"
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="primary" onClick={download} loading={downloading} disabled={loading || count === 0}>
            Descarregar
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Radio.Group
          value={scope}
          onChange={(e) => setScope(e.target.value as InvoiceDocumentsScope)}
          style={{ display: "flex", flexDirection: "column", gap: "10.2px" }}
        >
          <Radio value="ALL">
            <span style={{ fontWeight: 600 }}>Todas as faturas da obra</span>
            {badge("ALL")}
          </Radio>

          <div>
            <Radio value="ASSOCIATED">
              <span style={{ fontWeight: 600 }}>Só as já associadas a rubricas</span>
              {badge("ASSOCIATED")}
            </Radio>
            {/* o lote vive aqui dentro, e não como opção irmã, porque só se aplica
                às associadas — é a forma a dizer a regra */}
            {scope === "ASSOCIATED" && (
              <div style={{ marginLeft: 24, marginTop: "6.8px", display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ fontSize: 13, opacity: 0.7 }}>de:</span>
                <Select
                  value={lotId}
                  onChange={setLotId}
                  style={{ minWidth: 220 }}
                  options={[
                    { value: ANY_LOT, label: "qualquer lote" },
                    ...lots.map((l) => ({
                      value: l.id,
                      label: l.itemCount === 0 ? `${l.name} (sem rubricas)` : l.name,
                    })),
                  ]}
                />
              </div>
            )}
          </div>

          <Radio value="UNCLASSIFIED">
            <span style={{ fontWeight: 600 }}>Só as que ainda não estão associadas</span>
            {badge("UNCLASSIFIED")}
            <div style={{ fontSize: 12, opacity: 0.6 }}>
              Sem rubrica, não pertencem a lote nenhum.
            </div>
          </Radio>

          <Radio value="SELECTED" disabled={!hasSelection}>
            <span style={{ fontWeight: 600 }}>
              {hasSelection
                ? `As ${selectedInvoiceIds.length} fatura(s) selecionadas na lista`
                : "As faturas selecionadas na lista"}
            </span>
            {badge("SELECTED")}
            {!hasSelection && (
              <div style={{ fontSize: 12, opacity: 0.6 }}>
                Marque faturas na lista para usar esta opção — os filtros dela (fornecedor, datas,
                estado) servem para qualquer corte que não seja lote.
              </div>
            )}
          </Radio>
        </Radio.Group>

        {loading ? (
          <div style={{ textAlign: "center", padding: "10.2px" }}>
            <Spin /> <span style={{ fontSize: 13, marginLeft: 8 }}>A contar os documentos…</span>
          </div>
        ) : summary && count > 0 ? (
          <Alert
            type="success"
            showIcon
            message={nomeEsperado}
            description={
              summary.invoicesWithoutDocument > 0
                ? `${summary.invoicesWithoutDocument} fatura(s) deste âmbito não têm ficheiro anexado.`
                : undefined
            }
          />
        ) : summary ? (
          /* Zero. Um "não há documentos" seco deixava o utilizador preso sem saber
             porquê nem o que fazer — foi exatamente o que aconteceu a 2026-10-02 com
             o Lote 3 da Vila Aleu. Diz-se a causa e dá-se a saída. */
          <Alert
            type="warning"
            showIcon
            message={
              scope === "ASSOCIATED"
                ? lotId === ANY_LOT
                  ? "Nenhuma fatura desta obra está associada a rubricas."
                  : `Nenhuma fatura está classificada em rubricas do ${lot?.name ?? "lote"}.`
                : "Não há documentos neste âmbito."
            }
            description={
              scope === "ASSOCIATED" ? (
                <div style={{ display: "flex", flexDirection: "column", gap: "6.8px" }}>
                  <span>
                    O lote de uma fatura vem da rubrica onde é classificada. Enquanto não o for,
                    não pertence a lote nenhum — e não sai neste download.
                    {unclassifiedCount > 0 && (
                      <>
                        {" "}
                        Há <strong>{unclassifiedCount}</strong> ficheiro(s) em faturas ainda por
                        classificar.
                      </>
                    )}
                  </span>
                  <Space>
                    {unclassifiedCount > 0 && (
                      <Button size="small" onClick={() => setScope("UNCLASSIFIED")}>
                        Descarregar os {unclassifiedCount} por classificar
                      </Button>
                    )}
                    <Button size="small" type="link" onClick={onGoToClassify}>
                      Ir classificar faturas
                    </Button>
                  </Space>
                </div>
              ) : (
                "Nenhuma fatura deste âmbito tem ficheiro anexado."
              )
            }
          />
        ) : null}
      </div>
    </Modal>
  );
};
