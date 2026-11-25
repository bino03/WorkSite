import type { ReactNode } from "react";
import { Empty } from "antd";
import { InitialsAvatars } from "@/components/common/InitialsAvatars";
import type { Absence, TeamDayPerson } from "@/types/attendance";

interface Person {
  id: string;
  name: string;
}

interface WorkingGroup {
  key: string;
  /** null = picagem sem obra (só dados antigos: o formulário do admin já exige a obra). */
  enterpriseName: string | null;
  people: Person[];
}

export interface WhoIsIn {
  working: WorkingGroup[];
  workingCount: number;
  vacation: Person[];
  away: Person[];
  missing: Person[];
}

const covers = (absence: Absence, day: string) =>
  absence.status === "APPROVED" && !absence.deletedAt && absence.startsOn <= day && day <= absence.endsOn;

/**
 * Quem está em que estado hoje. A fonte de cada estado:
 *
 * - **A trabalhar**: a última picagem viva do dia é uma entrada; a obra é a dessa entrada
 *   (de manhã numa obra e à tarde noutra conta a da tarde).
 * - **Férias / Ausências**: ausências **aprovadas** que cobrem hoje. Uma de meio dia não
 *   tira a pessoa de "A trabalhar" — aparece nos dois, que é a verdade do dia.
 * - **Faltas**: uma falta injustificada aprovada para hoje, ou quem devia trabalhar (o
 *   horário tem hora de entrada hoje), já passou essa hora, não tem picagens nem ausência
 *   aprovada, e não é feriado. Antes da hora de entrada, quem ainda não chegou não é falta.
 */
export function whoIsIn(
  team: TeamDayPerson[],
  absences: Absence[],
  today: string,
  nowTime: string,
  holidayToday: boolean
): WhoIsIn {
  const todays = absences.filter((absence) => covers(absence, today));
  const groups = new Map<string, WorkingGroup>();
  const vacation: Person[] = [];
  const away: Person[] = [];
  const missing: Person[] = [];
  let workingCount = 0;

  team.forEach((member) => {
    const person = { id: member.profileId, name: member.profileName };
    const mine = todays.filter((absence) => absence.profileId === member.profileId);
    const last = member.entries[member.entries.length - 1];

    if (last?.direction === "IN") {
      const key = last.enterpriseId ?? "none";
      const group = groups.get(key) ?? { key, enterpriseName: last.enterpriseName, people: [] };
      group.people.push(person);
      groups.set(key, group);
      workingCount += 1;
    }

    if (mine.some((absence) => absence.type === "VACATION")) vacation.push(person);
    else if (mine.some((absence) => absence.type !== "UNJUSTIFIED")) away.push(person);

    const unjustified = mine.some((absence) => absence.type === "UNJUSTIFIED");
    const lateWithoutTrace =
      !holidayToday &&
      member.scheduledStart !== null &&
      nowTime >= member.scheduledStart.slice(0, 5) &&
      member.entries.length === 0 &&
      mine.length === 0;
    if (unjustified || lateWithoutTrace) missing.push(person);
  });

  const byName = (a: Person, b: Person) => a.name.localeCompare(b.name);
  const working = [...groups.values()]
    .map((group) => ({ ...group, people: group.people.sort(byName) }))
    // As obras por ordem alfabética, e "sem obra" sempre no fim.
    .sort((a, b) => (a.enterpriseName ?? "￿").localeCompare(b.enterpriseName ?? "￿"));

  return {
    working,
    workingCount,
    vacation: vacation.sort(byName),
    away: away.sort(byName),
    missing: missing.sort(byName),
  };
}

function Row({ label, people, children }: { label: string; people: Person[]; children?: ReactNode }) {
  return (
    <div style={{ padding: "10.2px 0", borderTop: "1px solid var(--ind-color-divider)" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
        <span style={{ fontWeight: 600 }}>
          {label} <span className="ind-card-meta">· {people.length}</span>
        </span>
        {!children && people.length > 0 && <InitialsAvatars people={people} />}
      </div>
      {children}
    </div>
  );
}

/** O cartão "Quem está" do painel Hoje (ideia tirada do painel da Sesame, 2026-11-20). */
export function WhoIsInCard({ state }: { state: WhoIsIn }) {
  const allWorking = state.working.flatMap((group) => group.people);
  const nobody =
    state.workingCount === 0 && state.vacation.length === 0 && state.away.length === 0 && state.missing.length === 0;

  return (
    <div className="ind-card" style={{ padding: "20.4px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2 style={{ margin: 0 }}>Quem está</h2>
        <span className="ind-card-meta" style={{ margin: 0 }}>hoje, agora</span>
      </div>
      <div style={{ marginTop: "13.6px" }}>
        {nobody ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Ainda ninguém picou hoje." />
        ) : (
          <>
            <Row label="A trabalhar" people={allWorking}>
              {state.working.map((group) => (
                <div
                  key={group.key}
                  style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, marginTop: 6 }}
                >
                  <span className="ind-card-meta" style={{ margin: 0 }}>
                    {group.enterpriseName ?? "Sem obra"}
                  </span>
                  <InitialsAvatars people={group.people} />
                </div>
              ))}
            </Row>
            <Row label="Férias" people={state.vacation} />
            <Row label="Ausências" people={state.away} />
            <Row label="Faltas" people={state.missing} />
          </>
        )}
      </div>
    </div>
  );
}
