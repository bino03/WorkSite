import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Alert, Button, Empty, Segmented, Spin, Table, Tooltip } from "antd";
import { DownloadOutlined, LeftOutlined, RightOutlined, WarningOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { exportMonthReport, getMonthReport } from "@/services/attendanceService";
import type { AttendanceMonthReport, AttendanceSummary, EnterpriseMonth } from "@/types/attendance";
import { downloadBlob } from "@/utils/downloadBlob";
import { formatMinutes, formatMonth } from "@/utils/formatters";

type View = "employees" | "enterprises";

/**
 * O fecho do mês: o mesmo tempo visto **por funcionário** e **por obra**. Os dois
 * lados vêm do mesmo pedido (`/attendance/reports/month`) e as horas por obra
 * somam as horas por pessoa — a pausa de um dia repartido entre duas obras é
 * dividida em proporção, não descontada duas vezes.
 *
 * O `.xlsx` é o mesmo mês para auditoria, gerado pelo backend (5 folhas, com
 * picagens anuladas e correções); aqui só se pede e se entrega ao browser.
 */
export default function ReportsPage() {
  const navigate = useNavigate();
  const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
  const [view, setView] = useState<View>("employees");
  const [report, setReport] = useState<AttendanceMonthReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  const fetchReport = useCallback(async () => {
    setLoading(true);
    try {
      setReport(await getMonthReport(month));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void fetchReport();
  }, [fetchReport]);

  const exportXlsx = async () => {
    setExporting(true);
    try {
      const file = await exportMonthReport(month);
      downloadBlob(file.blob, file.fileName);
      notificationService.success("Relatório exportado", file.fileName);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setExporting(false);
    }
  };

  const totals = useMemo(() => {
    const employees = report?.employees ?? [];
    return {
      worked: employees.reduce((sum, employee) => sum + employee.workedMinutes, 0),
      expected: employees.reduce((sum, employee) => sum + employee.expectedMinutes, 0),
      overtime: employees.reduce((sum, employee) => sum + employee.overtimeMinutes, 0),
      missing: employees.reduce((sum, employee) => sum + employee.daysMissing, 0),
      incomplete: employees.reduce((sum, employee) => sum + employee.daysIncomplete, 0),
    };
  }, [report]);

  const employeeColumns: ColumnsType<AttendanceSummary> = [
    {
      title: "Funcionário",
      dataIndex: "profileName",
      key: "profileName",
      render: (name: string | null, employee) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
          {name ?? "—"}
          {employee.scheduleChanges > 1 && (
            <Tooltip title="O horário mudou dentro do mês: os totais atravessam uma mudança de condições">
              <WarningOutlined style={{ marginLeft: 6, color: "var(--ind-color-accent)" }} />
            </Tooltip>
          )}
        </span>
      ),
    },
    {
      title: "Trabalhadas",
      dataIndex: "workedMinutes",
      key: "workedMinutes",
      width: 120,
      render: (minutes: number) => <strong>{formatMinutes(minutes)}</strong>,
    },
    {
      title: "Esperadas",
      dataIndex: "expectedMinutes",
      key: "expectedMinutes",
      width: 110,
      render: (minutes: number) => formatMinutes(minutes),
    },
    {
      title: "Extra",
      dataIndex: "overtimeMinutes",
      key: "overtimeMinutes",
      width: 100,
      render: (minutes: number) => (minutes ? formatMinutes(minutes) : "—"),
    },
    {
      title: "Atrasos",
      dataIndex: "latenessMinutes",
      key: "latenessMinutes",
      width: 100,
      render: (minutes: number) => (minutes ? formatMinutes(minutes) : "—"),
    },
    { title: "Dias", dataIndex: "daysWorked", key: "daysWorked", width: 80 },
    { title: "Faltas", dataIndex: "daysMissing", key: "daysMissing", width: 80 },
    { title: "Ausências", dataIndex: "daysOnLeave", key: "daysOnLeave", width: 100 },
    { title: "Feriados", dataIndex: "daysHoliday", key: "daysHoliday", width: 90 },
    {
      title: "Por fechar",
      dataIndex: "daysIncomplete",
      key: "daysIncomplete",
      width: 110,
      render: (days: number) => (days ? <span className="ind-tag ind-tag-outline">{days}</span> : "—"),
    },
  ];

  const enterpriseColumns: ColumnsType<EnterpriseMonth> = [
    {
      title: "Obra",
      dataIndex: "enterpriseName",
      key: "enterpriseName",
      render: (name: string | null) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, opacity: name ? 1 : 0.6 }}>
          {name ?? "sem obra"}
        </span>
      ),
    },
    {
      title: "Horas",
      dataIndex: "workedMinutes",
      key: "workedMinutes",
      width: 140,
      render: (minutes: number) => <strong>{formatMinutes(minutes)}</strong>,
    },
    {
      title: "Funcionários",
      key: "employees",
      width: 140,
      render: (_, enterprise) => enterprise.employees.length,
    },
  ];

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Equipa</h6>
          <h1 style={{ margin: 0 }}>Relatórios</h1>
          <p className="ind-card-meta" style={{ marginTop: 4, marginBottom: 0 }}>
            {formatMonth(month)} · {formatMinutes(totals.worked)} de {formatMinutes(totals.expected)}
            {totals.overtime > 0 && ` · ${formatMinutes(totals.overtime)} extra`}
          </p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Button
            size="small"
            icon={<LeftOutlined />}
            aria-label="Mês anterior"
            onClick={() => setMonth(dayjs(`${month}-01`).subtract(1, "month").format("YYYY-MM"))}
          />
          <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, minWidth: 120, textAlign: "center" }}>
            {formatMonth(month)}
          </span>
          <Button
            size="small"
            icon={<RightOutlined />}
            aria-label="Mês seguinte"
            onClick={() => setMonth(dayjs(`${month}-01`).add(1, "month").format("YYYY-MM"))}
          />
          <Button type="primary" icon={<DownloadOutlined />} loading={exporting} onClick={exportXlsx}>
            Exportar .xlsx
          </Button>
        </div>
      </div>

      {(totals.missing > 0 || totals.incomplete > 0) && !loading && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: "13.6px" }}
          message="O mês ainda não está limpo"
          description={`${totals.missing} falta(s) por justificar e ${totals.incomplete} dia(s) por fechar. Resolva-os em "Hoje" antes de exportar — o .xlsx sai com os números como estão.`}
        />
      )}

      <div style={{ marginBottom: "13.6px" }}>
        <Segmented
          value={view}
          onChange={(value) => setView(value as View)}
          options={[
            { label: "Por funcionário", value: "employees" },
            { label: "Por obra", value: "enterprises" },
          ]}
        />
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : view === "employees" ? (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table
            rowKey="profileId"
            columns={employeeColumns}
            dataSource={report?.employees ?? []}
            pagination={false}
            size="small"
            onRow={(employee) => ({
              onClick: () => navigate(`/team/employees/${employee.profileId}`),
              style: { cursor: "pointer" },
            })}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Nenhum funcionário com vínculo neste mês."
                />
              ),
            }}
          />
        </div>
      ) : (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table
            rowKey={(enterprise) => enterprise.enterpriseId ?? "none"}
            columns={enterpriseColumns}
            dataSource={report?.enterprises ?? []}
            pagination={false}
            size="small"
            expandable={{
              expandedRowRender: (enterprise) => (
                <div style={{ paddingLeft: 24 }}>
                  {enterprise.employees.map((employee) => (
                    <div
                      key={employee.profileId}
                      style={{ display: "flex", justifyContent: "space-between", maxWidth: 420, padding: "3.4px 0" }}
                    >
                      <span>{employee.profileName ?? "—"}</span>
                      <span className="ind-card-meta" style={{ margin: 0 }}>
                        {formatMinutes(employee.workedMinutes)} · {employee.daysWorked} dia(s)
                      </span>
                    </div>
                  ))}
                </div>
              ),
            }}
            locale={{
              emptyText: (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma obra com horas neste mês." />
              ),
            }}
          />
        </div>
      )}

      <p className="ind-card-meta" style={{ marginTop: 16 }}>
        As horas por obra somam as horas por funcionário: um dia repartido entre duas obras tem a
        pausa dividida em proporção, não descontada duas vezes.
      </p>
    </div>
  );
}
