import { useCallback, useEffect, useState } from "react";
import { Button, Empty, Spin, Table } from "antd";
import { PlusOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { parseApiError } from "@/utils/apiError";
import { getEmployment, listWorkSchedules } from "@/services/attendanceService";
import type { Employment, EmploymentTerm, WorkSchedule } from "@/types/attendance";
import { EmploymentCreateDrawer } from "./EmploymentCreateDrawer";
import { EmploymentDatesDrawer } from "./EmploymentDatesDrawer";
import { EmploymentTermDrawer } from "./EmploymentTermDrawer";

interface Props {
  profileId: string;
}

const formatDate = (date: string | null) => (date ? dayjs(date).format("DD/MM/YYYY") : "—");

/**
 * A ficha de emprego de um funcionário, dentro da sua página em Equipa: admissão,
 * fim, e o histórico de períodos (horário + dias de férias por ano). Um funcionário
 * sem ficha não tem horas contadas — daí o estado vazio ser um convite a criá-la.
 *
 * Um `404`/`ATT_001` aqui **não é erro**: é a ficha ainda não existir. Por isso o
 * `catch` olha para o código antes de o entregar ao `ErrorHandler` — passá-lo
 * sempre daria uma notificação de erro em cada funcionário novo.
 */
export function EmploymentCard({ profileId }: Props) {
  const [employment, setEmployment] = useState<Employment | null>(null);
  const [schedules, setSchedules] = useState<WorkSchedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [openDrawer, setOpenDrawer] = useState<"create" | "dates" | "term" | null>(null);

  const fetchEmployment = useCallback(async () => {
    setLoading(true);
    try {
      setEmployment(await getEmployment(profileId));
    } catch (error) {
      if (parseApiError(error)?.errorCode === "ATT_001") {
        setEmployment(null);
      } else {
        ErrorHandler.handle(error);
      }
    } finally {
      setLoading(false);
    }
  }, [profileId]);

  useEffect(() => {
    void fetchEmployment();
  }, [fetchEmployment]);

  // Os horários alimentam os dois drawers que escolhem um; são poucos e mudam raramente.
  useEffect(() => {
    listWorkSchedules().then(setSchedules).catch(ErrorHandler.handle);
  }, []);

  const columns: ColumnsType<EmploymentTerm> = [
    {
      title: "Desde",
      dataIndex: "validFrom",
      key: "validFrom",
      width: 140,
      render: (validFrom: string) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>{formatDate(validFrom)}</span>
      ),
    },
    { title: "Até", dataIndex: "validTo", key: "validTo", width: 140, render: formatDate },
    { title: "Horário", dataIndex: "workScheduleName", key: "workScheduleName" },
    {
      title: "Férias/ano",
      dataIndex: "vacationDaysPerYear",
      key: "vacationDaysPerYear",
      width: 120,
      render: (days: number) => `${days} dias`,
    },
    {
      title: "",
      key: "current",
      width: 110,
      render: (_, term) => (term.current ? <span className="ind-tag ind-tag-accent">em vigor</span> : null),
    },
  ];

  return (
    <div className="ind-card" style={{ marginTop: "20.4px", padding: "20.4px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "13.6px" }}>
        <div>
          <span className="ind-card-kicker">Emprego</span>
          <h2 style={{ margin: 0 }}>Ficha de emprego</h2>
          {employment && (
            <p className="ind-card-meta" style={{ marginTop: 4, marginBottom: 0 }}>
              Admissão {formatDate(employment.hiredAt)}
              {employment.endedAt ? ` · saiu a ${formatDate(employment.endedAt)}` : " · ao serviço"}
            </p>
          )}
        </div>
        {employment && (
          <div style={{ display: "flex", gap: 8 }}>
            <Button size="small" onClick={() => setOpenDrawer("dates")}>
              Editar datas
            </Button>
            <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => setOpenDrawer("term")}>
              Novo período
            </Button>
          </div>
        )}
      </div>

      {loading ? (
        <div style={{ padding: "27.2px", textAlign: "center" }}>
          <Spin />
        </div>
      ) : employment ? (
        <div style={{ borderTop: "1px solid var(--ind-color-divider)" }}>
          <Table rowKey="id" columns={columns} dataSource={employment.terms} pagination={false} size="small" />
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Sem ficha de emprego: as horas, as faltas e o saldo de férias deste funcionário ainda não são contados."
        >
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpenDrawer("create")}>
            Criar ficha
          </Button>
        </Empty>
      )}

      <EmploymentCreateDrawer
        open={openDrawer === "create"}
        profileId={profileId}
        schedules={schedules}
        onClose={() => setOpenDrawer(null)}
        onSaved={() => void fetchEmployment()}
      />

      {employment && (
        <>
          <EmploymentDatesDrawer
            open={openDrawer === "dates"}
            employment={employment}
            onClose={() => setOpenDrawer(null)}
            onSaved={() => void fetchEmployment()}
          />
          <EmploymentTermDrawer
            open={openDrawer === "term"}
            employment={employment}
            schedules={schedules}
            onClose={() => setOpenDrawer(null)}
            onSaved={() => void fetchEmployment()}
          />
        </>
      )}
    </div>
  );
}
