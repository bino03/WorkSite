import { Button, Empty } from "antd";
import { useNavigate } from "react-router-dom";
import dayjs from "dayjs";
import type { Holiday } from "@/types/attendance";
import { formatLongDate } from "@/utils/formatters";

interface Props {
  /** Os feriados de hoje a um ano, por ordem — o cartão mostra os 3 primeiros. */
  holidays: Holiday[];
  today: string;
}

const countdown = (date: string, today: string) => {
  const days = dayjs(date).diff(dayjs(today), "day");
  if (days === 0) return "hoje";
  if (days === 1) return "amanhã";
  return `faltam ${days} dias`;
};

/**
 * Os 3 feriados seguintes (o de hoje conta), lidos dos feriados de Configuração. Mostra
 * também os municipais, com o concelho: o cálculo das horas aplica qualquer feriado a
 * toda a equipa, e o cartão diz o mesmo que ele.
 */
export function UpcomingHolidaysCard({ holidays, today }: Props) {
  const navigate = useNavigate();
  const next = [...holidays]
    .filter((holiday) => holiday.date >= today)
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(0, 3);

  return (
    <div className="ind-card" style={{ padding: "20.4px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <h2 style={{ margin: 0 }}>Próximos feriados</h2>
        <Button type="link" style={{ padding: 0 }} onClick={() => navigate("/team/settings/holidays")}>
          Ver todos
        </Button>
      </div>
      <div style={{ marginTop: "13.6px" }}>
        {next.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="Sem feriados no próximo ano. Acrescentam-se em Configuração → Feriados."
          />
        ) : (
          next.map((holiday) => (
            <div
              key={holiday.id}
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
                <p style={{ margin: 0, fontWeight: 600 }}>{holiday.name}</p>
                <p className="ind-card-meta" style={{ margin: 0 }}>
                  {formatLongDate(holiday.date, true)}
                  {holiday.scope === "MUNICIPAL" && ` · Municipal${holiday.municipality ? ` · ${holiday.municipality}` : ""}`}
                </p>
              </div>
              <span className={`ind-tag ${holiday.date === today ? "ind-tag-accent" : "ind-tag-neutral"}`}>
                {countdown(holiday.date, today)}
              </span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
