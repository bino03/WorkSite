import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Button, Space, Popconfirm, Empty, Typography, Breadcrumb, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Layers, Plus, Pencil, Trash2, ChevronRight, ArrowLeft } from "lucide-react";
import { D, actionButtonBaseStyle } from "@/config/entityColors";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency } from "@/utils/formatters";
import { getStageById } from "@/services/constructionService";
import { listSubStagesByStage, deleteSubStage, listExpensesBySubStage } from "@/services/constructionService";
import type { ConstructionSubStageResponseDTO } from "@/types/construction";
import ConstructionSubStageUpsertDrawer from "@/components/construction/ConstructionSubStageUpsertDrawer";

const { Title, Text } = Typography;

type SubStageRow = ConstructionSubStageResponseDTO & { total: number };

export default function ConstructionSubStagesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { enterpriseId = "", stageId = "" } = useParams<{ enterpriseId: string; stageId: string }>();
  const { isAdmin } = useAuth();

  const [stageName, setStageName] = useState<string>("");
  const [data, setData] = useState<SubStageRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingSubStageId, setEditingSubStageId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchSubStages = async () => {
    setLoading(true);
    try {
      const subStages = await listSubStagesByStage(stageId);
      const withTotals = await Promise.all(
        subStages.map(async (subStage) => {
          const expenses = await listExpensesBySubStage(subStage.id);
          const total = expenses.reduce((sum, expense) => sum + expense.price, 0);
          return { ...subStage, total };
        })
      );
      setData(withTotals);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!stageId) return;
    getStageById(stageId).then((stage) => setStageName(stage.name));
    fetchSubStages();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stageId]);

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await deleteSubStage(id);
      message.success(t("constructionSubStages.deleted"));
      setData((prev) => prev.filter((s) => s.id !== id));
    } finally {
      setDeletingId(null);
    }
  };

  const columns: ColumnsType<SubStageRow> = useMemo(
    () => [
      {
        title: t("constructionSubStages.columns.name"),
        dataIndex: "name",
        key: "name",
        render: (name: string) => (
          <Text strong style={{ color: D.nearBlack }}>
            {name}
          </Text>
        ),
      },
      {
        title: t("constructionSubStages.columns.description"),
        dataIndex: "description",
        key: "description",
        render: (description: string | null) => description || <Text style={{ color: D.stoneGray }}>—</Text>,
      },
      {
        title: t("constructionSubStages.columns.total"),
        dataIndex: "total",
        key: "total",
        width: 160,
        render: (total: number) => (
          <Text strong style={{ color: D.terracotta }}>
            {formatCurrency(total)}
          </Text>
        ),
      },
      {
        title: t("common.actions"),
        key: "actions",
        width: 180,
        render: (_: unknown, record: SubStageRow) => (
          <Space size={2} direction="vertical" style={{ width: "100%" }}>
            <Button
              type="text"
              icon={<ChevronRight size={14} />}
              onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${stageId}/${record.id}`)}
              style={{
                ...actionButtonBaseStyle,
                color: D.ivory,
                background: D.terracotta,
                border: "none",
                boxShadow: `0px 0px 0px 1px ${D.terracotta}`,
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = D.coral;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.coral}`;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = D.terracotta;
                e.currentTarget.style.boxShadow = `0px 0px 0px 1px ${D.terracotta}`;
              }}
            >
              {t("constructionSubStages.viewExpenses")}
            </Button>

            {isAdmin() && (
              <>
                <Button
                  type="text"
                  icon={<Pencil size={14} />}
                  onClick={() => {
                    setEditingSubStageId(record.id);
                    setDrawerOpen(true);
                  }}
                  style={{
                    ...actionButtonBaseStyle,
                    color: D.charcoalWarm,
                    background: D.warmSand,
                    border: `1px solid ${D.borderWarm}`,
                    boxShadow: "none",
                  }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = D.terracotta;
                    e.currentTarget.style.color = D.terracotta;
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = D.borderWarm;
                    e.currentTarget.style.color = D.charcoalWarm;
                  }}
                >
                  {t("common.edit")}
                </Button>

                <Popconfirm
                  title={t("constructionSubStages.deleteConfirmTitle")}
                  description={t("common.cannotBeUndone")}
                  onConfirm={() => handleDelete(record.id)}
                  okButtonProps={{ danger: true }}
                >
                  <Button
                    type="text"
                    danger
                    icon={<Trash2 size={14} />}
                    loading={deletingId === record.id}
                    style={{
                      ...actionButtonBaseStyle,
                      color: "#b53333",
                      background: D.warmSand,
                      border: `1px solid ${D.borderWarm}`,
                      boxShadow: "none",
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = "#b53333";
                      e.currentTarget.style.background = "#fff5f5";
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = D.borderWarm;
                      e.currentTarget.style.background = D.warmSand;
                    }}
                  >
                    {t("common.delete")}
                  </Button>
                </Popconfirm>
              </>
            )}
          </Space>
        ),
      },
    ],
    [t, navigate, enterpriseId, stageId, isAdmin, deletingId]
  );

  return (
    <div style={{ padding: 24, minHeight: "100vh" }}>
      <Breadcrumb
        style={{ marginBottom: 12 }}
        items={[
          { title: <a onClick={() => navigate("/backoffice/empreendimentos")}>{t("enterprises.title")}</a> },
          { title: <a onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction`)}>{t("constructionStages.title")}</a> },
          { title: stageName || "…" },
        ]}
      />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <Button
            type="text"
            icon={<ArrowLeft size={16} />}
            onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction`)}
            style={{ paddingLeft: 0, marginBottom: 4, color: D.stoneGray }}
          >
            {t("common.back")}
          </Button>
          <Title level={2} style={{ margin: 0, color: D.nearBlack, fontFamily: "Georgia, serif", fontWeight: 500 }}>
            <Layers size={22} style={{ verticalAlign: "-4px", marginRight: 8, color: D.terracotta }} />
            {t("constructionSubStages.title")}
          </Title>
          <Text style={{ color: D.stoneGray, fontSize: 16 }}>
            {stageName ? t("constructionSubStages.subtitle", { stage: stageName }) : ""}
          </Text>
        </div>

        {isAdmin() && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            size="large"
            onClick={() => {
              setEditingSubStageId(null);
              setDrawerOpen(true);
            }}
          >
            {t("constructionSubStages.addSubStage")}
          </Button>
        )}
      </div>

      <Table<SubStageRow>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={false}
        onRow={(record) => ({
          onClick: () => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${stageId}/${record.id}`),
          style: { cursor: "pointer" },
        })}
        locale={{ emptyText: <Empty description={t("constructionSubStages.noSubStages")} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
      />

      <ConstructionSubStageUpsertDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        stageId={stageId}
        subStageId={editingSubStageId}
        onSaved={(subStage) => {
          setData((prev) => {
            const existing = prev.find((s) => s.id === subStage.id);
            const total = existing?.total ?? 0;
            const row = { ...subStage, total };
            const exists = prev.some((s) => s.id === subStage.id);
            return exists ? prev.map((s) => (s.id === subStage.id ? row : s)) : [...prev, row];
          });
        }}
      />
    </div>
  );
}
