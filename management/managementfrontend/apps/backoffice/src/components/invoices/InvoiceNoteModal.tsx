import { useEffect, useState } from "react";
import type { FC } from "react";
import { Input, Modal } from "antd";

import { setInvoiceNotes } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { ConstructionInvoice } from "@/types/invoice";

interface Props {
  /** A fatura a anotar; `null` fecha o modal. */
  invoice: ConstructionInvoice | null;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Escrever a nota de uma fatura sem abrir o detalhe. Se já tem nota, aparece
 * aqui para editar — gravar vazio apaga-a.
 */
export const InvoiceNoteModal: FC<Props> = ({ invoice, onClose, onSaved }) => {
  const [value, setValue] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setValue(invoice?.notes ?? "");
  }, [invoice]);

  const save = async () => {
    if (!invoice) return;
    setSaving(true);
    try {
      await setInvoiceNotes(invoice.id, value);
      notificationService.success("Nota guardada");
      onSaved();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const label = invoice?.invoiceNumber ?? invoice?.supplierName ?? "fatura";

  return (
    <Modal
      open={invoice !== null}
      title={`${invoice?.notes ? "Nota" : "Adicionar nota"} · ${label}`}
      okText="Guardar"
      cancelText="Cancelar"
      confirmLoading={saving}
      onOk={() => void save()}
      onCancel={onClose}
      destroyOnClose
    >
      <Input.TextArea
        autoFocus
        rows={5}
        maxLength={2000}
        showCount
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    </Modal>
  );
};

export default InvoiceNoteModal;
