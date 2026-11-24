import { useState } from "react";
import type { FC } from "react";
import { Button, Dropdown } from "antd";
import { DownOutlined, ExportOutlined, FileExcelOutlined, FilePdfOutlined } from "@ant-design/icons";

import { exportPaymentList } from "@/services/invoiceService";
import type { PaymentListFormat } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { downloadBlob } from "@/utils/downloadBlob";
import type { OutstandingByUrgencyParams } from "@/types/invoice";

interface Props {
  /** Os filtros de "A pagar" em vigor; o backend junta-lhes `approvedForPayment=true`. */
  params?: Omit<OutstandingByUrgencyParams, "page" | "size" | "approvedForPayment">;
  size?: "small" | "middle";
}

/**
 * "Exportar" → Excel ou PDF: a lista das faturas aprovadas para pagamento e
 * ainda por pagar, por fornecedor, com NIF, nº da fatura, montante e os IBAN
 * de cada banco. Vive em "A pagar" (com os filtros da página) e no cartão
 * "Aprovadas para pagamento" da página inicial (sem filtros).
 */
export const PaymentListExportButton: FC<Props> = ({ params = {}, size = "middle" }) => {
  const [busy, setBusy] = useState(false);

  const run = async (format: PaymentListFormat) => {
    setBusy(true);
    try {
      const file = await exportPaymentList(params, format);
      downloadBlob(file.blob, file.fileName);
      notificationService.success("Exportação", `Ficheiro "${file.fileName}" gerado.`);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dropdown
      trigger={["click"]}
      disabled={busy}
      menu={{
        items: [
          { key: "XLSX", icon: <FileExcelOutlined />, label: "Excel (.xlsx)" },
          { key: "PDF", icon: <FilePdfOutlined />, label: "PDF" },
        ],
        onClick: ({ key }) => run(key as PaymentListFormat),
      }}
    >
      <Button size={size} icon={<ExportOutlined />} loading={busy} aria-label="Exportar as faturas aprovadas para pagamento">
        Exportar <DownOutlined />
      </Button>
    </Dropdown>
  );
};

export default PaymentListExportButton;
