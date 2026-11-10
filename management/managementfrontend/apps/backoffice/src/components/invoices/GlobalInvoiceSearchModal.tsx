import { useCallback, useEffect, useRef, useState } from "react";
import type { FC, KeyboardEvent } from "react";
import { Empty, Input, Modal, Select, Spin } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { useIsMobile } from "@/hooks/useIsMobile";

import { searchAllInvoices } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { formatCurrency, formatDate } from "@/utils/formatters";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import {
  ALL_WORKS,
  SupplierFilterSelect,
  WorkFilterSelect,
  type WorkFilter,
} from "@/components/invoices/InvoiceFilterSelects";
import type { GlobalInvoiceSearchParams, InvoiceSearchResult } from "@/types/invoice";

/** O mesmo mínimo do backend (`GLOBAL_SEARCH_MIN_LENGTH`): sem filtros, abaixo disto nem se pede. */
const MIN_LENGTH = 2;
/** Com texto chega a lista curta; só com filtros pede-se o máximo do backend. */
const TEXT_LIMIT = 20;
const FILTER_ONLY_LIMIT = 50;

type PaymentFilter = NonNullable<GlobalInvoiceSearchParams["paymentStatus"]>;

const PAYMENT_OPTIONS: { value: PaymentFilter; label: string }[] = [
  { value: "OUTSTANDING", label: "Por liquidar" },
  { value: "UNPAID", label: "Nada pago" },
  { value: "PARTIAL", label: "Parcialmente pagas" },
  { value: "PAID", label: "Pagas" },
];

interface Filters {
  work: WorkFilter;
  supplierNif: string | null;
  paymentStatus: PaymentFilter | null;
}

const NO_FILTERS: Filters = { work: ALL_WORKS, supplierNif: null, paymentStatus: null };

function hasAnyFilter(filters: Filters): boolean {
  return (
    filters.work.enterpriseId !== null ||
    filters.work.scope !== null ||
    filters.supplierNif !== null ||
    filters.paymentStatus !== null
  );
}

interface Props {
  open: boolean;
  onClose: () => void;
}

/** Onde a fatura está, em palavras: a obra (e o lote, se a obra tem vários), ou o âmbito quando não tem obra. */
function whereLabel(row: InvoiceSearchResult): string {
  if (row.scope === "COMPANY") return "Despesas da empresa";
  if (row.scope === "UNIDENTIFIED") return "Por identificar";
  const enterprise = row.enterpriseName ?? "Obra";
  return row.budgetName ? `${enterprise} · ${row.budgetName}` : enterprise;
}

/**
 * A pesquisa global de faturas, aberta pelo botão do header ou por Ctrl+K:
 * "onde está aquela fatura?" sem saber em que obra, ou se na empresa ou na
 * quarentena. Um campo, três filtros pequenos por baixo (obra, fornecedor,
 * pagamento — 2026-11-10) e uma lista curta; clicar abre o `InvoiceDetailDrawer`
 * por cima, e fechá-lo volta aos resultados. Com um filtro, o texto é opcional.
 *
 * Modal e não página, de propósito: é um utilitário de passagem, como o
 * `BudgetItemPickerModal`. Só `ADMIN` e `ACCOUNTANT` — o gate real é o `@PreAuthorize`.
 */
export const GlobalInvoiceSearchModal: FC<Props> = ({ open, onClose }) => {
  const { t } = useTranslation();
  const isMobile = useIsMobile();
  const [query, setQuery] = useState("");
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [results, setResults] = useState<InvoiceSearchResult[]>([]);
  const [limitUsed, setLimitUsed] = useState(TEXT_LIMIT);
  const [loading, setLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  const [detailId, setDetailId] = useState<string | null>(null);
  /** Só a pesquisa mais recente escreve no estado — ver `RubricSearchField`. */
  const requestId = useRef(0);

  const run = useCallback(async (text: string, current: Filters) => {
    const mine = ++requestId.current;
    if (!hasAnyFilter(current) && text.length < MIN_LENGTH) {
      setResults([]);
      setLoading(false);
      return;
    }
    const size = text ? TEXT_LIMIT : FILTER_ONLY_LIMIT;
    setLoading(true);
    try {
      const found = await searchAllInvoices({
        q: text || undefined,
        enterpriseId: current.work.enterpriseId ?? undefined,
        scope: current.work.scope ?? undefined,
        supplierNif: current.supplierNif ?? undefined,
        paymentStatus: current.paymentStatus ?? undefined,
        size,
      });
      if (mine === requestId.current) {
        setResults(found);
        setLimitUsed(size);
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
    const timer = window.setTimeout(() => void run(query.trim(), filters), 250);
    return () => window.clearTimeout(timer);
  }, [query, filters, run]);

  // Cada abertura começa do zero: o Ctrl+K é para uma pesquisa nova.
  useEffect(() => {
    if (open) {
      setQuery("");
      setFilters(NO_FILTERS);
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

  const idle = !hasAnyFilter(filters) && query.trim().length < MIN_LENGTH;
  const selectWidth = isMobile ? "100%" : 180;

  return (
    <>
      <Modal
        open={open}
        onCancel={onClose}
        footer={null}
        closable={isMobile}
        destroyOnClose
        width="min(640px, 94vw)"
        styles={{ body: { display: "flex", flexDirection: "column", gap: "10.2px", maxHeight: isMobile ? undefined : "70vh" } }}
      >
        <Input
          autoFocus={!isMobile}
          allowClear
          size="large"
          prefix={<SearchOutlined />}
          placeholder="Fornecedor, NIF, número, ATCUD, ficheiro, notas ou valor exato"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
        />

        <div style={{ display: "flex", flexWrap: "wrap", gap: 6.8 }}>
          <WorkFilterSelect
            includeUnidentified
            value={filters.work}
            onChange={(work) => setFilters((prev) => ({ ...prev, work }))}
            style={{ width: selectWidth }}
          />
          <SupplierFilterSelect
            value={filters.supplierNif}
            onChange={(supplierNif) => setFilters((prev) => ({ ...prev, supplierNif }))}
            style={{ width: selectWidth }}
          />
          <Select
            allowClear
            size="small"
            placeholder="Pagamento"
            value={filters.paymentStatus ?? undefined}
            onChange={(paymentStatus?: PaymentFilter) =>
              setFilters((prev) => ({ ...prev, paymentStatus: paymentStatus ?? null }))
            }
            options={PAYMENT_OPTIONS}
            style={{ width: isMobile ? "100%" : 160 }}
          />
        </div>

        <Spin spinning={loading}>
          <div style={{ maxHeight: isMobile ? "calc(100dvh - 240px)" : "52vh", overflowY: "auto", display: "flex", flexDirection: "column", gap: 4 }}>
            {idle && (
              <div style={{ fontSize: 12, opacity: 0.55, padding: "4px 2px" }}>
                Procura em todas as obras, nas despesas da empresa e em "Por identificar".
              </div>
            )}

            {!idle && !loading && results.length === 0 && (
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
                  {row.documentType === "CREDIT_NOTE" ? (
                    <span className="ind-tag ind-tag-outline" style={{ fontSize: 10 }}>
                      Nota de crédito
                    </span>
                  ) : (
                    // As mesmas cores do selo no detalhe da fatura.
                    <span
                      className={`ind-tag ${
                        row.paymentStatus === "PAID"
                          ? "ind-tag-accent-2"
                          : row.paymentStatus === "PARTIAL"
                            ? "ind-tag-accent"
                            : "ind-tag-outline"
                      }`}
                      style={{ fontSize: 10 }}
                    >
                      {t(`invoices.payment.status.${row.paymentStatus}`)}
                    </span>
                  )}
                  <span>{whereLabel(row)}</span>
                  <span>·</span>
                  <span>{row.invoiceDate ? formatDate(row.invoiceDate) : "sem data"}</span>
                </div>
              </button>
            ))}

            {!loading && results.length >= limitUsed && (
              <div style={{ fontSize: 12, opacity: 0.55, padding: "4px 2px" }}>
                A mostrar as {limitUsed} mais recentes — afine a pesquisa para ver outras.
              </div>
            )}
          </div>
        </Spin>
      </Modal>

      <InvoiceDetailDrawer
        invoiceId={detailId}
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        // Apagada ou transferida, a fatura muda de sítio ou desaparece: repete-se a pesquisa.
        onChanged={() => void run(query.trim(), filters)}
        // Classificar é dentro da obra; aqui só se encontra.
        onAllocate={() => undefined}
      />
    </>
  );
};

export default GlobalInvoiceSearchModal;
