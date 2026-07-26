import { useTranslation } from "react-i18next";
import { Modal, Typography } from "antd";
import { FileTextOutlined } from "@ant-design/icons";

const { Text } = Typography;

interface InvoicePreviewModalProps {
  open: boolean;
  onClose: () => void;
  invoiceUrl: string | null;
  mimeType: string | null;
  filename: string | null;
}

export default function InvoicePreviewModal({ open, onClose, invoiceUrl, mimeType, filename }: InvoicePreviewModalProps) {
  const { t } = useTranslation();

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
      footer={null}
      bodyStyle={{ padding: 0 }}
      destroyOnClose
    >
      {invoiceUrl ? (
        mimeType?.startsWith("image/") ? (
          <img src={invoiceUrl} alt={filename ?? "invoice"} style={{ width: "100%", display: "block" }} />
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
