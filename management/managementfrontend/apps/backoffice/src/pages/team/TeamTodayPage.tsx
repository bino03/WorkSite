import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Empty, Spin } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { getMonthReport, listPendingAbsences, listTeamAbsences } from "@/services/attendanceService";
import { ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AttendanceMonthReport, DayAttendance } from "@/types/attendance";
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
 * **Não tem endpoint próprio** — monta-se de três pedidos que já serviam outros
 * ecrãs (`reports/month`, `absences/pending`, `absences/team`). Os números são
 * derivados dos mesmos `days[]` que a página de Picagens usa, para não haver duas
 * contas diferentes do mesmo facto.
 */
export default function TeamTodayPage() {
  const navigate = useNavigate();
  const today = dayjs().format("YYYY-MM-DD");
  const month = dayjs().format("YYYY-MM");

  const [report, setReport] = useState<AttendanceMonthReport | null>(null);
  const [pending, setPending] = useState<Absence[]>([]);
  const [monthAbsences, setMonthAbsences] = useState<Absence[]>([]);
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
      const [monthReport, pendingAbsences, teamAbsences] = await Promise.all([
        getMonthReport(month),
        listPendingAbsences(),
        listTeamAbsences(from, to),
      ]);
      setReport(monthReport);
      setPending(pendingAbsences);
      setMonthAbsences(teamAbsences);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void fetchAll();
  }, [fetchAll]);

  const { working, missing, incomplete, attention } = useMemo(() => {
    const rows: Attention[] = [];
    let workingNow = 0;
    let missingDays = 0;
    let incompleteDays = 0;

    report?.employees.forEach((employee) => {
      const name = employee.profileName ?? "—";
      employee.days.forEach((day: DayAttendance) => {
        // "A trabalhar agora" é hoje com entrada picada e ainda sem saída.
        if (day.date === today && day.firstIn && !day.lastOut) workingNow += 1;

        if (day.status === "MISSING") {
          missingDays += 1;
          rows.push({
            key: `m-${employee.profileId}-${day.date}`,
            profileId: employee.profileId,
            profileName: name,
            day: day.date,
            what: `Falta — ${formatLongDate(day.date)}`,
            action: "justify",
          });
        } else if (day.incomplete) {
          incompleteDays += 1;
          rows.push({
            key: `i-${employee.profileId}-${day.date}`,
            profileId: employee.profileId,
            profileName: name,
            day: day.date,
            what: `Dia incompleto — entrada ${day.firstIn ?? "?"}, sem saída`,
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
      working: workingNow,
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
            <Stat value={working} label="A trabalhar agora" hint="com entrada picada e sem saída" />
            <Stat value={missing} label="Faltas por justificar" hint="este mês" />
            <Stat value={incomplete} label="Dias incompletos" hint="falta uma saída" />
            <Stat value={pending.length} label="Pedidos de férias" hint="à espera de aprovação" />
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
