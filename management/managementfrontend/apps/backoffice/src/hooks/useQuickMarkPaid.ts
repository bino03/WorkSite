import dayjs from "dayjs";

import { markInvoicePaid } from "@/services/paymentService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatCurrency } from "@/utils/formatters";
import { isUnapprovedPayment } from "@/hooks/useInvoicePaymentApproval";
import type { ConstructionInvoice } from "@/types/invoice";

/**
 * "Marcar como pago" do menu de contexto: liquida o que falta, hoje, por
 * transferência. Para outra data, método, valor parcial ou prova, há o
 * "Registar pagamento…" (drawer completo).
 */
export function useQuickMarkPaid(onDone: () => void) {
  const confirm = useConfirm();

  return (invoice: ConstructionInvoice) => {
    const remaining = Math.round(((invoice.netAmount ?? 0) - invoice.paidAmount) * 100) / 100;
    confirm({
      title: "Marcar como paga",
      // Avisa e deixa: a aprovação não bloqueia o pagamento, só o assinala.
      message:
        `Registar o pagamento de ${formatCurrency(remaining)} hoje, por transferência?` +
        (isUnapprovedPayment(invoice)
          ? " Atenção: esta fatura ainda não foi aprovada para pagamento."
          : ""),
      actionLabel: "Marcar como paga",
      onConfirm: async () => {
        try {
          await markInvoicePaid(invoice.id, {
            paidOn: dayjs().format("YYYY-MM-DD"),
            method: "TRANSFERENCIA",
            amount: null,
          });
          notificationService.success("Pagamento registado");
          onDone();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };
}
