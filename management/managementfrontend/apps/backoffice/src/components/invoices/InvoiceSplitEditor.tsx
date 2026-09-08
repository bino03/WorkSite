import type { FC } from "react";
import { Alert, Button, InputNumber } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { formatCurrency } from "@/utils/formatters";
import { splitSum } from "@/components/invoices/invoiceSplit";
import type { DraftSplitLine } from "@/components/invoices/invoiceSplit";

interface Props {
  lines: DraftSplitLine[];
  /** Total da fatura. Null = fatura ainda sem total (as linhas nascem a zero). */
  total: number | null;
  /** Qual das linhas está a receber o que a pesquisa escolher. */
  editingIndex: number | null;
  onEditLine: (index: number) => void;
  onChange: (lines: DraftSplitLine[]) => void;
}

/**
 * As linhas de uma fatura repartida, com a soma ao vivo.
 *
 * A regra que o desenho serve: a soma tem de **esgotar** o total, e o backend
 * recusa se não esgotar. Em vez de deixar a pessoa descobrir isso ao gravar, o
 * que falta está sempre à vista, e há um botão que põe o resto na linha — que é
 * o gesto real de quem reparte "300 disto, o resto daquilo".
 */
export const InvoiceSplitEditor: FC<Props> = ({
  lines,
  total,
  editingIndex,
  onEditLine,
  onChange,
}) => {
  const { t } = useTranslation();

  const sum = splitSum(lines);
  const remaining = total == null ? null : Math.round((total - sum) * 100) / 100;

  const setLine = (index: number, patch: Partial<DraftSplitLine>) =>
    onChange(lines.map((line, i) => (i === index ? { ...line, ...patch } : line)));

  const addLine = () => {
    onChange([...lines, { budgetItemId: null, label: null, amount: remaining ?? null }]);
    onEditLine(lines.length);
  };

  const removeLine = (index: number) => {
    onChange(lines.filter((_, i) => i !== index));
    if (editingIndex === index) onEditLine(Math.max(0, index - 1));
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "10.2px" }}>
      {lines.map((line, index) => (
        <div
          key={index}
          style={{
            display: "grid",
            gridTemplateColumns: "1fr minmax(130px, auto) auto",
            gap: 6,
            alignItems: "center",
          }}
        >
          <Button
            block
            style={{ textAlign: "left" }}
            type={editingIndex === index ? "primary" : "default"}
            ghost={editingIndex === index}
            onClick={() => onEditLine(index)}
          >
            {line.label ?? t("invoices.classify.searchPlaceholder")}
          </Button>

          <InputNumber
            value={line.amount}
            onChange={(value) => setLine(index, { amount: value ?? null })}
            precision={2}
            decimalSeparator=","
            addonAfter="€"
            disabled={total == null}
            style={{ width: "100%" }}
          />

          <Button
            type="text"
            icon={<DeleteOutlined />}
            onClick={() => removeLine(index)}
            disabled={lines.length === 1}
            aria-label="Remover linha"
          />
        </div>
      ))}

      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <Button size="small" icon={<PlusOutlined />} onClick={addLine}>
          {t("invoices.classify.split")}
        </Button>

        {remaining != null && remaining !== 0 && (
          <>
            <span style={{ fontSize: 12, opacity: 0.75 }}>
              {t("invoices.classify.splitRemaining", { amount: formatCurrency(remaining) })}
            </span>
            <Button
              size="small"
              type="link"
              onClick={() =>
                setLine(lines.length - 1, {
                  amount: Math.round(((lines[lines.length - 1].amount ?? 0) + remaining) * 100) / 100,
                })
              }
            >
              {t("invoices.classify.splitFillRest")}
            </Button>
          </>
        )}
      </div>

      {total == null && (
        <Alert type="info" showIcon message={t("invoices.classify.provisional")} />
      )}

      {total != null && remaining !== 0 && (
        <Alert
          type="warning"
          showIcon
          message={t("invoices.classify.splitMustMatch", { total: formatCurrency(total) })}
        />
      )}
    </div>
  );
};

export default InvoiceSplitEditor;
