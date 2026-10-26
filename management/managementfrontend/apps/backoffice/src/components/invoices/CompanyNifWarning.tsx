import type { FC } from "react";
import { Alert } from "antd";

/**
 * O NIF da Vilatro — o mesmo que o `BudgetExcelExportService` imprime no
 * cabeçalho do orçamento. Numa fatura de compra é o NIF do *cliente*, não do
 * fornecedor.
 */
export const COMPANY_NIF = "518849651";

export function isCompanyNif(nif: string | null | undefined): boolean {
  return (nif ?? "").replace(/\D/g, "") === COMPANY_NIF;
}

/**
 * Aviso por baixo do campo NIF do fornecedor quando lá está o NIF da empresa.
 * É o engano clássico de copiar o "Contribuinte" do bloco do cliente — e passa
 * por todas as verificações de duplicado, que contam com o NIF certo: foi assim
 * que a FT 20263/1222 da Brivel entrou duas vezes. Só avisa, não bloqueia.
 */
export const CompanyNifWarning: FC<{ nif: string | null | undefined }> = ({ nif }) =>
  isCompanyNif(nif) ? (
    <Alert
      type="warning"
      showIcon
      style={{ marginTop: 6, fontSize: 12 }}
      message="Este NIF é da Vilatro, não do fornecedor."
      description="Na fatura, o NIF da empresa aparece nos dados do cliente. O do fornecedor está no topo, junto ao nome dele."
    />
  ) : null;
