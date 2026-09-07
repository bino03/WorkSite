import type { FC } from "react";

import ScopedInvoicesPage from "@/pages/backoffice/invoices/ScopedInvoicesPage";

/**
 * Despesas da empresa: faturas que não pertencem a obra nenhuma e por isso não
 * entram em orçamento nenhum — seguros, contabilidade, material de escritório.
 */
const CompanyInvoicesPage: FC = () => (
  <ScopedInvoicesPage
    scope="COMPANY"
    kicker="Faturas"
    title="Despesas da empresa"
    emptyHint="Ainda não há despesas da empresa registadas."
  />
);

export default CompanyInvoicesPage;
