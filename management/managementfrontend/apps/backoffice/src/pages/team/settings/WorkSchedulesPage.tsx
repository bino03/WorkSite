import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Empty, Segmented, Table } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import {
  deleteWorkSchedule,
  listDeletedWorkSchedules,
  listWorkSchedules,
  restoreWorkSchedule,
} from "@/services/attendanceService";
import { WEEKDAYS, WEEKDAY_SHORT } from "@/types/attendance";
import type { WorkSchedule } from "@/types/attendance";
import { formatMinutes } from "@/utils/formatters";
import { ListActions, ListActionPrimary, ListActionSecondary, ListActionDanger } from "@/components/common/ListActions";
import { WorkScheduleUpsertDrawer } from "@/components/attendance/schedules/WorkScheduleUpsertDrawer";

/**
 * Resume os dias de um horário juntando os consecutivos: "Seg–Sex", "Seg–Qua, Sex".
 * Ler sete abreviaturas numa célula de tabela é pior do que ler duas.
 */
function summarizeDays(schedule: WorkSchedule): string {
  const worked = WEEKDAYS.filter((weekday) => schedule.days.some((day) => day.weekday === weekday));
  if (worked.length === 0) return "—";

  const runs: string[] = [];
  let runStart = 0;
  WEEKDAYS.forEach((weekday, index) => {
    const isWorked = worked.includes(weekday);
    const nextIsWorked = index + 1 < WEEKDAYS.length && worked.includes(WEEKDAYS[index + 1]);
    if (isWorked && !nextIsWorked) {
      const start = WEEKDAY_SHORT[WEEKDAYS[runStart]];
      const end = WEEKDAY_SHORT[weekday];
      runs.push(index - runStart >= 2 ? `${start}–${end}` : index === runStart ? start : `${start}, ${end}`);
    }
    if (!isWorked) runStart = index + 1;
  });
  return runs.join(", ");
}

type View = "active" | "deleted";

/**
 * Os horários de trabalho (Equipa → Configuração). Um horário já atribuído a
 * alguém não pode ser alterado (`SCHED_007`) — ver o aviso no drawer.
 */
export default function WorkSchedulesPage() {
  const confirm = useConfirm();
  const [view, setView] = useState<View>("active");
  const [schedules, setSchedules] = useState<WorkSchedule[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<{ schedule: WorkSchedule | null } | null>(null);

  const fetchSchedules = useCallback(async () => {
    setLoading(true);
    try {
      setSchedules(view === "active" ? await listWorkSchedules() : await listDeletedWorkSchedules());
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [view]);

  useEffect(() => {
    void fetchSchedules();
  }, [fetchSchedules]);

  const askDelete = useCallback((schedule: WorkSchedule) => {
    confirm({
      message: `Eliminar o horário "${schedule.name}"? Vai para os apagados e pode ser restaurado; os períodos de emprego que já o usam continuam a contar com ele.`,
      onConfirm: async () => {
        try {
          await deleteWorkSchedule(schedule.id);
          notificationService.success("Horário eliminado");
          void fetchSchedules();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  }, [confirm, fetchSchedules]);

  const askRestore = useCallback((schedule: WorkSchedule) => {
    confirm({
      title: "Restaurar horário",
      actionLabel: "Restaurar",
      message: `Voltar a pôr "${schedule.name}" entre os horários ativos?`,
      onConfirm: async () => {
        try {
          await restoreWorkSchedule(schedule.id);
          notificationService.success("Horário restaurado");
          void fetchSchedules();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  }, [confirm, fetchSchedules]);

  const columns = useMemo<ColumnsType<WorkSchedule>>(
    () => [
      {
        title: "Nome",
        dataIndex: "name",
        key: "name",
        render: (name: string, schedule) => (
          <div>
            <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{name}</span>
            {schedule.notes && <p className="ind-card-meta" style={{ margin: 0 }}>{schedule.notes}</p>}
          </div>
        ),
      },
      { title: "Dias", key: "days", width: 190, render: (_, schedule) => summarizeDays(schedule) },
      {
        title: "Horas/semana",
        dataIndex: "weeklyMinutes",
        key: "weeklyMinutes",
        width: 130,
        render: (minutes: number) => (
          <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{formatMinutes(minutes)}</span>
        ),
      },
      {
        title: "Criado por",
        dataIndex: "createdByName",
        key: "createdByName",
        width: 180,
        render: (createdBy: string | null) => createdBy ?? "—",
      },
      {
        title: "",
        key: "actions",
        width: 170,
        render: (_, schedule) =>
          view === "active" ? (
            <ListActions>
              <ListActionSecondary onClick={() => setEditing({ schedule })}>Editar</ListActionSecondary>
              <ListActionDanger onClick={() => askDelete(schedule)}>Eliminar</ListActionDanger>
            </ListActions>
          ) : (
            <ListActions>
              <ListActionPrimary onClick={() => askRestore(schedule)}>Restaurar</ListActionPrimary>
            </ListActions>
          ),
      },
    ],
    [view, askDelete, askRestore]
  );

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: "20.4px" }}>
        <div>
          <h6 style={{ color: "var(--ind-accent-700)" }}>Configuração</h6>
          <h1 style={{ margin: 0 }}>Horários</h1>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ schedule: null })}>
          Novo horário
        </Button>
      </div>

      <div style={{ marginBottom: "13.6px" }}>
        <Segmented
          value={view}
          onChange={(value) => setView(value as View)}
          options={[
            { label: "Ativos", value: "active" },
            { label: "Apagados", value: "deleted" },
          ]}
        />
      </div>

      <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
        <Table
          rowKey="id"
          columns={columns}
          dataSource={schedules ?? []}
          loading={loading}
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={
                  view === "active"
                    ? "Ainda não há horários. Crie o primeiro para o poder atribuir na ficha de emprego."
                    : "Nenhum horário apagado."
                }
              />
            ),
          }}
        />
      </div>

      <WorkScheduleUpsertDrawer
        open={!!editing}
        schedule={editing?.schedule ?? null}
        onClose={() => setEditing(null)}
        onSaved={() => void fetchSchedules()}
      />
    </div>
  );
}
