import type { FC } from "react";

import ScopedInvoicesPage from "@/pages/backoffice/invoices/ScopedInvoicesPage";

/**
 * A quarentena: chegou uma fatura e ainda não se sabe de quem é.
 *
 * Ordenada pela data da fatura, da mais recente para a mais antiga, como as
 * outras listas de faturas (até 2026-11-13 vinha da mais antiga). É uma fila
 * de trabalho, não um arquivo.
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
