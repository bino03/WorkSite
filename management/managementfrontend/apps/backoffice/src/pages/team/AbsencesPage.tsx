import { useCallback, useEffect, useMemo, useState } from "react";
import { Badge, Button, Empty, Input, Segmented, Spin, Table } from "antd";
import { LeftOutlined, PlusOutlined, RightOutlined, SearchOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { deleteAbsence, listTeamAbsences } from "@/services/attendanceService";
import { ABSENCE_STATUS_LABEL, ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AbsenceStatus } from "@/types/attendance";
import { formatMonth } from "@/utils/formatters";
import { ListActions, ListActionPrimary, ListActionSecondary, ListActionDanger } from "@/components/common/ListActions";
import { TeamAbsenceMap } from "@/components/attendance/absences/TeamAbsenceMap";
import { AbsenceUpsertDrawer } from "@/components/attendance/absences/AbsenceUpsertDrawer";
import { AbsenceDetailDrawer } from "@/components/attendance/absences/AbsenceDetailDrawer";

const STATUS_CLASS: Record<AbsenceStatus, string> = {
  PENDING: "ind-tag-outline",
  APPROVED: "ind-tag-accent",
  REJECTED: "ind-tag-neutral",
};

type View = "map" | "list";
type StatusFilter = AbsenceStatus | "ALL";

/**
 * Férias e ausências da equipa, mês a mês. Duas leituras do mesmo pedido
 * (`/attendance/absences/team?from&to`): o **mapa** para ver quem falta quando, e
 * a **lista** para decidir. Nenhuma das duas volta a pedir dados ao mudar de
 * vista — o filtro de estado e a pesquisa são em memória.
 */
export default function AbsencesPage() {
  const confirm = useConfirm();
  const [month, setMonth] = useState(() => dayjs().format("YYYY-MM"));
  const [view, setView] = useState<View>("map");
  const [status, setStatus] = useState<StatusFilter>("ALL");
  const [query, setQuery] = useState("");
  const [absences, setAbsences] = useState<Absence[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<{ absence: Absence | null } | null>(null);
  const [openDetail, setOpenDetail] = useState<string | null>(null);

  const fetchAbsences = useCallback(async () => {
    setLoading(true);
    const from = `${month}-01`;
    const to = dayjs(from).endOf("month").format("YYYY-MM-DD");
    try {
      // Sem filtro de estado no pedido: o mapa precisa de todas para as pintar.
      setAbsences(await listTeamAbsences(from, to));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [month]);

  useEffect(() => {
    void fetchAbsences();
  }, [fetchAbsences]);

  const pendingCount = absences.filter((absence) => absence.status === "PENDING").length;

  const filtered = useMemo(() => {
    const wanted = query.trim().toLowerCase();
    return absences
      .filter((absence) => status === "ALL" || absence.status === status)
      .filter((absence) => !wanted || absence.profileName.toLowerCase().includes(wanted))
      .sort((a, b) => a.startsOn.localeCompare(b.startsOn) || a.profileName.localeCompare(b.profileName));
  }, [absences, status, query]);

  const askDelete = useCallback(
    (absence: Absence) => {
      confirm({
        message: `Eliminar a ausência de ${absence.profileName} (${ABSENCE_TYPE_LABEL[absence.type]}, ${dayjs(
          absence.startsOn
        ).format("DD/MM")}–${dayjs(absence.endsOn).format("DD/MM")})? Os dias voltam a contar como dias de trabalho.`,
        onConfirm: async () => {
          try {
            await deleteAbsence(absence.id);
            notificationService.success("Ausência eliminada");
            void fetchAbsences();
          } catch (error) {
            ErrorHandler.handle(error);
          }
        },
      });
    },
    [confirm, fetchAbsences]
  );

  const columns = useMemo<ColumnsType<Absence>>(
    () => [
      {
        title: "Funcionário",
        dataIndex: "profileName",
        key: "profileName",
        render: (name: string) => (
          <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{name}</span>
        ),
      },
      {
        title: "Tipo",
        dataIndex: "type",
        key: "type",
        width: 160,
        render: (type: Absence["type"]) => ABSENCE_TYPE_LABEL[type],
      },
      {
        title: "Período",
        key: "period",
        width: 180,
        render: (_, absence) =>
          absence.startsOn === absence.endsOn
            ? dayjs(absence.startsOn).format("DD/MM/YYYY")
            : `${dayjs(absence.startsOn).format("DD/MM")} a ${dayjs(absence.endsOn).format("DD/MM/YYYY")}`,
      },
      {
        title: "Dias úteis",
        dataIndex: "workingDays",
        key: "workingDays",
        width: 100,
        render: (days: number) => <span style={{ fontWeight: 600 }}>{days}</span>,
      },
      {
        title: "Estado",
        dataIndex: "status",
        key: "status",
        width: 120,
        render: (value: AbsenceStatus) => (
          <span className={`ind-tag ${STATUS_CLASS[value]}`}>{ABSENCE_STATUS_LABEL[value]}</span>
        ),
      },
      {
        title: "",
        key: "actions",
        width: 170,
        render: (_, absence) => (
          <ListActions>
            <ListActionPrimary onClick={() => setOpenDetail(absence.id)}>
              {absence.status === "PENDING" ? "Decidir" : "Ver detalhes"}
            </ListActionPrimary>
            <ListActionSecondary onClick={() => setEditing({ absence })}>Editar</ListActionSecondary>
            <ListActionDanger onClick={() => askDelete(absence)}>Eliminar</ListActionDanger>
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
          <h6 style={{ color: "var(--ind-accent-700)" }}>Equipa</h6>
          <h1 style={{ margin: 0 }}>Férias e ausências</h1>
          <p className="ind-card-meta" style={{ marginTop: 4, marginBottom: 0 }}>
            {formatMonth(month)}
            {pendingCount > 0 && ` · ${pendingCount} à espera de decisão`}
          </p>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Button
            size="small"
            icon={<LeftOutlined />}
            aria-label="Mês anterior"
            onClick={() => setMonth(dayjs(`${month}-01`).subtract(1, "month").format("YYYY-MM"))}
          />
          <Button
            size="small"
            icon={<RightOutlined />}
            aria-label="Mês seguinte"
            onClick={() => setMonth(dayjs(`${month}-01`).add(1, "month").format("YYYY-MM"))}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ absence: null })}>
            Registar ausência
          </Button>
        </div>
      </div>

      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: "13.6px", flexWrap: "wrap" }}>
        <Segmented
          value={view}
          onChange={(value) => setView(value as View)}
          options={[
            { label: "Mapa", value: "map" },
            { label: "Lista", value: "list" },
          ]}
        />
        {view === "list" && (
          <Segmented
            value={status}
            onChange={(value) => setStatus(value as StatusFilter)}
            options={[
              { label: "Todas", value: "ALL" },
              {
                label: pendingCount > 0 ? <Badge count={pendingCount} offset={[8, -2]} size="small">Pendentes</Badge> : "Pendentes",
                value: "PENDING",
              },
              { label: "Aprovadas", value: "APPROVED" },
              { label: "Recusadas", value: "REJECTED" },
            ]}
          />
        )}
        <Input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Procurar funcionário"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          style={{ maxWidth: 320 }}
          allowClear
        />
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : view === "map" ? (
        <TeamAbsenceMap
          month={month}
          absences={absences.filter(
            (absence) => !query.trim() || absence.profileName.toLowerCase().includes(query.trim().toLowerCase())
          )}
          onPick={(absence) => setOpenDetail(absence.id)}
        />
      ) : (
        <>
          <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
            <Table
              rowKey="id"
              columns={columns}
              dataSource={filtered}
              pagination={false}
              size="small"
              locale={{
                emptyText: (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nenhuma ausência com este filtro." />
                ),
              }}
            />
          </div>
          <div style={{ marginTop: 16 }}>
            <p style={{ fontSize: 12, opacity: 0.6, margin: 0 }}>{filtered.length} ausência(s)</p>
          </div>
        </>
      )}

      <AbsenceUpsertDrawer
        open={!!editing}
        absence={editing?.absence ?? null}
        defaultDay={`${month}-01`}
        onClose={() => setEditing(null)}
        onSaved={() => void fetchAbsences()}
      />

      <AbsenceDetailDrawer
        absenceId={openDetail}
        onClose={() => setOpenDetail(null)}
        onChanged={() => void fetchAbsences()}
      />
    </div>
  );
}
