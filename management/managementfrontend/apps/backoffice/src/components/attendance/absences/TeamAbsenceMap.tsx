import { useMemo } from "react";
import { Empty, Tooltip } from "antd";
import dayjs from "dayjs";
import { ABSENCE_STATUS_LABEL, ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence } from "@/types/attendance";

interface Props {
  /** "YYYY-MM" — o mês desenhado. */
  month: string;
  absences: Absence[];
  onPick: (absence: Absence) => void;
}

/**
 * O mapa de férias do mês: uma linha por funcionário, uma coluna por dia. As
 * barras são `<div>`s numa grelha CSS — não é um calendário de biblioteca, é a
 * leitura rápida de "quem falta quando" que a maquete pede.
 *
 * O preenchimento diz o **estado**, não o tipo, com uma exceção: a baixa tem cor
 * própria porque é a que muda o que se faz a seguir (não se recusa uma baixa).
 *
 * A maquete pinta a baixa de laranja, mas **a paleta não tem laranja** — é toda
 * azul/neutra, com o verde do espaço por cima. Usa-se o segundo acento
 * (`--ind-accent-2-*`) em vez de introduzir um hex novo; se a laranja for mesmo
 * precisa, entra primeiro como token em `index.css`.
 */
export function TeamAbsenceMap({ month, absences, onPick }: Props) {
  const daysInMonth = dayjs(`${month}-01`).daysInMonth();
  const days = Array.from({ length: daysInMonth }, (_, index) => index + 1);
  const firstDay = `${month}-01`;
  const lastDay = `${month}-${String(daysInMonth).padStart(2, "0")}`;

  const byEmployee = useMemo(() => {
    const map = new Map<string, { name: string; absences: Absence[] }>();
    absences.forEach((absence) => {
      const entry = map.get(absence.profileId) ?? { name: absence.profileName, absences: [] };
      entry.absences.push(absence);
      map.set(absence.profileId, entry);
    });
    return [...map.entries()].sort((a, b) => a[1].name.localeCompare(b[1].name));
  }, [absences]);

  /** Fim de semana: pinta-se a coluna para o mapa se ler como um calendário. */
  const isWeekend = (day: number) => {
    const weekday = dayjs(`${month}-${String(day).padStart(2, "0")}`).day();
    return weekday === 0 || weekday === 6;
  };

  const barStyle = (absence: Absence) => {
    if (absence.type === "SICK_LEAVE") {
      return { background: "var(--ind-accent-2-600)", border: "none" };
    }
    if (absence.status === "PENDING") {
      return { background: "transparent", border: "1px dashed var(--ind-color-accent)" };
    }
    if (absence.status === "REJECTED") {
      return { background: "var(--ind-neutral-200)", border: "none" };
    }
    return { background: "var(--ind-color-accent)", border: "none" };
  };

  if (byEmployee.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma ausência neste mês." />;
  }

  return (
    <div style={{ overflowX: "auto" }}>
      <div style={{ minWidth: 680 }}>
        {/* Cabeçalho dos dias */}
        <div style={{ display: "grid", gridTemplateColumns: `160px repeat(${daysInMonth}, 1fr)`, gap: 2 }}>
          <span />
          {days.map((day) => (
            <span
              key={day}
              style={{
                fontSize: 10,
                textAlign: "center",
                opacity: isWeekend(day) ? 0.35 : 0.6,
                fontFamily: "var(--ind-font-heading)",
              }}
            >
              {day}
            </span>
          ))}
        </div>

        {byEmployee.map(([profileId, employee]) => (
          <div
            key={profileId}
            style={{
              display: "grid",
              gridTemplateColumns: `160px repeat(${daysInMonth}, 1fr)`,
              gap: 2,
              alignItems: "center",
              height: 30,
              borderTop: "1px solid var(--ind-color-divider)",
            }}
          >
            <span style={{ fontSize: 12, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {employee.name}
            </span>
            {days.map((day) => {
              const date = `${month}-${String(day).padStart(2, "0")}`;
              const absence = employee.absences.find(
                (candidate) =>
                  candidate.startsOn <= date &&
                  candidate.endsOn >= date &&
                  // Uma ausência pode atravessar o mês; o mapa só mostra a fatia dele.
                  candidate.endsOn >= firstDay &&
                  candidate.startsOn <= lastDay
              );
              if (!absence) {
                return (
                  <span
                    key={day}
                    style={{ height: 14, background: isWeekend(day) ? "var(--ind-neutral-100)" : "transparent" }}
                  />
                );
              }
              return (
                <Tooltip
                  key={day}
                  title={`${ABSENCE_TYPE_LABEL[absence.type]} · ${ABSENCE_STATUS_LABEL[absence.status]} · ${dayjs(
                    absence.startsOn
                  ).format("DD/MM")}–${dayjs(absence.endsOn).format("DD/MM")}`}
                >
                  <button
                    type="button"
                    onClick={() => onPick(absence)}
                    aria-label={`${employee.name}, dia ${day}: ${ABSENCE_TYPE_LABEL[absence.type]}`}
                    style={{ height: 14, padding: 0, cursor: "pointer", ...barStyle(absence) }}
                  />
                </Tooltip>
              );
            })}
          </div>
        ))}
      </div>

      <div style={{ display: "flex", gap: 16, marginTop: "13.6px", fontSize: 11, opacity: 0.75 }}>
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 18, height: 10, background: "var(--ind-color-accent)" }} /> Aprovadas
        </span>
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 18, height: 10, border: "1px dashed var(--ind-color-accent)" }} /> Pendentes
        </span>
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 18, height: 10, background: "var(--ind-accent-2-600)" }} /> Baixa
        </span>
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 18, height: 10, background: "var(--ind-neutral-200)" }} /> Recusadas
        </span>
      </div>
    </div>
  );
}
