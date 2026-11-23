import { useCallback, useEffect, useState } from "react";
import { Button, Empty, Skeleton } from "antd";
import { LeftOutlined, RightOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import BlueprintCard from "@/components/common/BlueprintCard";
import { ErrorHandler } from "@/errors/errorHandler";
import { myMonthSummary } from "@/services/myAttendanceService";
import { ABSENCE_TYPE_LABEL, DAY_STATUS_LABEL } from "@/types/attendance";
import type { AttendanceSummary, DayAttendance, DayStatus } from "@/types/attendance";
import { formatLongDate, formatMinutes, formatMonth } from "@/utils/formatters";

const STATUS_CLASS: Record<DayStatus, string> = {
  WORKED: "ind-tag-accent",
  MISSING: "ind-tag-outline",
  UPCOMING: "ind-tag-neutral",
  NOT_SCHEDULED: "ind-tag-neutral",
  NO_SCHEDULE: "ind-tag-outline",
  HOLIDAY: "ind-tag-neutral",
  ON_LEAVE: "ind-tag-neutral",
};

/** Folgas e dias por acontecer não dizem nada ao funcionário — a lista mostra só os outros. */
const HIDDEN: DayStatus[] = ["NOT_SCHEDULED", "UPCOMING"];

const statusLabel = (day: DayAttendance) => {
  if (day.status === "HOLIDAY" && day.holidayName) return day.holidayName;
  if (day.status === "ON_LEAVE" && day.absenceType) return ABSENCE_TYPE_LABEL[day.absenceType];
  return DAY_STATUS_LABEL[day.status];
};

/**
 * "As minhas horas" — o mês do próprio, com o mesmo cálculo que o escritório vê em
 * Equipa → Picagens (`/attendance/me/summary/month`). Só leitura: uma picagem errada
 * corrige-se no escritório.
 */
export function MyHoursPanel() {
  const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
  const [summary, setSummary] = useState<AttendanceSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (target: string) => {
    setLoading(true);
    try {
      setSummary(await myMonthSummary(target));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(month);
  }, [month, load]);

  const shift = (delta: number) => setMonth(dayjs(`${month}-01`).add(delta, "month").format("YYYY-MM"));
  const days = (summary?.days ?? []).filter((day) => !HIDDEN.includes(day.status)).reverse();

  const stat = (kicker: string, value: string, meta: string) => (
    <BlueprintCard style={{ padding: "13.6px" }}>
      <span className="ind-card-kicker">{kicker}</span>
      <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 28, lineHeight: 1.1 }}>{value}</div>
      <span className="ind-card-meta">{meta}</span>
    </BlueprintCard>
  );

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <Button size="small" icon={<LeftOutlined />} aria-label="Mês anterior" onClick={() => shift(-1)} />
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, minWidth: 130, textAlign: "center" }}>
          {formatMonth(month)}
        </span>
        <Button
          size="small"
          icon={<RightOutlined />}
          aria-label="Mês seguinte"
          disabled={month >= dayjs().format("YYYY-MM")}
          onClick={() => shift(1)}
        />
      </div>

      {loading && !summary ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : summary ? (
        <>
          <div
            className="keep-cols"
            style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: "13.6px" }}
          >
            {stat("Trabalhadas", formatMinutes(summary.workedMinutes), `de ${formatMinutes(summary.expectedMinutes)} previstas`)}
            {stat("Extra", formatMinutes(summary.overtimeMinutes), "acima do horário")}
            {stat("Atrasos", formatMinutes(summary.latenessMinutes), "somados no mês")}
            {stat("Faltas", String(summary.daysMissing), "dias por justificar")}
          </div>

          <BlueprintCard kicker="Dia a dia" style={{ padding: "13.6px" }}>
            {days.length === 0 ? (
              <Empty description="Sem dias de trabalho neste mês" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <ul style={{ listStyle: "none", margin: 0, padding: 0 }}>
                {days.map((day) => (
                  <li
                    key={day.date}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: "10.2px",
                      padding: "10.2px 0",
                      borderBottom: "1px solid var(--ind-color-divider)",
                    }}
                  >
                    <div style={{ minWidth: 0 }}>
                      <strong>{formatLongDate(day.date)}</strong>
                      <div className="ind-card-meta">
                        {day.firstIn ? `${day.firstIn} – ${day.lastOut ?? "…"}` : "Sem picagens"}
                        {day.enterprises.length > 0 &&
                          ` · ${day.enterprises.map((at) => at.enterpriseName ?? "sem obra").join(", ")}`}
                        {day.incomplete && " · por fechar"}
                      </div>
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 8, flexShrink: 0 }}>
                      {day.workedMinutes > 0 && (
                        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
                          {formatMinutes(day.workedMinutes)}
                        </span>
                      )}
                      <span className={`ind-tag ${STATUS_CLASS[day.status]}`}>{statusLabel(day)}</span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            <p className="ind-card-meta" style={{ margin: "10.2px 0 0" }}>
              Uma picagem em falta ou errada corrige-se no escritório.
            </p>
          </BlueprintCard>
        </>
      ) : null}
    </div>
  );
}
