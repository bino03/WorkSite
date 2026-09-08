import { useCallback, useEffect, useRef, useState } from "react";
import type { FC } from "react";
import { Empty, Input, Spin, Tooltip } from "antd";
import { SearchOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { searchBudgetItems } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { formatCurrency } from "@/utils/formatters";
import type { BudgetItemSearchResult } from "@/types/budget";

interface Props {
  enterpriseId: string;
  /** A rubrica atualmente escolhida, para se destacar na lista. */
  selectedId?: string | null;
  onPick: (item: BudgetItemSearchResult) => void;
  autoFocus?: boolean;
}

/**
 * Um campo só, que aceita código (`4.2`) ou texto (`betão`).
 *
 * É a alternativa ao `BudgetItemPickerModal` de dois passos, e existe porque no
 * ecrã de classificação a escolha da rubrica é o gesto principal, não uma
 * digressão: abrir um modal, escolher capítulo e depois rubrica são três
 * decisões onde devia haver uma.
 *
 * Cada resultado mostra o **caminho completo** e o gasto vs. orçamentado. O
 * caminho não é enfeite: num orçamento real "Betão" aparece em três capítulos e
 * o nome sozinho não os distingue. O gasto responde à pergunta que a pessoa tem
 * na cabeça — "ainda cabe aqui?" — sem a mandar a outro ecrã.
 */
export const RubricSearchField: FC<Props> = ({ enterpriseId, selectedId, onPick, autoFocus }) => {
  const { t } = useTranslation();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<BudgetItemSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  /**
   * Cada pesquisa leva um número; só a mais recente pode escrever no estado.
   * Sem isto, uma resposta lenta de "be" chega depois da de "betão" e repõe a
   * lista errada por baixo do que já está escrito.
   */
  const requestId = useRef(0);

  const run = useCallback(
    async (text: string) => {
      const mine = ++requestId.current;
      setLoading(true);
      try {
        const found = await searchBudgetItems(enterpriseId, text);
        if (mine === requestId.current) setResults(found);
      } catch (error) {
        if (mine === requestId.current) {
          setResults([]);
          ErrorHandler.handle(error, { showNotification: false });
        }
      } finally {
        if (mine === requestId.current) setLoading(false);
      }
    },
    [enterpriseId]
  );

  useEffect(() => {
    const text = query.trim();
    if (!text) {
      requestId.current++;
      setResults([]);
      setLoading(false);
      return;
    }
    const timer = window.setTimeout(() => void run(text), 250);
    return () => window.clearTimeout(timer);
  }, [query, run]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "10.2px", minHeight: 0 }}>
      <Input
        allowClear
        autoFocus={autoFocus}
        prefix={<SearchOutlined />}
        placeholder={t("invoices.classify.searchPlaceholder")}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />

      <Spin spinning={loading}>
        <div style={{ maxHeight: 320, overflowY: "auto", display: "flex", flexDirection: "column", gap: 4 }}>
          {query.trim() && !loading && results.length === 0 && (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={t("invoices.classify.searchEmpty")}
            />
          )}

          {results.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onPick(item)}
              style={{
                textAlign: "left",
                cursor: "pointer",
                padding: "8px 10px",
                borderRadius: 2,
                background: item.id === selectedId ? "var(--ind-color-surface)" : "transparent",
                border: `1px solid ${
                  item.id === selectedId ? "var(--ind-color-accent)" : "var(--ind-color-divider)"
                }`,
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, fontSize: 13 }}>
                <span style={{ fontWeight: 600 }}>
                  {item.code ? `${item.code} · ` : ""}
                  {item.name}
                </span>
                <span style={{ whiteSpace: "nowrap", opacity: item.overBudget ? 1 : 0.7 }}>
                  {t("invoices.classify.budgetVsSpent", {
                    spent: formatCurrency(item.spentTotal),
                    budget: formatCurrency(item.rolledUpBudget),
                  })}
                </span>
              </div>

              <div style={{ fontSize: 11, opacity: 0.55, marginTop: 2 }}>{item.path}</div>

              <div style={{ display: "flex", gap: 6, marginTop: 4 }}>
                {item.chapter && (
                  <Tooltip title={t("invoices.classify.chapterHint")}>
                    <span className="ind-tag ind-tag-neutral" style={{ fontSize: 10 }}>
                      {t("invoices.classify.chapterTag")}
                    </span>
                  </Tooltip>
                )}
                {item.overBudget && (
                  <span className="ind-tag ind-tag-outline" style={{ fontSize: 10 }}>
                    {t("invoices.classify.overBudget")}
                  </span>
                )}
              </div>
            </button>
          ))}
        </div>
      </Spin>
    </div>
  );
};

export default RubricSearchField;
