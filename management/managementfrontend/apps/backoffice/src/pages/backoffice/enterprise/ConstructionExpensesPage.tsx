import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Table, Button, Space, Tooltip, Popconfirm, Empty, Typography, Breadcrumb, message, Card } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Receipt, Plus, Pencil, Trash2, FileText, ArrowLeft } from "lucide-react";
import { D, actionButtonBaseStyle } from "@/config/entityColors";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency } from "@/utils/formatters";
import { getSubStageById, listExpensesBySubStage, deleteExpense } from "@/services/constructionService";
import type { ConstructionExpenseResponseDTO } from "@/types/construction";
import ConstructionExpenseUpsertDrawer from "@/components/construction/ConstructionExpenseUpsertDrawer";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";

const { Title, Text } = Typography;

export default function ConstructionExpensesPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { enterpriseId = "", stageId = "", subStageId = "" } = useParams<{
    enterpriseId: string;
    stageId: string;
    subStageId: string;
  }>();
  const { isAdmin } = useAuth();

  const [subStageName, setSubStageName] = useState<string>("");
  const [data, setData] = useState<ConstructionExpenseResponseDTO[]>([]);
  const [loading, setLoading] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeExpenseId, setActiveExpenseId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [previewExpense, setPreviewExpense] = useState<ConstructionExpenseResponseDTO | null>(null);

  const fetchExpenses = async () => {
    setLoading(true);
    try {
      const expenses = await listExpensesBySubStage(subStageId);
      setData(expenses);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!subStageId) return;
    getSubStageById(subStageId).then((subStage) => setSubStageName(subStage.name));
    fetchExpenses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subStageId]);

  const total = useMemo(() => data.reduce((sum, expense) => sum + expense.price, 0), [data]);

  const handleDelete = async (id: string) => {
    setDeletingId(id);
    try {
      await deleteExpense(id);
      message.success(t("constructionExpenses.deleted"));
      setData((prev) => prev.filter((e) => e.id !== id));
    } finally {
      setDeletingId(null);
    }
  };

  const columns: ColumnsType<ConstructionExpenseResponseDTO> = useMemo(
    () => [
      {
        title: t("constructionExpenses.columns.name"),
        dataIndex: "name",
        key: "name",
        render: (name: string) => (
          <Text strong style={{ color: D.nearBlack }}>
            {name}
          </Text>
        ),
      },
      {
        title: t("constructionExpenses.columns.price"),
        dataIndex: "price",
        key: "price",
        width: 160,
        render: (price: number) => (
          <Text strong style={{ color: D.terracotta }}>
            {formatCurrency(price)}
          </Text>
        ),
      },
      {
        title: t("constructionExpenses.columns.invoice"),
        key: "invoice",
        width: 120,
        render: (_: unknown, record: ConstructionExpenseResponseDTO) =>
          record.invoiceUrl ? (
            <Tooltip title={t("constructionExpenses.viewInvoice")}>
              <Button type="text" icon={<FileText size={16} />} onClick={() => setPreviewExpense(record)} />
            </Tooltip>
          ) : (
            <Text style={{ color: D.stoneGray }}>—</Text>
          ),
      },
      {
        title: t("common.actions"),
        key: "actions",
        width: 180,
        render: (_: unknown, record: ConstructionExpenseResponseDTO) =>
          isAdmin() ? (
            <Space size={2} direction="vertical" style={{ width: "100%" }}>
              <Button
                type="text"
                icon={<Pencil size={14} />}
                onClick={() => {
                  setActiveExpenseId(record.id);
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
                title={t("constructionExpenses.deleteConfirmTitle")}
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
            </Space>
          ) : null,
      },
    ],
    [t, isAdmin, deletingId]
  );

  return (
    <div style={{ padding: 24, minHeight: "100vh" }}>
      <Breadcrumb
        style={{ marginBottom: 12 }}
        items={[
          { title: <a onClick={() => navigate("/backoffice/empreendimentos")}>{t("enterprises.title")}</a> },
          { title: <a onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction`)}>{t("constructionStages.title")}</a> },
          { title: <a onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${stageId}`)}>{t("constructionSubStages.title")}</a> },
          { title: subStageName || "…" },
        ]}
      />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 20, flexWrap: "wrap", gap: 12 }}>
        <div>
          <Button
            type="text"
            icon={<ArrowLeft size={16} />}
            onClick={() => navigate(`/backoffice/empreendimentos/${enterpriseId}/construction/${stageId}`)}
            style={{ paddingLeft: 0, marginBottom: 4, color: D.stoneGray }}
          >
            {t("common.back")}
          </Button>
          <Title level={2} style={{ margin: 0, color: D.nearBlack, fontFamily: "Georgia, serif", fontWeight: 500 }}>
            <Receipt size={22} style={{ verticalAlign: "-4px", marginRight: 8, color: D.terracotta }} />
            {t("constructionExpenses.title")}
          </Title>
          <Text style={{ color: D.stoneGray, fontSize: 16 }}>
            {subStageName ? t("constructionExpenses.subtitle", { subStage: subStageName }) : ""}
          </Text>
        </div>

        {isAdmin() && (
          <Button
            type="primary"
            icon={<Plus size={16} />}
            size="large"
            onClick={() => {
              setActiveExpenseId(null);
              setDrawerOpen(true);
            }}
          >
            {t("constructionExpenses.addExpense")}
          </Button>
        )}
      </div>

      <Card
        style={{ marginBottom: 16, borderRadius: 12, border: `1px solid ${D.borderCream}`, backgroundColor: D.ivory, boxShadow: D.whisper }}
        bodyStyle={{ padding: 16, display: "flex", justifyContent: "space-between", alignItems: "center" }}
      >
        <Text style={{ color: D.stoneGray }}>{t("constructionExpenses.totalSpent")}</Text>
        <Text strong style={{ fontSize: 20, color: D.terracotta }}>
          {formatCurrency(total)}
        </Text>
      </Card>

      <Table<ConstructionExpenseResponseDTO>
        rowKey="id"
        columns={columns}
        dataSource={data}
        loading={loading}
        pagination={false}
        locale={{ emptyText: <Empty description={t("constructionExpenses.noExpenses")} image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
      />

      <ConstructionExpenseUpsertDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        subStageId={subStageId}
        expenseId={activeExpenseId}
        onSaved={(expense) => {
          setData((prev) => {
            const exists = prev.some((e) => e.id === expense.id);
            return exists ? prev.map((e) => (e.id === expense.id ? expense : e)) : [...prev, expense];
          });
        }}
      />

      <InvoicePreviewModal
        open={previewExpense !== null}
        onClose={() => setPreviewExpense(null)}
        invoiceUrl={previewExpense?.invoiceUrl ?? null}
        mimeType={previewExpense?.mimeType ?? null}
        filename={previewExpense?.originalFilename ?? null}
      />
    </div>
  );
}
