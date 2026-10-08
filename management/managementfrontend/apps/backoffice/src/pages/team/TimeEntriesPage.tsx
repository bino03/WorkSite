import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, DatePicker, Empty, Input, Segmented, Spin, Table, Tooltip } from "antd";
import { LeftOutlined, RightOutlined, SearchOutlined, WarningOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { getMonthReport } from "@/services/attendanceService";
import { DAY_STATUS_LABEL } from "@/types/attendance";
import type { AttendanceMonthReport, DayStatus } from "@/types/attendance";
import { formatLongDate, formatMinutes, formatMonth } from "@/utils/formatters";
import { DayTimeEntriesDrawer } from "@/components/attendance/timeentries/DayTimeEntriesDrawer";

/** Uma linha é um funcionário num dia — é o que se corrige de cada vez. */
interface Row {
  key: string;
  profileId: string;
  profileName: string;
  date: string;
  status: DayStatus;
  firstIn: string | null;
  lastOut: string | null;
  workedMinutes: number;
  needsAttention: boolean;
  enterprises: string;
}

const STATUS_CLASS: Record<DayStatus, string> = {
  WORKED: "ind-tag-accent",
  MISSING: "ind-tag-outline",
  NOT_SCHEDULED: "ind-tag-neutral",
  NO_SCHEDULE: "ind-tag-outline",
  HOLIDAY: "ind-tag-neutral",
  ON_LEAVE: "ind-tag-neutral",
};

type Scope = "day" | "month";

/**
 * As picagens de **toda a equipa**, por mês ou por um dia dentro dele.
 *
 * Não há endpoint de picagens da equipa — `/attendance/time-entries` é sempre por
 * funcionário. O que existe é `/attendance/reports/month`, que devolve todos os
 * funcionários do mês com os seus dias já calculados; esta página achata isso em
 * linhas (funcionário × dia) e vai buscar as picagens em cru só quando se abre um
 * dia, pelo `DayTimeEntriesDrawer`.
 */
export default function TimeEntriesPage() {
  const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
  const [scope, setScope] = useState<Scope>("day");
  const [day, setDay] = useState(() => dayjs().format("YYYY-MM-DD"));
  const [query, setQuery] = useState("");
  const [report, setReport] = useState<AttendanceMonthReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [openDay, setOpenDay] = useState<Row | null>(null);

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

  // Mudar de mês arrasta o dia escolhido para dentro dele, senão a vista "dia"
  // ficaria a apontar para fora do relatório carregado e apareceria vazia.
  const goToMonth = (next: string) => {
    setMonth(next);
    setDay((current) => (current.startsWith(next) ? current : `${next}-01`));
  };

  const rows = useMemo<Row[]>(() => {
    if (!report) return [];
    const wanted = query.trim().toLowerCase();
    return report.employees
      .flatMap((employee) =>
        employee.days
          .filter((entry) => scope === "month" || entry.date === day)
          .map<Row>((entry) => ({
            key: `${employee.profileId}-${entry.date}`,
            profileId: employee.profileId,
            profileName: employee.profileName ?? "—",
            date: entry.date,
            status: entry.status,
            firstIn: entry.firstIn,
            lastOut: entry.lastOut,
            workedMinutes: entry.workedMinutes,
            needsAttention: entry.needsAttention,
            enterprises: entry.enterprises.map((at) => at.enterpriseName ?? "sem obra").join(", "),
          }))
      )
      .filter((row) => !wanted || row.profileName.toLowerCase().includes(wanted))
      .sort((a, b) => a.date.localeCompare(b.date) || a.profileName.localeCompare(b.profileName));
  }, [report, scope, day, query]);

  const columns: ColumnsType<Row> = [
    {
      title: "Funcionário",
      dataIndex: "profileName",
      key: "profileName",
      render: (name: string, row) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
          {name}
          {row.needsAttention && (
            <Tooltip title="Falta por justificar ou dia mal preenchido">
              <WarningOutlined style={{ marginLeft: 6, color: "var(--ind-color-accent)" }} />
            </Tooltip>
          )}
        </span>
      ),
    },
    {
      title: "Dia",
      dataIndex: "date",
      key: "date",
      width: 110,
      render: (date: string) => dayjs(date).format("DD/MM"),
    },
    {
      title: "Estado",
      dataIndex: "status",
      key: "status",
      width: 130,
      render: (status: DayStatus) => <span className={`ind-tag ${STATUS_CLASS[status]}`}>{DAY_STATUS_LABEL[status]}</span>,
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
    { title: "Obras", dataIndex: "enterprises", key: "enterprises", render: (value: string) => value || "—" },
  ];

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Equipa</h6>
          <h1 style={{ margin: 0 }}>Picagens</h1>
          <p className="ind-card-meta" style={{ marginTop: 4, marginBottom: 0 }}>
            {scope === "day" ? formatLongDate(day, true) : formatMonth(month)}
          </p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Button
            size="small"
            icon={<LeftOutlined />}
            aria-label="Mês anterior"
            onClick={() => goToMonth(dayjs(`${month}-01`).subtract(1, "month").format("YYYY-MM"))}
          />
          <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, minWidth: 120, textAlign: "center" }}>
            {formatMonth(month)}
          </span>
          <Button
            size="small"
            icon={<RightOutlined />}
            aria-label="Mês seguinte"
            onClick={() => goToMonth(dayjs(`${month}-01`).add(1, "month").format("YYYY-MM"))}
          />
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: "13.6px", flexWrap: "wrap" }}>
        <Segmented
          value={scope}
          onChange={(value) => setScope(value as Scope)}
          options={[
            { label: "Um dia", value: "day" },
            { label: "O mês todo", value: "month" },
          ]}
        />
        {scope === "day" && (
          <DatePicker
            format="DD/MM/YYYY"
            allowClear={false}
            value={dayjs(day)}
            disabledDate={(date) => !date.format("YYYY-MM").startsWith(month)}
            onChange={(date) => date && setDay(date.format("YYYY-MM-DD"))}
          />
        )}
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Procurar funcionário"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          style={{ maxWidth: 320 }}
          allowClear
        />
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table
            rowKey="key"
            columns={columns}
            dataSource={rows}
            pagination={false}
            size="small"
            onRow={(row) => ({ onClick: () => setOpenDay(row), style: { cursor: "pointer" } })}
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="Nenhum funcionário com dias neste período. Só aparecem aqui os que têm ficha de emprego."
                />
              ),
            }}
          />
        </div>
      )}

      <div style={{ marginTop: 16 }}>
        <p style={{ fontSize: 12, opacity: 0.6, margin: 0 }}>{rows.length} linha(s)</p>
      </div>

      {openDay && (
        <DayTimeEntriesDrawer
          open
          profileId={openDay.profileId}
          profileName={openDay.profileName}
          day={openDay.date}
          onClose={() => setOpenDay(null)}
          onChanged={() => void fetchReport()}
        />
      )}
    </div>
  );
}
