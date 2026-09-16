import { useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Modal, TreeSelect } from "antd";

import { moveBudgetItem } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { BudgetItemNode, BudgetTree } from "@/types/budget";
import { buildParentTreeOptions } from "./budgetTree";

interface Props {
  open: boolean;
  tree: BudgetTree | null;
  item: BudgetItemNode | null;
  onClose: () => void;
  onMoved: () => void;
}

/**
 * Seletor de rubrica-mãe para "Mover para…" — um utilitário curto, não a
 * edição da entidade (essa é o `BudgetItemDrawer`), por isso Modal e não
 * Drawer.
 */
export const BudgetMoveToModal: FC<Props> = ({ open, tree, item, onClose, onMoved }) => {
  const [targetParentId, setTargetParentId] = useState<string | undefined>(undefined);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) setTargetParentId(item?.parentId ?? undefined);
  }, [open, item]);

  const options = buildParentTreeOptions(tree?.roots ?? [], item?.id);

  const submit = async () => {
    if (!item) return;
    setSaving(true);
    try {
      await moveBudgetItem(item.id, targetParentId ?? null);
      notificationService.success("Rubrica", "Rubrica movida.");
      onMoved();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onCancel={onClose}
      title={`Mover "${item?.name ?? ""}" para…`}
      footer={
        <>
          <Button onClick={onClose} disabled={saving}>
            Cancelar
          </Button>
          <Button type="primary" onClick={submit} loading={saving}>
            Mover
          </Button>
        </>
      }
      destroyOnClose
    >
      <TreeSelect
        value={targetParentId}
        onChange={(v) => setTargetParentId(v ?? undefined)}
        treeData={options}
        allowClear
        showSearch
        treeDefaultExpandAll
        placeholder="Topo (sem mãe)"
        treeNodeFilterProp="title"
        style={{ width: "100%" }}
      />
    </Modal>
  );
};
