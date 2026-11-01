import { useMemo } from "react";
import type { CSSProperties } from "react";
import { Empty, Popover, Tooltip } from "antd";
import dayjs from "dayjs";
import { ABSENCE_STATUS_LABEL, ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence } from "@/types/attendance";

interface Props {
  /** "YYYY-MM" — o mês desenhado. */
  month: string;
  absences: Absence[];
  onPick: (absence: Absence) => void;
}

const WEEKDAYS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];

/** Num dia com mais gente fora do que isto, o resto fica atrás de um "+N". */
const MAX_CHIPS_PER_DAY = 3;

/**
 * O mapa de férias do mês, como um calendário de parede: semanas em linhas, segunda a domingo em
 * colunas, e em cada dia os nomes de quem falta. Responde a "quem falta no dia X" num relance — a
 * linha do tempo por funcionário que havia antes respondia bem a "quando falta o João" mas obrigava
 * a contar colunas para o resto (decisão de 2026-11-01).
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
  const today = dayjs().format("YYYY-MM-DD");

  /** As semanas do mês, de segunda a domingo; os dias de fora do mês ficam `null`. */
  const weeks = useMemo(() => {
    const first = dayjs(`${month}-01`);
    const leading = (first.day() + 6) % 7; // dayjs: 0 = domingo
    const cells: (string | null)[] = [
      ...Array.from({ length: leading }, () => null),
      ...Array.from({ length: first.daysInMonth() }, (_, index) => first.add(index, "day").format("YYYY-MM-DD")),
    ];
    while (cells.length % 7 !== 0) cells.push(null);
    return Array.from({ length: cells.length / 7 }, (_, week) => cells.slice(week * 7, week * 7 + 7));
  }, [month]);

  /** Por nome, para a mesma pessoa ficar na mesma posição de um dia para o outro. */
  const sorted = useMemo(
    () => [...absences].sort((a, b) => a.profileName.localeCompare(b.profileName)),
    [absences]
  );

  const absencesOn = (date: string) => sorted.filter((absence) => absence.startsOn <= date && absence.endsOn >= date);

  if (absences.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma ausência neste mês." />;
  }

  const chip = (absence: Absence) => (
    <Tooltip
      key={absence.id}
      title={`${absence.profileName} · ${ABSENCE_TYPE_LABEL[absence.type]} · ${ABSENCE_STATUS_LABEL[absence.status]} · ${dayjs(
        absence.startsOn
      ).format("DD/MM")}–${dayjs(absence.endsOn).format("DD/MM")}`}
    >
      <button
        type="button"
        onClick={() => onPick(absence)}
        aria-label={`${absence.profileName}: ${ABSENCE_TYPE_LABEL[absence.type]}, ${ABSENCE_STATUS_LABEL[absence.status]}`}
        style={{
          display: "block",
          width: "100%",
          padding: "1px 5px",
          fontSize: 11,
          lineHeight: "16px",
          textAlign: "left",
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
          cursor: "pointer",
          borderRadius: 3,
          ...chipStyle(absence),
        }}
      >
        {absence.profileName}
        {absence.halfDay !== "NONE" ? " ½" : ""}
      </button>
    </Tooltip>
  );

  return (
    <div style={{ overflowX: "auto" }}>
      <div style={{ minWidth: 640 }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 2, marginBottom: 2 }}>
          {WEEKDAYS.map((weekday, index) => (
            <span
              key={weekday}
              style={{
                fontSize: 11,
                textAlign: "center",
                fontFamily: "var(--ind-font-heading)",
                textTransform: "uppercase",
                opacity: index >= 5 ? 0.4 : 0.65,
              }}
            >
              {weekday}
            </span>
          ))}
        </div>

        {weeks.map((week, weekIndex) => (
          <div key={weekIndex} style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", gap: 2, marginBottom: 2 }}>
            {week.map((date, dayIndex) => {
              if (!date) {
                return <div key={`empty-${dayIndex}`} style={{ minHeight: 84 }} />;
              }
              const isWeekend = dayIndex >= 5;
              const isToday = date === today;
              const away = absencesOn(date);
              const shown = away.slice(0, MAX_CHIPS_PER_DAY);
              const hidden = away.slice(MAX_CHIPS_PER_DAY);
              return (
                <div
                  key={date}
                  style={{
                    minHeight: 84,
                    padding: 4,
                    display: "flex",
                    flexDirection: "column",
                    gap: 2,
                    minWidth: 0,
                    background: isWeekend ? "var(--ind-neutral-100)" : "transparent",
                    border: isToday ? "1px solid var(--ind-color-accent)" : "1px solid var(--ind-color-divider)",
                  }}
                >
                  <span
                    style={{
                      fontSize: 12,
                      fontFamily: "var(--ind-font-heading)",
                      fontWeight: isToday ? 700 : 400,
                      color: isToday ? "var(--ind-color-accent)" : undefined,
                      opacity: isWeekend && !isToday ? 0.45 : 0.8,
                    }}
                  >
                    {dayjs(date).date()}
                    {isToday ? " · hoje" : ""}
                  </span>
                  {shown.map(chip)}
                  {hidden.length > 0 && (
                    <Popover
                      trigger="click"
                      title={dayjs(date).format("DD/MM")}
                      content={<div style={{ display: "flex", flexDirection: "column", gap: 2, width: 180 }}>{away.map(chip)}</div>}
                    >
                      <button
                        type="button"
                        style={{
                          padding: 0,
                          fontSize: 11,
                          textAlign: "left",
                          background: "transparent",
                          border: "none",
                          cursor: "pointer",
                          color: "var(--ind-color-accent)",
                        }}
                      >
                        +{hidden.length} {hidden.length === 1 ? "outro" : "outros"}
                      </button>
                    </Popover>
                  )}
                </div>
              );
            })}
          </div>
        ))}
      </div>

      <div style={{ display: "flex", flexWrap: "wrap", gap: 16, marginTop: "13.6px", fontSize: 11, opacity: 0.75 }}>
        {LEGEND.map(([label, style]) => (
          <span key={label} style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 18, height: 10, borderRadius: 2, ...style }} /> {label}
          </span>
        ))}
        <span style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 18, height: 10, border: "1px solid var(--ind-color-accent)" }} /> Hoje
        </span>
        <span>½ meio dia</span>
      </div>
    </div>
  );
}

const APPROVED: CSSProperties = { background: "var(--ind-color-accent)", color: "var(--ind-neutral-100)", border: "none" };
const PENDING: CSSProperties = { background: "transparent", color: "var(--ind-accent-800)", border: "1px dashed var(--ind-color-accent)" };
const SICK: CSSProperties = { background: "var(--ind-accent-2-600)", color: "var(--ind-neutral-100)", border: "none" };
const REJECTED: CSSProperties = {
  background: "var(--ind-neutral-200)",
  color: "var(--ind-neutral-600)",
  border: "none",
  textDecoration: "line-through",
};

function chipStyle(absence: Absence): CSSProperties {
  if (absence.type === "SICK_LEAVE") return SICK;
  if (absence.status === "PENDING") return PENDING;
  if (absence.status === "REJECTED") return REJECTED;
  return APPROVED;
}

const LEGEND: [string, CSSProperties][] = [
  ["Aprovadas", APPROVED],
  ["Pendentes", PENDING],
  ["Baixa", SICK],
  ["Recusadas", REJECTED],
];
