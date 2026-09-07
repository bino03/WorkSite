import type { FC } from "react";

import ScopedInvoicesPage from "@/pages/backoffice/invoices/ScopedInvoicesPage";

/**
 * A quarentena: chegou uma fatura e ainda não se sabe de quem é.
 *
 * Ordenada da mais antiga para a mais recente pelo backend — quanto mais tempo
 * uma fatura aqui está, mais urgente é identificá-la. É uma fila de trabalho,
 * não um arquivo.
 */
const UnidentifiedInvoicesPage: FC = () => (
  <ScopedInvoicesPage
    scope="UNIDENTIFIED"
    kicker="Faturas"
    title="Por identificar"
    emptyHint="Nada por identificar — todas as faturas têm dono."
  />
);

export default UnidentifiedInvoicesPage;
