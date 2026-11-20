import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Space, Spin, Typography } from "antd";
import { CopyOutlined, PrinterOutlined, QrcodeOutlined, ReloadOutlined } from "@ant-design/icons";
import BlueprintCard from "@/components/common/BlueprintCard";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { generateClockCode, getClockCode, getClockQrPng } from "@/services/clockService";
import type { ClockCode } from "@/services/clockService";
import { formatDateTime } from "@/utils/formatters";

const { Text } = Typography;

interface Props {
  enterpriseId: string;
}

/**
 * O QR de picagem de uma obra, no drawer da obra (passo "QR de picagem", só ADMIN).
 * Está aqui e não à mão na lista de obras porque raramente se mexe: define-se uma vez,
 * imprime-se e cola-se à entrada da obra. Uma obra começa **sem** QR; "Criar QR" ou
 * "Gerar novo QR" são a mesma operação no backend — o código anterior, se havia, deixa
 * de picar logo (para quando um QR foi fotografado e partilhado).
 */
export function ClockQrCard({ enterpriseId }: Props) {
  const confirm = useConfirm();
  const [code, setCode] = useState<ClockCode | null>(null);
  const [imageUrl, setImageUrl] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const showImage = (png: Blob | null) =>
    setImageUrl((previous) => {
      if (previous) URL.revokeObjectURL(previous);
      return png ? URL.createObjectURL(png) : null;
    });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const info = await getClockCode(enterpriseId);
      setCode(info);
      showImage(info ? await getClockQrPng(enterpriseId) : null);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [enterpriseId]);

  useEffect(() => {
    void load();
    return () => showImage(null);
  }, [load]);

  const generate = async () => {
    setSaving(true);
    try {
      await generateClockCode(enterpriseId);
      notificationService.success(code ? "QR novo gerado — imprima-o e substitua o da obra" : "QR de picagem criado");
      await load();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const askToReplace = () =>
    confirm({
      title: "Gerar um QR novo?",
      message:
        "O QR atual deixa de picar logo. Vai ter de imprimir o novo e substituí-lo na obra — até lá, ninguém consegue picar com o antigo.",
      actionLabel: "Gerar novo QR",
      onConfirm: generate,
    });

  const copyLink = async () => {
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code.url);
      notificationService.success("Link copiado");
    } catch {
      notificationService.warning("Não foi possível copiar", code.url);
    }
  };

  /** Abre uma página só com o nome da obra e o QR, para imprimir em A4. */
  const print = () => {
    if (!code || !imageUrl) return;
    const win = window.open("", "_blank");
    if (!win) return;
    const doc = win.document;
    doc.title = `QR de picagem - ${code.enterpriseName}`;
    doc.body.style.cssText = "margin:0;font-family:sans-serif;text-align:center;padding:40px";
    const title = doc.createElement("h1");
    title.textContent = code.enterpriseName;
    title.style.cssText = "font-size:40px;margin:0 0 8px";
    const subtitle = doc.createElement("p");
    subtitle.textContent = "Picar ponto: leia este QR com a câmara do telemóvel";
    subtitle.style.cssText = "font-size:20px;margin:0 0 24px";
    const image = doc.createElement("img");
    image.src = imageUrl;
    image.style.cssText = "width:120mm;height:120mm";
    image.onload = () => win.print();
    const generated = doc.createElement("p");
    generated.textContent = `QR gerado a ${formatDateTime(code.generatedAt)}`;
    generated.style.cssText = "font-size:14px;color:#666;margin:16px 0 0";
    doc.body.append(title, subtitle, image, generated);
  };

  return (
    <BlueprintCard kicker="QR de picagem" style={{ padding: "13.6px", gap: "10.2px" }}>
      {loading ? (
        <div style={{ textAlign: "center", padding: "var(--ind-space-8)" }}>
          <Spin />
        </div>
      ) : !code ? (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", gap: "var(--ind-space-4)" }}>
          <Text type="secondary">
            Esta obra ainda não tem QR de picagem. Depois de criado, imprime-se e cola-se à entrada da obra: o
            funcionário lê-o com a câmara do telemóvel, entra com a sua conta e confirma a entrada ou a saída.
          </Text>
          <Button type="primary" icon={<QrcodeOutlined />} loading={saving} onClick={() => void generate()}>
            Criar QR
          </Button>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "var(--ind-space-4)" }}>
          {imageUrl && (
            <img src={imageUrl} alt={`QR de picagem da obra ${code.enterpriseName}`} style={{ width: 240, height: 240 }} />
          )}
          <Text type="secondary">
            Gerado a <strong>{formatDateTime(code.generatedAt)}</strong> — a mesma data sai na folha impressa, para
            se saber se o QR colado na obra é o atual.
          </Text>
          <Space wrap style={{ justifyContent: "center" }}>
            <Button type="primary" icon={<PrinterOutlined />} onClick={print} disabled={!imageUrl}>
              Imprimir
            </Button>
            <Button icon={<CopyOutlined />} onClick={() => void copyLink()}>
              Copiar link
            </Button>
            <Button icon={<ReloadOutlined />} loading={saving} onClick={askToReplace}>
              Gerar novo QR
            </Button>
          </Space>
          {!code.url.startsWith("https://") && (
            <Alert
              type="warning"
              showIcon
              message="Este QR aponta para um endereço local"
              description={`${code.url.split("/picar")[0]} só funciona neste computador. Imprima o QR a partir da app online.`}
            />
          )}
        </div>
      )}
    </BlueprintCard>
  );
}
