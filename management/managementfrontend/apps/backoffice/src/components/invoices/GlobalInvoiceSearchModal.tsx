import { useCallback, useEffect, useRef, useState } from "react";
import type { FC, KeyboardEvent } from "react";
import { Empty, Input, Modal, Spin } from "antd";
import { SearchOutlined } from "@ant-design/icons";

import { searchAllInvoices } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { formatCurrency, formatDate } from "@/utils/formatters";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import type { InvoiceSearchResult } from "@/types/invoice";

/** O mesmo mínimo do backend (`GLOBAL_SEARCH_MIN_LENGTH`): abaixo disto nem se pede. */
const MIN_LENGTH = 2;

interface Props {
  open: boolean;
  onClose: () => void;
}

/** Onde a fatura está, em palavras: a obra, ou o âmbito quando não tem obra. */
function whereLabel(row: InvoiceSearchResult): string {
  if (row.scope === "COMPANY") return "Despesas da empresa";
  if (row.scope === "UNIDENTIFIED") return "Por identificar";
  return row.enterpriseName ?? "Obra";
}

/**
 * A pesquisa global de faturas, aberta pelo botão do header ou por Ctrl+K:
 * "onde está aquela fatura?" sem saber em que obra, ou se na empresa ou na
 * quarentena. Um campo e uma lista curta; clicar abre o `InvoiceDetailDrawer`
 * por cima, e fechá-lo volta aos resultados.
 *
 * Modal e não página, de propósito: é um utilitário de passagem, como o
 * `BudgetItemPickerModal`. Só `ADMIN` e `ACCOUNTANT` — o gate real é o `@PreAuthorize`.
 */
export const GlobalInvoiceSearchModal: FC<Props> = ({ open, onClose }) => {
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<InvoiceSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [detailId, setDetailId] = useState<string | null>(null);
  /** Só a pesquisa mais recente escreve no estado — ver `RubricSearchField`. */
  const requestId = useRef(0);

  const run = useCallback(async (text: string) => {
    const mine = ++requestId.current;
    if (text.length < MIN_LENGTH) {
      setResults([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const found = await searchAllInvoices(text);
      if (mine === requestId.current) {
        setResults(found);
        setActiveIndex(0);
      }
    } catch (error) {
      if (mine === requestId.current) {
        setResults([]);
        ErrorHandler.handle(error);
      }
    } finally {
      if (mine === requestId.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void run(query.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [query, run]);

  // Cada abertura começa do zero: o Ctrl+K é para uma pesquisa nova.
  useEffect(() => {
    if (open) {
      setQuery("");
      setResults([]);
    }
  }, [open]);

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (results.length === 0) return;
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActiveIndex((i) => Math.min(i + 1, results.length - 1));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActiveIndex((i) => Math.max(i - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      setDetailId(results[activeIndex].id);
    }
  };

  const tooShort = query.trim().length < MIN_LENGTH;

  return (
    <>
      <Modal
        open={open}
        onCancel={onClose}
        footer={null}
        closable={false}
        destroyOnClose
        width="min(640px, 94vw)"
        styles={{ body: { display: "flex", flexDirection: "column", gap: "10.2px", maxHeight: "70vh" } }}
      >
        <Input
          autoFocus
          allowClear
          size="large"
          prefix={<SearchOutlined />}
          placeholder="Fornecedor, NIF, número, ATCUD, ficheiro, notas ou valor exato"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
        />

        <Spin spinning={loading}>
          <div style={{ maxHeight: "56vh", overflowY: "auto", display: "flex", flexDirection: "column", gap: 4 }}>
            {tooShort && (
              <div style={{ fontSize: 12, opacity: 0.55, padding: "4px 2px" }}>
                Procura em todas as obras, nas despesas da empresa e em "Por identificar".
              </div>
            )}

            {!tooShort && !loading && results.length === 0 && (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma fatura encontrada" />
            )}

            {results.map((row, index) => (
              <button
                key={row.id}
                type="button"
                onClick={() => setDetailId(row.id)}
                onMouseEnter={() => setActiveIndex(index)}
                style={{
                  textAlign: "left",
                  cursor: "pointer",
                  padding: "8px 10px",
                  borderRadius: 2,
                  background: index === activeIndex ? "var(--ind-color-surface)" : "transparent",
                  border: `1px solid ${
                    index === activeIndex ? "var(--ind-color-accent)" : "var(--ind-color-divider)"
                  }`,
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13 }}>
                  <span style={{ fontWeight: 600, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {row.invoiceNumber ?? "Sem número"}
                    {" · "}
                    {row.supplierName ?? row.supplierNif ?? "Fornecedor por identificar"}
                  </span>
                  <span style={{ whiteSpace: "nowrap" }}>
                    {row.totalAmount === null ? "—" : formatCurrency(row.totalAmount)}
                  </span>
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center", fontSize: 11, opacity: 0.7, marginTop: 2 }}>
                  {row.documentType === "CREDIT_NOTE" && (
                    <span className="ind-tag ind-tag-outline" style={{ fontSize: 10 }}>
                      Nota de crédito
                    </span>
                  )}
                  <span>{whereLabel(row)}</span>
                  <span>·</span>
                  <span>{row.invoiceDate ? formatDate(row.invoiceDate) : "sem data"}</span>
                </div>
              </button>
            ))}
          </div>
        </Spin>
      </Modal>

      <InvoiceDetailDrawer
        invoiceId={detailId}
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        // Apagada ou transferida, a fatura muda de sítio ou desaparece: repete-se a pesquisa.
        onChanged={() => void run(query.trim())}
        // Classificar é dentro da obra; aqui só se encontra.
        onAllocate={() => undefined}
      />
    </>
  );
};

export default GlobalInvoiceSearchModal;
