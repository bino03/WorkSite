import { useCallback, useEffect, useMemo, useState } from "react";
import { Calendar, Empty, Skeleton, Tag } from "antd";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import BlueprintCard from "@/components/common/BlueprintCard";
import { ErrorHandler } from "@/errors/errorHandler";
import { useIsMobile } from "@/hooks/useIsMobile";
import { holidaysBetween, myAbsences, myVacationBalance } from "@/services/myAttendanceService";
import { ABSENCE_STATUS_LABEL, ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AbsenceStatus, Holiday, VacationBalance } from "@/types/attendance";

/**
 * "As minhas férias" — o funcionário (EMPLOYEE) vê o seu saldo de férias do ano, as suas
 * ausências e o calendário com os feriados (fase 5b da assiduidade, 2026-11-20). Só
 * leitura: marcar férias continua a ser pedido ao escritório, que as regista no espaço
 * Equipa.
 */
const STATUS_COLOR: Record<AbsenceStatus, string> = {
  APPROVED: "green",
  PENDING: "gold",
  REJECTED: "default",
};

const ISO = "YYYY-MM-DD";

const formatDays = (days: number) => (Number.isInteger(days) ? String(days) : days.toFixed(1).replace(".", ","));

const formatRange = (absence: Absence) => {
  const start = dayjs(absence.startsOn);
  const end = dayjs(absence.endsOn);
  const half = absence.halfDay === "MORNING" ? " (manhã)" : absence.halfDay === "AFTERNOON" ? " (tarde)" : "";
  return start.isSame(end, "day")
    ? `${start.format("DD/MM/YYYY")}${half}`
    : `${start.format("DD/MM/YYYY")} a ${end.format("DD/MM/YYYY")}`;
};

export default function MyVacationsPage() {
  const isMobile = useIsMobile();
  const [month, setMonth] = useState<Dayjs>(dayjs().startOf("month"));
  const year = month.year();

  const [balance, setBalance] = useState<VacationBalance | null>(null);
  const [absences, setAbsences] = useState<Absence[]>([]);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState(true);

  // O ano inteiro de uma vez: a lista mostra o ano, e mudar de mês não volta a pedir nada.
  const load = useCallback(async (targetYear: number) => {
    setLoading(true);
    const from = dayjs().year(targetYear).startOf("year").format(ISO);
    const to = dayjs().year(targetYear).endOf("year").format(ISO);
    try {
      const [nextBalance, nextAbsences, nextHolidays] = await Promise.all([
        myVacationBalance(targetYear),
        myAbsences(from, to),
        holidaysBetween(from, to),
      ]);
      setBalance(nextBalance);
      setAbsences(nextAbsences);
      setHolidays(nextHolidays);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(year);
  }, [year, load]);

  const holidayByDate = useMemo(() => {
    const map = new Map<string, Holiday>();
    holidays.forEach((holiday) => map.set(holiday.date, holiday));
    return map;
  }, [holidays]);

  const absenceOn = useCallback(
    (day: Dayjs) =>
      absences.find(
        (absence) =>
          absence.status !== "REJECTED" &&
          !day.isBefore(dayjs(absence.startsOn), "day") &&
          !day.isAfter(dayjs(absence.endsOn), "day")
      ),
    [absences]
  );

  const sortedAbsences = useMemo(
    () => [...absences].sort((a, b) => a.startsOn.localeCompare(b.startsOn)),
    [absences]
  );

  const cellRender = (day: Dayjs) => {
    const holiday = holidayByDate.get(day.format(ISO));
    const absence = absenceOn(day);
    if (!holiday && !absence) return null;
    // No telemóvel o calendário é pequeno: um ponto de cor em vez do texto.
    if (isMobile) {
      return (
        <div style={{ display: "flex", justifyContent: "center", gap: 2 }}>
          {holiday && <span style={{ width: 6, height: 6, borderRadius: 3, background: "var(--ind-accent-600)" }} />}
          {absence && (
            <span
              style={{
                width: 6,
                height: 6,
                borderRadius: 3,
                background: absence.status === "APPROVED" ? "var(--ind-color-accent)" : "var(--ind-neutral-500)",
              }}
            />
          )}
        </div>
      );
    }
    return (
      <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
        {holiday && <Tag color="blue" style={{ margin: 0 }}>{holiday.name}</Tag>}
        {absence && (
          <Tag color={STATUS_COLOR[absence.status]} style={{ margin: 0 }}>
            {ABSENCE_TYPE_LABEL[absence.type]}
            {absence.status === "PENDING" ? " (pendente)" : ""}
          </Tag>
        )}
      </div>
    );
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "20.4px" }}>
      <div>
        <h6 style={{ color: "var(--ind-accent-700)" }}>Assiduidade</h6>
        <h1 style={{ margin: 0 }}>As minhas férias</h1>
        <p className="ind-card-meta" style={{ margin: "4px 0 0" }}>
          Para marcar férias, fale com o escritório — o pedido aparece aqui como pendente.
        </p>
      </div>

      {loading && !balance ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <>
          {balance && (
            <div
              className="keep-cols"
              style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: "13.6px" }}
            >
              <BlueprintCard style={{ padding: "13.6px" }}>
                <span className="ind-card-kicker">Disponíveis em {balance.year}</span>
                <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 34, lineHeight: 1 }}>
                  {formatDays(balance.available)}
                </div>
                <span className="ind-card-meta">dias úteis</span>
              </BlueprintCard>
              <BlueprintCard style={{ padding: "13.6px" }}>
                <span className="ind-card-kicker">Direito</span>
                <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 34, lineHeight: 1 }}>
                  {formatDays(balance.entitled)}
                </div>
                <span className="ind-card-meta">dias no ano</span>
              </BlueprintCard>
              <BlueprintCard style={{ padding: "13.6px" }}>
                <span className="ind-card-kicker">Gozados</span>
                <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 34, lineHeight: 1 }}>
                  {formatDays(balance.taken)}
                </div>
                <span className="ind-card-meta">aprovados</span>
              </BlueprintCard>
              <BlueprintCard style={{ padding: "13.6px" }}>
                <span className="ind-card-kicker">Pendentes</span>
                <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 34, lineHeight: 1 }}>
                  {formatDays(balance.pending)}
                </div>
                <span className="ind-card-meta">por aprovar</span>
              </BlueprintCard>
            </div>
          )}

          <BlueprintCard kicker="Calendário" style={{ padding: "13.6px" }}>
            <Calendar
              fullscreen={!isMobile}
              value={month}
              onPanelChange={(value) => setMonth(value.startOf("month"))}
              onSelect={(value, info) => {
                if (info.source !== "date") setMonth(value.startOf("month"));
              }}
              cellRender={(current, info) => (info.type === "date" ? cellRender(current) : info.originNode)}
            />
            <div className="ind-card-meta" style={{ display: "flex", gap: "13.6px", flexWrap: "wrap" }}>
              <span><Tag color="blue">Feriado</Tag></span>
              <span><Tag color="green">Aprovada</Tag></span>
              <span><Tag color="gold">Pendente</Tag></span>
            </div>
          </BlueprintCard>

          <BlueprintCard kicker={`Ausências em ${year}`} style={{ padding: "13.6px" }}>
            {sortedAbsences.length === 0 ? (
              <Empty description="Sem ausências marcadas neste ano" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column" }}>
                {sortedAbsences.map((absence) => (
                  <li
                    key={absence.id}
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: "10.2px",
                      padding: "10.2px 0",
                      borderBottom: "1px solid var(--ind-color-divider)",
                    }}
                  >
                    <div>
                      <strong>{ABSENCE_TYPE_LABEL[absence.type]}</strong>
                      <div className="ind-card-meta">
                        {formatRange(absence)} · {formatDays(absence.workingDays)} dia(s) útil(eis)
                      </div>
                    </div>
                    <Tag color={STATUS_COLOR[absence.status]} style={{ margin: 0 }}>
                      {ABSENCE_STATUS_LABEL[absence.status]}
                    </Tag>
                  </li>
                ))}
              </ul>
            )}
          </BlueprintCard>
        </>
      )}
    </div>
  );
}
