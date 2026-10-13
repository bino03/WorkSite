import { setInvoicePaymentApproval } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { ConstructionInvoice } from "@/types/invoice";

/**
 * Só se aprova o que ainda há para pagar — a mesma regra do backend
 * (`setApprovedForPayment`), para a ação nem aparecer onde ia ser recusada.
 */
export function canApproveForPayment(invoice: ConstructionInvoice): boolean {
  return (
    invoice.documentType !== "CREDIT_NOTE" &&
    !invoice.approvedForPayment &&
    invoice.paymentStatus !== "PAID"
  );
}

/** Por liquidar e ainda sem aprovação: é o caso em que pagar avisa (e deixa). */
export function isUnapprovedPayment(invoice: ConstructionInvoice): boolean {
  return invoice.documentType !== "CREDIT_NOTE" && !invoice.approvedForPayment;
}

/**
 * Aprovar para pagamento e retirar a aprovação. Sem diálogo de confirmação:
 * não mexe em dinheiro e desfaz-se com um clique.
 */
export function useInvoicePaymentApproval(onDone: () => void) {
  /** Aprova se não estiver aprovada; retira se estiver. */
  const toggle = async (invoice: ConstructionInvoice) => {
    try {
      await setInvoicePaymentApproval(invoice.id, !invoice.approvedForPayment);
      notificationService.success(
        "Pagamento",
        invoice.approvedForPayment ? "Aprovação retirada." : "Fatura aprovada para pagamento."
      );
      onDone();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  /**
   * Aprova várias de seguida. Só aprova, nunca retira em bloco — a mesma
   * cautela de "marcar como enviadas". Para à primeira falha e diz quantas
   * ficaram feitas.
   */
  const approveMany = async (invoices: ConstructionInvoice[]) => {
    let approved = 0;
    try {
      for (const invoice of invoices) {
        await setInvoicePaymentApproval(invoice.id, true);
        approved++;
      }
      notificationService.success(
        "Pagamento",
        `${approved} fatura${approved > 1 ? "s aprovadas" : " aprovada"} para pagamento.`
      );
    } catch (error) {
      ErrorHandler.handle(error);
      if (approved > 0) {
        notificationService.info(
          "Pagamento",
          `${approved} de ${invoices.length} ficaram aprovadas antes do erro.`
        );
      }
    } finally {
      onDone();
    }
  };

  return { toggle, approveMany };
}
