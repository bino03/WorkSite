import type { FC } from "react";
import { Typography } from "antd";

import { formatIban } from "@/utils/iban";

interface Props {
  /** O `supplierIban` da fatura — vem da ficha do fornecedor com o mesmo NIF. */
  iban: string | null | undefined;
}

/**
 * O IBAN do fornecedor, para copiar para a transferência no homebanking.
 *
 * Copia-se sem espaços (o homebanking aceita assim e não há espaço a mais para
 * estragar a colagem); mostra-se de 4 em 4, como vem impresso nas faturas,
 * para se conferir de olhos.
 */
export const SupplierIbanLine: FC<Props> = ({ iban }) => (
  <div style={{ fontSize: 13 }}>
    <span style={{ opacity: 0.7 }}>IBAN do fornecedor: </span>
    {iban ? (
      <Typography.Text strong copyable={{ text: iban, tooltips: ["Copiar IBAN", "Copiado"] }}>
        {formatIban(iban)}
      </Typography.Text>
    ) : (
      <Typography.Text type="secondary">
        sem IBAN na ficha — acrescente-o em Fornecedores
      </Typography.Text>
    )}
  </div>
);
