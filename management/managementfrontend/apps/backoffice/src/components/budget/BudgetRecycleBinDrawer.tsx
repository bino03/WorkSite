import { useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Drawer, Empty, List, Spin } from "antd";
import { UndoOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import { listDeletedBudgetItems, recoverBudgetItem } from "@/services/budgetService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { BudgetItemDeleted } from "@/types/budget";

interface Props {
  open: boolean;
  enterpriseId: string;
  onClose: () => void;
  /** A árvore principal tem de recarregar depois de uma recuperação. */
  onRecovered: () => void;
}

function daysRemaining(purgeAt: string): number {
  return Math.max(0, Math.ceil((new Date(purgeAt).getTime() - Date.now()) / 86_400_000));
}

const ROW_KIND_LABEL: Record<BudgetItemDeleted["rowKind"], string> = {
  ITEM: "Rubrica",
  HEADING: "Título",
  NOTE: "Nota",
};

/**
 * A janela dos 30 dias entre eliminar e a purga automática
 * (`ConstructionBudgetItemPurgeConfig`, no servidor). "Recuperar" volta à mãe
 * original se ela ainda existir; ao topo caso contrário.
 */
export const BudgetRecycleBinDrawer: FC<Props> = ({ open, enterpriseId, onClose, onRecovered }) => {
  const [items, setItems] = useState<BudgetItemDeleted[]>([]);
  const [loading, setLoading] = useState(false);
  const [recoveringId, setRecoveringId] = useState<string | null>(null);

  const fetchDeleted = async () => {
    setLoading(true);
    try {
      setItems(await listDeletedBudgetItems(enterpriseId));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (open) fetchDeleted();
  }, [open, enterpriseId]);

  const recover = async (item: BudgetItemDeleted) => {
    setRecoveringId(item.id);
    try {
      await recoverBudgetItem(item.id);
      notificationService.success("Rubrica", `"${item.name}" recuperada.`);
      setItems((prev) => prev.filter((i) => i.id !== item.id));
      onRecovered();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setRecoveringId(null);
    }
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={480}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Orçamento</h6>
          <h2 style={{ margin: 0 }}>Eliminadas ({items.length})</h2>
        </div>
      }
    >
      <Spin spinning={loading}>
        <List
          dataSource={items}
          locale={{
            emptyText: (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Sem rubricas eliminadas" />
            ),
          }}
          renderItem={(deleted) => (
            <List.Item
              actions={[
                <Button
                  key="recover"
                  size="small"
                  icon={<UndoOutlined />}
                  loading={recoveringId === deleted.id}
                  onClick={() => recover(deleted)}
                >
                  Recuperar
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={
                  <span>
                    {deleted.code && (
                      <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, marginRight: 6 }}>
                        {deleted.code}
                      </span>
                    )}
                    {deleted.name}
                  </span>
                }
                description={
                  <span style={{ fontSize: 12, opacity: 0.7 }}>
                    {ROW_KIND_LABEL[deleted.rowKind]} · eliminada em {dayjs(deleted.deletedAt).format("DD/MM/YYYY")}
                    {" · "}
                    {daysRemaining(deleted.purgeAt) > 0
                      ? `restam ${daysRemaining(deleted.purgeAt)} dia(s)`
                      : "purga hoje"}
                  </span>
                }
              />
            </List.Item>
          )}
        />
      </Spin>
    </Drawer>
  );
};
