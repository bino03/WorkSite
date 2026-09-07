import api from "@/api";
import type {
  AggregatePaymentPayload,
  AggregatePaymentResult,
  MarkPaidPayload,
  PaymentResponse,
} from "@/types/invoice";

/**
 * Pagamentos de faturas de obra (fase 2). Tudo `ADMIN`.
 *
 * O estado de pagamento de uma fatura (`paymentStatus`/`paidAmount`/`payments`)
 * não se pede aqui — vem derivado em todos os DTOs de fatura. Estas funções só
 * criam e anulam movimentos.
 *
 * Sem `try/catch`: o `errorCode` do backend sobe intacto até ao `ErrorHandler`
 * do componente.
 */

function jsonPart(value: unknown): Blob {
  return new Blob([JSON.stringify(value)], { type: "application/json" });
}

/** Marca **uma** fatura como paga. `proof` (recibo/extrato) é opcional. */
export async function markInvoicePaid(
  invoiceId: string,
  payload: MarkPaidPayload,
  proof?: File | null
): Promise<PaymentResponse> {
  const form = new FormData();
  form.append("payment", jsonPart(payload));
  if (proof) form.append("proof", proof);

  const { data } = await api.post<PaymentResponse>(
    `/construction-invoices/${invoiceId}/payments`,
    form,
    { headers: { "Content-Type": "multipart/form-data" } }
  );
  return data;
}

/**
 * Um movimento que liquida N faturas. Se o valor não bater com a soma do que
 * falta pagar nas faturas escolhidas, `result.created` vem `false` e
 * `result.leftOut` diz quais tirar da seleção — **nada foi gravado**.
 */
export async function registerAggregatePayment(
  payload: AggregatePaymentPayload,
  proof?: File | null
): Promise<AggregatePaymentResult> {
  const form = new FormData();
  form.append("payment", jsonPart(payload));
  if (proof) form.append("proof", proof);

  const { data } = await api.post<AggregatePaymentResult>(
    `/construction-invoices/payments`,
    form,
    { headers: { "Content-Type": "multipart/form-data" } }
  );
  return data;
}

/** Anula um pagamento: apaga as ligações e o movimento, as faturas voltam a por liquidar. */
export async function deletePayment(paymentId: string): Promise<void> {
  await api.delete(`/construction-invoices/payments/${paymentId}`);
}
