import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Modal, Space, Typography } from "antd";
import { FileTextOutlined, ZoomInOutlined, ZoomOutOutlined } from "@ant-design/icons";

const { Text } = Typography;

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.25;

interface InvoicePreviewModalProps {
  open: boolean;
  onClose: () => void;
  invoiceUrl: string | null;
  mimeType: string | null;
  filename: string | null;
}

export default function InvoicePreviewModal({ open, onClose, invoiceUrl, mimeType, filename }: InvoicePreviewModalProps) {
  const { t } = useTranslation();
  const [zoom, setZoom] = useState(1);
  const isImage = mimeType?.startsWith("image/") ?? false;

  return (
    <Modal
      title={
        <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <FileTextOutlined />
          {filename || t("constructionExpenses.fields.invoice")}
        </span>
      }
      open={open}
      onCancel={onClose}
      width={800}
      // O PDF já traz o zoom próprio do visualizador do browser — os botões
      // só fazem sentido para a imagem, onde não há nenhum.
      footer={
        isImage ? (
          <Space style={{ display: "flex", justifyContent: "center" }}>
            <Button
              icon={<ZoomOutOutlined />}
              disabled={zoom <= MIN_ZOOM}
              onClick={() => setZoom((z) => Math.max(MIN_ZOOM, +(z - ZOOM_STEP).toFixed(2)))}
              aria-label="Reduzir zoom"
            />
            <span style={{ fontSize: 12, opacity: 0.7, minWidth: 40, textAlign: "center" }}>
              {Math.round(zoom * 100)}%
            </span>
            <Button
              icon={<ZoomInOutlined />}
              disabled={zoom >= MAX_ZOOM}
              onClick={() => setZoom((z) => Math.min(MAX_ZOOM, +(z + ZOOM_STEP).toFixed(2)))}
              aria-label="Aumentar zoom"
            />
          </Space>
        ) : null
      }
      bodyStyle={{ padding: 0, maxHeight: "75vh", overflow: "auto" }}
      destroyOnClose
    >
      {invoiceUrl ? (
        isImage ? (
          <img
            src={invoiceUrl}
            alt={filename ?? "invoice"}
            style={{ width: `${zoom * 100}%`, display: "block", margin: "0 auto" }}
          />
        ) : (
          <embed src={invoiceUrl} type="application/pdf" style={{ width: "100%", height: "75vh", display: "block" }} />
        )
      ) : (
        <div style={{ padding: 24 }}>
          <Text type="secondary">{t("constructionExpenses.fields.noInvoicePreview")}</Text>
        </div>
      )}
    </Modal>
  );
}
