import { useCallback, useEffect, useState } from "react";
import { Button, Empty, Spin, Table, Tooltip } from "antd";
import { LeftOutlined, RightOutlined, WarningOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { parseApiError } from "@/utils/apiError";
import { getMonthSummary } from "@/services/attendanceService";
import { DAY_STATUS_LABEL } from "@/types/attendance";
import type { AttendanceSummary, DayAttendance, DayStatus } from "@/types/attendance";
import { formatMinutes, formatMonth } from "@/utils/formatters";
import { DayTimeEntriesDrawer } from "./DayTimeEntriesDrawer";

interface Props {
  profileId: string;
}

const STATUS_CLASS: Record<DayStatus, string> = {
  WORKED: "ind-tag-accent",
  MISSING: "ind-tag-outline",
  NOT_SCHEDULED: "ind-tag-neutral",
  NO_SCHEDULE: "ind-tag-outline",
  HOLIDAY: "ind-tag-neutral",
  ON_LEAVE: "ind-tag-neutral",
};

/**
 * O mês de assiduidade de um funcionário, na sua página: um dia por linha, com
 * entrada, saída, horas e extras, e o drill-down para as picagens em cru.
 *
 * Os números são todos **derivados** (nada está guardado), por isso o mês passado
 * muda se as picagens mudarem — é o que torna corrigir aqui útil.
 */
export function AttendanceMonthCard({ profileId }: Props) {
  const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
  const [summary, setSummary] = useState<AttendanceSummary | null>(null);
  const [loading, setLoading] = useState(true);
  /** `null` quando o funcionário ainda não tem ficha de emprego (`ATT_001`/`ATT_005`). */
  const [noEmployment, setNoEmployment] = useState(false);
  const [openDay, setOpenDay] = useState<string | null>(null);

  const fetchSummary = useCallback(async () => {
    setLoading(true);
    try {
      setSummary(await getMonthSummary(profileId, month));
      setNoEmployment(false);
    } catch (error) {
      const code = parseApiError(error)?.errorCode;
      if (code === "ATT_001" || code === "ATT_005") {
        setSummary(null);
        setNoEmployment(true);
      } else {
        ErrorHandler.handle(error);
      }
    } finally {
      setLoading(false);
    }
  }, [profileId, month]);

  useEffect(() => {
    void fetchSummary();
  }, [fetchSummary]);

  const columns: ColumnsType<DayAttendance> = [
    {
      title: "Dia",
      dataIndex: "date",
      key: "date",
      width: 110,
      render: (date: string, day) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
          {dayjs(date).format("DD/MM")}
          {day.needsAttention && (
            <Tooltip title="Falta por justificar ou dia mal preenchido">
              <WarningOutlined style={{ marginLeft: 6, color: "var(--ind-color-accent)" }} />
            </Tooltip>
          )}
        </span>
      ),
    },
    {
      title: "Estado",
      dataIndex: "status",
      key: "status",
      width: 130,
      render: (status: DayStatus, day) => (
        <Tooltip title={day.holidayName ?? undefined}>
          <span className={`ind-tag ${STATUS_CLASS[status]}`}>{DAY_STATUS_LABEL[status]}</span>
        </Tooltip>
      ),
    },
    { title: "Entrada", dataIndex: "firstIn", key: "firstIn", width: 90, render: (time: string | null) => time ?? "—" },
    { title: "Saída", dataIndex: "lastOut", key: "lastOut", width: 90, render: (time: string | null) => time ?? "—" },
    {
      title: "Horas",
      dataIndex: "workedMinutes",
      key: "workedMinutes",
      width: 90,
      render: (minutes: number) => formatMinutes(minutes),
    },
    {
      title: "Extra",
      dataIndex: "overtimeMinutes",
      key: "overtimeMinutes",
      width: 90,
      render: (minutes: number) => (minutes ? formatMinutes(minutes) : "—"),
    },
    {
      title: "Obras",
      key: "enterprises",
      render: (_, day) =>
        day.enterprises.length === 0
          ? "—"
          : day.enterprises.map((at) => at.enterpriseName ?? "sem obra").join(", "),
    },
  ];

  return (
    <div className="ind-card" style={{ marginTop: "20.4px", padding: "20.4px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "13.6px" }}>
        <div>
          <span className="ind-card-kicker">Assiduidade</span>
          <h2 style={{ margin: 0 }}>{formatMonth(month)}</h2>
          {summary && (
            <p className="ind-card-meta" style={{ marginTop: 4, marginBottom: 0 }}>
              {formatMinutes(summary.workedMinutes)} de {formatMinutes(summary.expectedMinutes)} ·{" "}
              {summary.daysWorked} dia(s) · {summary.daysMissing} falta(s) · {summary.daysIncomplete} por fechar
              {summary.scheduleChanges > 1 && " · o horário mudou dentro do mês"}
            </p>
          )}
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Button
            size="small"
            icon={<LeftOutlined />}
            aria-label="Mês anterior"
            onClick={() => setMonth(dayjs(`${month}-01`).subtract(1, "month").format("YYYY-MM"))}
          />
          <Button
            size="small"
            icon={<RightOutlined />}
            aria-label="Mês seguinte"
            onClick={() => setMonth(dayjs(`${month}-01`).add(1, "month").format("YYYY-MM"))}
          />
        </div>
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : noEmployment ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Sem ficha de emprego ou sem horário atribuído — não há horas esperadas para comparar."
        />
      ) : (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table
            rowKey="date"
            columns={columns}
            dataSource={summary?.days ?? []}
            pagination={false}
            size="small"
            onRow={(day) => ({ onClick: () => setOpenDay(day.date), style: { cursor: "pointer" } })}
          />
        </div>
      )}

      {openDay && (
        <DayTimeEntriesDrawer
          open
          profileId={profileId}
          profileName={summary?.profileName ?? "Funcionário"}
          day={openDay}
          onClose={() => setOpenDay(null)}
          onChanged={() => void fetchSummary()}
        />
      )}
    </div>
  );
}
