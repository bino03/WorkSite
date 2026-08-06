import { useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Checkbox, DatePicker, Drawer, Space } from "antd";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";

import { updateBudgetItem } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { BudgetItemNode } from "@/types/budget";
import { describeHints } from "./budgetTree";

interface Props {
  item: BudgetItemNode | null;
  enterpriseId: string;
  /** Ascendentes sem data, calculados no cliente a partir da árvore já carregada. */
  ancestorsMissing: { start: BudgetItemNode[]; end: BudgetItemNode[] } | null;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

export const BudgetDatesDrawer: FC<Props> = ({
  item,
  enterpriseId,
  ancestorsMissing,
  open,
  onClose,
  onSaved,
}) => {
  const { t } = useTranslation();
  const [start, setStart] = useState<string | null>(null);
  const [end, setEnd] = useState<string | null>(null);
  const [propagateStart, setPropagateStart] = useState(false);
  const [propagateEnd, setPropagateEnd] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open || !item) return;
    setStart(item.startDate);
    setEnd(item.endDate);
    setPropagateStart(false);
    setPropagateEnd(false);
  }, [open, item]);

  // A opção só faz sentido quando se está mesmo a definir a data e há
  // ascendentes sem ela — o cliente sabe isto sem perguntar ao servidor.
  const showPropagateStart = !!start && (ancestorsMissing?.start.length ?? 0) > 0;
  const showPropagateEnd = !!end && (ancestorsMissing?.end.length ?? 0) > 0;

  const ancestorLabel = (nodes: BudgetItemNode[] | undefined) =>
    (nodes ?? []).map((n) => n.code ?? n.name).join(", ");

  const submit = async () => {
    if (!item) return;
    setSaving(true);
    try {
      const result = await updateBudgetItem(item.id, {
        enterpriseId,
        parentId: item.parentId,
        rowKind: item.rowKind,
        code: item.code,
        name: item.name,
        unit: item.unit,
        quantity: item.quantity,
        unitPrice: item.unitPrice,
        totalPrice: item.totalPrice,
        observations: item.observations,
        startDate: start,
        endDate: end,
        propagateStartDate: propagateStart,
        propagateEndDate: propagateEnd,
      });

      // Rede de segurança: se a árvore em memória estivesse desatualizada, o
      // backend devolve aqui os ascendentes que ficaram por preencher.
      if (result.datePropagationHints.length > 0) {
        notificationService.info(
          "Datas",
          `As etapas ${describeHints(result.datePropagationHints)} continuam sem data. Reabra e marque a opção para as preencher.`
        );
      } else {
        notificationService.success("Datas", "Datas atualizadas.");
      }
      onSaved();
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={480}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>
            Rubrica {item?.code ?? ""}
          </h6>
          <h2 style={{ margin: 0 }}>Datas</h2>
        </div>
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={saving}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={submit} loading={saving}>
            {t("common.save")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <div>
          <label style={{ display: "block", fontSize: 12, marginBottom: 5, opacity: 0.7 }}>
            Data de início
          </label>
          <DatePicker
            style={{ width: "100%" }}
            format="DD/MM/YYYY"
            value={start ? dayjs(start) : null}
            onChange={(d) => setStart(d ? d.format("YYYY-MM-DD") : null)}
          />
        </div>

        {showPropagateStart && (
          <Checkbox checked={propagateStart} onChange={(e) => setPropagateStart(e.target.checked)}>
            <span style={{ fontSize: 13 }}>
              aplicar também às etapas acima ({ancestorLabel(ancestorsMissing?.start)})
            </span>
          </Checkbox>
        )}

        <div>
          <label style={{ display: "block", fontSize: 12, marginBottom: 5, opacity: 0.7 }}>
            Data de fim
          </label>
          <DatePicker
            style={{ width: "100%" }}
            format="DD/MM/YYYY"
            value={end ? dayjs(end) : null}
            onChange={(d) => setEnd(d ? d.format("YYYY-MM-DD") : null)}
          />
        </div>

        {showPropagateEnd && (
          <Checkbox checked={propagateEnd} onChange={(e) => setPropagateEnd(e.target.checked)}>
            <span style={{ fontSize: 13 }}>
              aplicar também às etapas acima ({ancestorLabel(ancestorsMissing?.end)})
            </span>
          </Checkbox>
        )}
      </div>
    </Drawer>
  );
};
