import type { FC } from "react";
import { Typography } from "antd";

import { bankOfIban, formatIban } from "@/utils/iban";

interface Props {
  /** Os `supplierIbans` da fatura — vêm da ficha do fornecedor com o mesmo NIF. */
  ibans: string[] | null | undefined;
}

/**
 * Os IBAN do fornecedor (um por banco), para copiar para a transferência no
 * homebanking — escolhe-se ao pagar, normalmente o do mesmo banco de onde sai.
 *
 * Copia-se sem espaços (o homebanking aceita assim e não há espaço a mais para
 * estragar a colagem); mostra-se de 4 em 4, como vem impresso nas faturas,
 * para se conferir de olhos.
 */
export const SupplierIbanLine: FC<Props> = ({ ibans }) => (
  <div style={{ fontSize: 13, display: "flex", flexDirection: "column", gap: 2 }}>
    <span style={{ opacity: 0.7 }}>
      {ibans && ibans.length > 1 ? "IBAN do fornecedor (escolha o banco):" : "IBAN do fornecedor:"}
    </span>
    {ibans && ibans.length > 0 ? (
      ibans.map((iban) => (
        <div key={iban} data-iban={iban}>
          <span style={{ opacity: 0.7 }}>{bankOfIban(iban) ?? "IBAN"} · </span>
          <Typography.Text strong copyable={{ text: iban, tooltips: ["Copiar IBAN", "Copiado"] }}>
            {formatIban(iban)}
          </Typography.Text>
        </div>
      ))
    ) : (
      <Typography.Text type="secondary">
        sem IBAN na ficha — acrescente-o em Fornecedores
      </Typography.Text>
    )}
  </div>
);
