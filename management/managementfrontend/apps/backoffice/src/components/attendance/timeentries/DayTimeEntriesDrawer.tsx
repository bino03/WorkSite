import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Empty, Segmented, Spin, Table } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import {
  listDeletedTimeEntries,
  listTimeEntriesForDay,
  restoreTimeEntry,
  voidTimeEntry,
} from "@/services/attendanceService";
import type { TimeEntry } from "@/types/attendance";
import { formatLongDate, formatMinutes } from "@/utils/formatters";
import { ListActions, ListActionPrimary, ListActionSecondary, ListActionDanger } from "@/components/common/ListActions";
import { TimeEntryUpsertDrawer } from "./TimeEntryUpsertDrawer";
import { TimeEntryReasonModal } from "./TimeEntryReasonModal";
import { TimeEntryRevisionsModal } from "./TimeEntryRevisionsModal";

interface Props {
  open: boolean;
  profileId: string;
  profileName: string;
  /** O dia em Lisboa, `YYYY-MM-DD`. */
  day: string;
  onClose: () => void;
  /** Chamado depois de qualquer escrita, para a vista de trás recarregar. */
  onChanged: () => void;
}

type View = "active" | "voided";

const hour = (entry: TimeEntry) => dayjs(entry.happenedAt).format("HH:mm");
const directionLabel = (entry: TimeEntry) => (entry.direction === "IN" ? "Entrada" : "Saída");
const describe = (entry: TimeEntry) =>
  `${hour(entry)} · ${directionLabel(entry)}${entry.enterpriseName ? ` · ${entry.enterpriseName}` : ""}`;

/**
 * As picagens de um funcionário num dia: a sequência em cru, e tudo o que se lhe
 * pode fazer. É a peça partilhada entre a lista da equipa (`TimeEntriesPage`) e o
 * mês na página do funcionário — as duas abrem este mesmo drawer.
 *
 * As anuladas vêm de `/deleted`, que é **por funcionário e não por dia**, e por
 * isso são filtradas aqui pelo `localDate`.
 */
export function DayTimeEntriesDrawer({ open, profileId, profileName, day, onClose, onChanged }: Props) {
  const [view, setView] = useState<View>("active");
  const [entries, setEntries] = useState<TimeEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState<{ entry: TimeEntry | null } | null>(null);
  const [reasonFor, setReasonFor] = useState<{ entry: TimeEntry; action: "void" | "restore" } | null>(null);
  const [revisionsFor, setRevisionsFor] = useState<string | null>(null);

  const fetchEntries = useCallback(async () => {
    setLoading(true);
    try {
      if (view === "active") {
        setEntries(await listTimeEntriesForDay(profileId, day));
      } else {
        const deleted = await listDeletedTimeEntries(profileId);
        setEntries(deleted.filter((entry) => entry.localDate === day));
      }
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, [profileId, day, view]);

  useEffect(() => {
    if (open) void fetchEntries();
  }, [open, fetchEntries]);

  // A sequência do dia só fecha em pares; um número ímpar é um dia por acabar.
  const worked = entries
    .filter((entry) => !entry.deletedAt)
    .reduce((total, entry, index, all) => {
      if (entry.direction !== "IN") return total;
      const next = all[index + 1];
      if (!next || next.direction !== "OUT") return total;
      return total + dayjs(next.happenedAt).diff(dayjs(entry.happenedAt), "minute");
    }, 0);
  const incomplete = view === "active" && entries.length % 2 !== 0;

  const afterWrite = () => {
    void fetchEntries();
    onChanged();
  };

  const confirmReason = async (reason: string) => {
    if (!reasonFor) return;
    try {
      if (reasonFor.action === "void") {
        await voidTimeEntry(reasonFor.entry.id, reason);
        notificationService.success("Picagem anulada");
      } else {
        await restoreTimeEntry(reasonFor.entry.id, reason);
        notificationService.success("Picagem restaurada");
      }
      setReasonFor(null);
      afterWrite();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const columns: ColumnsType<TimeEntry> = [
    {
      title: "Hora",
      key: "time",
      width: 90,
      render: (_, entry) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{hour(entry)}</span>
      ),
    },
    {
      title: "Sentido",
      key: "direction",
      width: 110,
      render: (_, entry) => (
        <span className={`ind-tag ${entry.direction === "IN" ? "ind-tag-accent" : "ind-tag-neutral"}`}>
          {directionLabel(entry)}
        </span>
      ),
    },
    {
      title: "Obra",
      dataIndex: "enterpriseName",
      key: "enterpriseName",
      render: (name: string | null) => name ?? "—",
    },
    { title: "Nota", dataIndex: "note", key: "note", render: (note: string | null) => note ?? "—" },
    {
      title: "Registada por",
      dataIndex: "registeredByName",
      key: "registeredByName",
      width: 150,
      render: (name: string | null) => name ?? "—",
    },
    {
      title: "",
      key: "actions",
      width: 170,
      render: (_, entry) => (
        <ListActions>
          {view === "active" ? (
            <>
              <ListActionSecondary onClick={() => setEditing({ entry })}>Corrigir</ListActionSecondary>
              <ListActionDanger onClick={() => setReasonFor({ entry, action: "void" })}>Anular</ListActionDanger>
            </>
          ) : (
            <ListActionPrimary onClick={() => setReasonFor({ entry, action: "restore" })}>
              Restaurar
            </ListActionPrimary>
          )}
          <ListActionSecondary onClick={() => setRevisionsFor(entry.id)}>Histórico</ListActionSecondary>
        </ListActions>
      ),
    },
  ];

  return (
    <Drawer
      title={`${profileName} · ${formatLongDate(day)}`}
      open={open}
      onClose={onClose}
      width={900}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing({ entry: null })}>
          Registar picagem
        </Button>
      }
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "13.6px" }}>
        <Segmented
          value={view}
          onChange={(value) => setView(value as View)}
          options={[
            { label: "Picagens", value: "active" },
            { label: "Anuladas", value: "voided" },
          ]}
        />
        {view === "active" && (
          <p className="ind-card-meta" style={{ margin: 0 }}>
            {entries.length} picagem(ns) · <strong>{formatMinutes(worked)}</strong>
            {incomplete && " · falta fechar o dia"}
          </p>
        )}
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table
            rowKey="id"
            columns={columns}
            dataSource={entries}
            pagination={false}
            size="small"
            locale={{
              emptyText: (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={view === "active" ? "Nenhuma picagem neste dia." : "Nenhuma picagem anulada neste dia."}
                />
              ),
            }}
          />
        </div>
      )}

      <TimeEntryUpsertDrawer
        open={!!editing}
        profileId={profileId}
        day={day}
        entry={editing?.entry ?? null}
        onClose={() => setEditing(null)}
        onSaved={afterWrite}
      />

      <TimeEntryReasonModal
        open={!!reasonFor}
        action={reasonFor?.action ?? "void"}
        description={reasonFor ? describe(reasonFor.entry) : ""}
        onCancel={() => setReasonFor(null)}
        onConfirm={confirmReason}
      />

      <TimeEntryRevisionsModal
        open={!!revisionsFor}
        entryId={revisionsFor}
        onClose={() => setRevisionsFor(null)}
      />
    </Drawer>
  );
}
