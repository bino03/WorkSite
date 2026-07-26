import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Button, Space, Popconfirm, Empty, Typography, Breadcrumb, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Layers, Plus, Pencil, Trash2, ChevronRight, ArrowLeft } from "lucide-react";
import { D, actionButtonBaseStyle } from "@/config/entityColors";
import { useAuth } from "@/hooks/useAuth";
import { getEnterpriseById } from "@/services/enterpriseService";
import { listStagesByEnterprise, deleteStage } from "@/services/constructionService";
import type { ConstructionStageResponseDTO } from "@/types/construction";
import ConstructionStageUpsertDrawer from "@/components/construction/ConstructionStageUpsertDrawer";

const { Title, Text } = Typography;

export default function ConstructionStagesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { enterpriseId = "" } = useParams<{ enterpriseId: string }>();
  const { isAdmin } = useAuth();

  const [enterpriseName, setEnterpriseName] = useState<string>("");
  const [data, setData] = useState<ConstructionStageResponseDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingStageId, setEditingStageId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchStages = async () => {
    setLoading(true);
    try {
      const stages = await listStagesByEnterprise(enterpriseId);
      setData(stages);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!enterpriseId) return;
    getEnterpriseById(enterpriseId).then((enterprise) => setEnterpriseName(enterprise.name));
    fetchStages();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enterpriseId]);

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await deleteStage(id);
      message.success(t("constructionStages.deleted"));
      setData((prev) => prev.filter((s) => s.id !== id));
    } finally {
      setDeletingId(null);
    }
  };

  const columns: ColumnsType<ConstructionStageResponseDTO> = useMemo(
    () => [
      {
        title: t("constructionStages.columns.name"),
        dataIndex: "name",
        key: "name",
        render: (name: string) => (
          <Text strong style={{ color: D.nearBlack }}>
            {name}
          </Text>
        ),
      },
      {
        title: t("constructionStages.columns.description"),
        dataIndex: "description",
        key: "description",
        render: (description: string | null) => description || <Text style={{ color: D.stoneGray }}>—</Text>,
      },
      {
        title: t("common.actions"),
        key: "actions",
        width: 180,
        render: (_: unknown, record: ConstructionStageResponseDTO) => (
          <Space size={2} direction="vertical" style={{ width: "100%" }}>
            <Button
              type="text"
              icon={<ChevronRight size={14} />}
              onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${record.id}`)}
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
              {t("constructionStages.viewSubStages")}
            </Button>

            {isAdmin() && (
              <>
                <Button
                  type="text"
                  icon={<Pencil size={14} />}
                  onClick={() => {
                    setEditingStageId(record.id);
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
                  title={t("constructionStages.deleteConfirmTitle")}
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
    [t, navigate, enterpriseId, isAdmin, deletingId]
  );

  return (
    <div style={{ padding: 24, minHeight: "100vh" }}>
      <Breadcrumb
        style={{ marginBottom: 12 }}
        items={[
          { title: <a onClick={() => navigate("/backoffice/empreendimentos")}>{t("enterprises.title")}</a> },
          { title: enterpriseName || "…" },
          { title: t("constructionStages.title") },
        ]}
      />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <Button
            type="text"
            icon={<ArrowLeft size={16} />}
            onClick={() => navigate("/backoffice/empreendimentos")}
            style={{ paddingLeft: 0, marginBottom: 4, color: D.stoneGray }}
          >
            {t("common.back")}
          </Button>
          <Title level={2} style={{ margin: 0, color: D.nearBlack, fontFamily: "Georgia, serif", fontWeight: 500 }}>
            <Layers size={22} style={{ verticalAlign: "-4px", marginRight: 8, color: D.terracotta }} />
            {t("constructionStages.title")}
          </Title>
          <Text style={{ color: D.stoneGray, fontSize: 16 }}>
            {enterpriseName ? t("constructionStages.subtitle", { enterprise: enterpriseName }) : ""}
          </Text>
        </div>

        {isAdmin() && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            size="large"
            onClick={() => {
              setEditingStageId(null);
              setDrawerOpen(true);
            }}
          >
            {t("constructionStages.addStage")}
          </Button>
        )}
      </div>

      <Table<ConstructionStageResponseDTO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={false}
        onRow={(record) => ({
          onClick: () => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${record.id}`),
          style: { cursor: "pointer" },
        })}
        locale={{ emptyText: <Empty description={t("constructionStages.noStages")} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
      />

      <ConstructionStageUpsertDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        enterpriseId={enterpriseId}
        stageId={editingStageId}
        onSaved={(stage) => {
          setData((prev) => {
            const exists = prev.some((s) => s.id === stage.id);
            return exists ? prev.map((s) => (s.id === stage.id ? stage : s)) : [...prev, stage];
          });
        }}
      />
    </div>
  );
}
