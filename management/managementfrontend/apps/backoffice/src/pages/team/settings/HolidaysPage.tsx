import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Empty, Table } from "antd";
import { LeftOutlined, PlusOutlined, RightOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { deleteHoliday, listHolidays } from "@/services/attendanceService";
import type { Holiday } from "@/types/attendance";
import { ListActions, ListActionSecondary, ListActionDanger } from "@/components/common/ListActions";
import { HolidayUpsertDrawer } from "@/components/attendance/holidays/HolidayUpsertDrawer";

const SCOPE_LABEL: Record<Holiday["scope"], string> = {
  NATIONAL: "Nacional",
  MUNICIPAL: "Municipal",
};

/**
 * Os feriados (Equipa → Configuração), um ano de cada vez: são poucos por ano e
 * mudam de ano para ano, pelo que a lista completa nunca é o que se quer ver.
 * Apagar é a sério — um feriado errado não tem de ficar guardado.
 */
export default function HolidaysPage() {
  const confirm = useConfirm();
  const [year, setYear] = useState(() => dayjs().year());
  const [holidays, setHolidays] = useState<Holiday[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<{ holiday: Holiday | null } | null>(null);

  const fetchHolidays = useCallback(async () => {
    setLoading(true);
    try {
      setHolidays(await listHolidays(`${year}-01-01`, `${year}-12-31`));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [year]);

  useEffect(() => {
    void fetchHolidays();
  }, [fetchHolidays]);

  const askDelete = useCallback(
    (holiday: Holiday) => {
      confirm({
        message: `Eliminar o feriado "${holiday.name}" de ${dayjs(holiday.date).format("DD/MM/YYYY")}? Não vai para nenhuma zona de recuperação. Os relatórios são calculados ao vivo, por isso esse dia volta a contar como dia de trabalho — também nos meses já passados.`,
        onConfirm: async () => {
          try {
            await deleteHoliday(holiday.id);
            notificationService.success("Feriado eliminado");
            void fetchHolidays();
          } catch (error) {
            ErrorHandler.handle(error);
          }
        },
      });
    },
    [confirm, fetchHolidays]
  );

  const columns = useMemo<ColumnsType<Holiday>>(
    () => [
      {
        title: "Data",
        dataIndex: "date",
        key: "date",
        width: 160,
        render: (date: string) => (
          <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
            {dayjs(date).format("DD/MM/YYYY")}
          </span>
        ),
      },
      { title: "Nome", dataIndex: "name", key: "name" },
      {
        title: "Âmbito",
        dataIndex: "scope",
        key: "scope",
        width: 130,
        render: (scope: Holiday["scope"]) => (
          <span className={`ind-tag ${scope === "MUNICIPAL" ? "ind-tag-outline" : "ind-tag-neutral"}`}>
            {SCOPE_LABEL[scope]}
          </span>
        ),
      },
      {
        title: "Concelho",
        dataIndex: "municipality",
        key: "municipality",
        width: 180,
        render: (municipality: string | null) => municipality ?? "—",
      },
      {
        title: "",
        key: "actions",
        width: 170,
        render: (_, holiday) => (
          <ListActions>
            <ListActionSecondary onClick={() => setEditing({ holiday })}>Editar</ListActionSecondary>
            <ListActionDanger onClick={() => askDelete(holiday)}>Eliminar</ListActionDanger>
          </ListActions>
        ),
      },
    ],
    [askDelete]
  );

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Configuração</h6>
          <h1 style={{ margin: 0 }}>Feriados</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ holiday: null })}>
          Novo feriado
        </Button>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: "13.6px" }}>
        <Button size="small" icon={<LeftOutlined />} onClick={() => setYear(year - 1)} aria-label="Ano anterior" />
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600, minWidth: 48, textAlign: "center" }}>
          {year}
        </span>
        <Button size="small" icon={<RightOutlined />} onClick={() => setYear(year + 1)} aria-label="Ano seguinte" />
      </div>

      <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={holidays}
          loading={loading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={`Nenhum feriado registado em ${year}.`}
              />
            ),
          }}
        />
      </div>

      <div style={{ marginTop: 16 }}>
        <p style={{ fontSize: 12, opacity: 0.6, margin: 0 }}>{holidays.length} feriado(s) em {year}</p>
      </div>

      <HolidayUpsertDrawer
        open={!!editing}
        holiday={editing?.holiday ?? null}
        defaultYear={year}
        onClose={() => setEditing(null)}
        onSaved={() => void fetchHolidays()}
      />
    </div>
  );
}
