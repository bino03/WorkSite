import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Empty, Spin } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import {
  getMonthReport,
  listHolidays,
  listPendingAbsences,
  listTeamAbsences,
  listTeamDay,
} from "@/services/attendanceService";
import { ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AttendanceMonthReport, DayAttendance, Holiday, TeamDayPerson } from "@/types/attendance";
import { WhoIsInCard, whoIsIn } from "@/components/attendance/today/WhoIsInCard";
import { UpcomingHolidaysCard } from "@/components/attendance/today/UpcomingHolidaysCard";
import { formatLongDate } from "@/utils/formatters";
import { TeamAbsenceMap } from "@/components/attendance/absences/TeamAbsenceMap";
import { AbsenceUpsertDrawer } from "@/components/attendance/absences/AbsenceUpsertDrawer";
import { AbsenceDetailDrawer } from "@/components/attendance/absences/AbsenceDetailDrawer";
import { JustifyDayModal } from "@/components/attendance/absences/JustifyDayModal";
import { DayTimeEntriesDrawer } from "@/components/attendance/timeentries/DayTimeEntriesDrawer";
import { TimeEntryUpsertDrawer } from "@/components/attendance/timeentries/TimeEntryUpsertDrawer";

/** Uma linha de "Precisa de atenção": o que está mal e o que se faz com isso. */
interface Attention {
  key: string;
  profileId: string;
  profileName: string;
  day: string;
  what: string;
  action: "justify" | "correct" | "decide" | "assign";
  absenceId?: string;
}

const ACTION_LABEL = {
  justify: "Justificar",
  correct: "Corrigir",
  decide: "Decidir",
  assign: "Atribuir",
} as const;

function Stat({ value, label, hint }: { value: number; label: string; hint: string }) {
  return (
    <div className="ind-card" style={{ padding: "13.6px 20.4px" }}>
      <p className="ind-card-meta" style={{ margin: 0 }}>{label}</p>
      <p style={{ fontFamily: "var(--ind-font-heading)", fontSize: 38, lineHeight: 1.1, margin: "4px 0" }}>{value}</p>
      <p className="ind-card-meta" style={{ margin: 0 }}>{hint}</p>
    </div>
  );
}

/**
 * O painel de entrada do espaço Equipa: o dia de hoje, o que está por resolver, e
 * o mapa de férias do mês.
 *
 * Monta-se de três pedidos que já serviam outros ecrãs (`reports/month`,
 * `absences/pending`, `absences/team`) e de um próprio, `time-entries/team-day`, para o
 * "Quem está": o relatório do mês só tem as obras de pares fechados, e quem só picou
 * entrada não tinha obra. Faltas e dias incompletos continuam a sair dos mesmos `days[]`
 * que a página de Picagens usa, para não haver duas contas diferentes do mesmo facto.
 */
export default function TeamTodayPage() {
  const navigate = useNavigate();
  const today = dayjs().format("YYYY-MM-DD");
  const month = dayjs().format("YYYY-MM");

  const [report, setReport] = useState<AttendanceMonthReport | null>(null);
  const [pending, setPending] = useState<Absence[]>([]);
  const [monthAbsences, setMonthAbsences] = useState<Absence[]>([]);
  const [teamDay, setTeamDay] = useState<TeamDayPerson[]>([]);
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [now, setNow] = useState(() => dayjs().format("HH:mm"));
  const [loading, setLoading] = useState(true);

  const [registerEntry, setRegisterEntry] = useState(false);
  const [registerAbsence, setRegisterAbsence] = useState(false);
  const [correcting, setCorrecting] = useState<Attention | null>(null);
  const [justifying, setJustifying] = useState<Attention | null>(null);
  const [decidingId, setDecidingId] = useState<string | null>(null);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    const from = `${month}-01`;
    const to = dayjs(from).endOf("month").format("YYYY-MM-DD");
    try {
      const [monthReport, pendingAbsences, teamAbsences, teamToday, nextHolidays] = await Promise.all([
        getMonthReport(month),
        listPendingAbsences(),
        listTeamAbsences(from, to),
        listTeamDay(today),
        // Um ano à frente numa chamada só: em dezembro apanha os de janeiro.
        listHolidays(today, dayjs(today).add(1, "year").format("YYYY-MM-DD")),
      ]);
      setHolidays(nextHolidays);
      setReport(monthReport);
      setPending(pendingAbsences);
      setMonthAbsences(teamAbsences);
      setTeamDay(teamToday);
      setNow(dayjs().format("HH:mm"));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [month, today]);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  // "A trabalhar agora" sai daqui e não do relatório do mês: desde que o almoço se pica, quem
  // voltou do almoço tem uma saída no dia, e o "entrada sem saída" antigo deixava-o de fora.
  const who = useMemo(() => {
    const holidayToday = !!report?.employees.some((employee) =>
      employee.days.some((day) => day.date === today && day.status === "HOLIDAY")
    );
    return whoIsIn(teamDay, monthAbsences, today, now, holidayToday);
  }, [teamDay, monthAbsences, today, now, report]);

  const { missing, incomplete, attention } = useMemo(() => {
    const rows: Attention[] = [];
    let missingDays = 0;
    let incompleteDays = 0;

    report?.employees.forEach((employee) => {
      const name = employee.profileName ?? "—";
      employee.days.forEach((day: DayAttendance) => {

        // Os dias futuros já vêm do backend como "UPCOMING", mas hoje conta lá como dia começado:
        // sem picagens é "MISSING", e quem está a trabalhar agora vem como "incompleto". Aqui só se
        // pede justificação ou correção do que já terminou.
        const hasHappened = day.date < today;

        if (day.status === "MISSING" && hasHappened) {
          missingDays += 1;
          rows.push({
            key: `m-${employee.profileId}-${day.date}`,
            profileId: employee.profileId,
            profileName: name,
            day: day.date,
            what: `Falta — ${formatLongDate(day.date)}`,
            action: "justify",
          });
        } else if (day.incomplete && hasHappened) {
          incompleteDays += 1;
          rows.push({
            key: `i-${employee.profileId}-${day.date}`,
            profileId: employee.profileId,
            profileName: name,
            day: day.date,
            what: day.lunchNotClocked
              ? `Almoço por picar — ${day.firstIn ?? "?"}–${day.lastOut ?? "?"}, a pausa do horário foi descontada`
              : `Dia incompleto — entrada ${day.firstIn ?? "?"}, sem saída`,
            action: "correct",
          });
        } else if (day.status === "NO_SCHEDULE" && day.date === today) {
          // Uma vez por pessoa, não um aviso por cada dia do mês.
          rows.push({
            key: `s-${employee.profileId}`,
            profileId: employee.profileId,
            profileName: name,
            day: day.date,
            what: "Sem horário atribuído",
            action: "assign",
          });
        }
      });
    });

    pending.forEach((absence) => {
      rows.push({
        key: `a-${absence.id}`,
        profileId: absence.profileId,
        profileName: absence.profileName,
        day: absence.startsOn,
        what: `${ABSENCE_TYPE_LABEL[absence.type]} ${dayjs(absence.startsOn).format("DD/MM")}–${dayjs(
          absence.endsOn
        ).format("DD/MM")} · pendente`,
        action: "decide",
        absenceId: absence.id,
      });
    });

    return {
      missing: missingDays,
      incomplete: incompleteDays,
      attention: rows.sort((a, b) => b.day.localeCompare(a.day)),
    };
  }, [report, pending, today]);

  const act = (row: Attention) => {
    if (row.action === "justify") setJustifying(row);
    else if (row.action === "correct") setCorrecting(row);
    else if (row.action === "decide" && row.absenceId) setDecidingId(row.absenceId);
    else if (row.action === "assign") navigate(`/team/employees/${row.profileId}`);
  };

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Equipa · Hoje</h6>
          <h1 style={{ margin: 0 }}>{formatLongDate(today)}</h1>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <Button onClick={() => setRegisterAbsence(true)}>Registar ausência</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setRegisterEntry(true)}>
            Registar picagem
          </Button>
        </div>
      </div>

      {loading ? (
        <div style={{ padding: "40.8px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : (
        <>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
              gap: "13.6px",
              marginBottom: "20.4px",
            }}
          >
            <Stat value={who.workingCount} label="A trabalhar agora" hint="a última picagem é uma entrada" />
            <Stat value={missing} label="Faltas por justificar" hint="este mês" />
            <Stat value={incomplete} label="Dias incompletos" hint="falta uma saída ou o almoço" />
            <Stat value={pending.length} label="Pedidos de férias" hint="à espera de aprovação" />
          </div>

          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
              gap: "13.6px",
              marginBottom: "20.4px",
            }}
          >
            <WhoIsInCard state={who} />
            <UpcomingHolidaysCard holidays={holidays} today={today} />
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "minmax(320px, 2fr) 3fr", gap: "13.6px" }}>
            <div className="ind-card" style={{ padding: "20.4px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
                <h2 style={{ margin: 0 }}>Precisa de atenção</h2>
                <span className="ind-card-meta" style={{ margin: 0 }}>faltas e dias mal preenchidos</span>
              </div>

              <div style={{ marginTop: "13.6px" }}>
                {attention.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nada por resolver este mês." />
                ) : (
                  attention.map((row) => (
                    <div
                      key={row.key}
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        gap: 12,
                        padding: "10.2px 0",
                        borderTop: "1px solid var(--ind-color-divider)",
                      }}
                    >
                      <div style={{ minWidth: 0 }}>
                        <p style={{ margin: 0, fontWeight: 600 }}>{row.profileName}</p>
                        <p className="ind-card-meta" style={{ margin: 0 }}>{row.what}</p>
                      </div>
                      <Button type="link" style={{ padding: 0 }} onClick={() => act(row)}>
                        {ACTION_LABEL[row.action]}
                      </Button>
                    </div>
                  ))
                )}
              </div>
            </div>

            <div className="ind-card" style={{ padding: "20.4px" }}>
              <h2 style={{ margin: "0 0 13.6px" }}>Mapa de férias do mês</h2>
              <TeamAbsenceMap month={month} absences={monthAbsences} onPick={(absence) => setDecidingId(absence.id)} />
            </div>
          </div>
        </>
      )}

      <TimeEntryUpsertDrawer
        open={registerEntry}
        profileId=""
        day={today}
        entry={null}
        onClose={() => setRegisterEntry(false)}
        onSaved={() => void fetchAll()}
      />

      <AbsenceUpsertDrawer
        open={registerAbsence}
        absence={null}
        defaultDay={today}
        onClose={() => setRegisterAbsence(false)}
        onSaved={() => void fetchAll()}
      />

      {correcting && (
        <DayTimeEntriesDrawer
          open
          profileId={correcting.profileId}
          profileName={correcting.profileName}
          day={correcting.day}
          onClose={() => setCorrecting(null)}
          onChanged={() => void fetchAll()}
        />
      )}

      {justifying && (
        <JustifyDayModal
          open
          profileId={justifying.profileId}
          profileName={justifying.profileName}
          day={justifying.day}
          onCancel={() => setJustifying(null)}
          onDone={() => {
            setJustifying(null);
            void fetchAll();
          }}
        />
      )}

      <AbsenceDetailDrawer
        absenceId={decidingId}
        onClose={() => setDecidingId(null)}
        onChanged={() => void fetchAll()}
      />
    </div>
  );
}
