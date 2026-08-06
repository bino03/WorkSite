import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { BuildOutlined, CheckSquareOutlined, TeamOutlined } from "@ant-design/icons";
import { useAuthContext } from "@/context/AuthContext";
import { useAuth } from "@/hooks/useAuth";
import { taskService } from "@/services/taskService";
import api from "@/api";
import BlueprintCard from "@/components/common/BlueprintCard";

interface Metric {
  label: string;
  value: string;
  meta: string;
}

export const BackofficeHome = () => {
  const { t } = useTranslation();
  const { user } = useAuthContext();
  const { isAdmin, profileId } = useAuth();
  const [metrics, setMetrics] = useState<Metric[]>([]);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      const admin = isAdmin();
      const assigneeId = admin ? null : profileId;
      const today = dayjs().startOf("day");
      const weekFromNow = today.add(7, "day");

      try {
        const [pendingRes, inProgressRes, enterprisesRes] = await Promise.all([
          taskService.list({ q: "", status: "PENDING", enterpriseId: null, assigneeId, page: 0, size: 100 }),
          taskService.list({ q: "", status: "IN_PROGRESS", enterpriseId: null, assigneeId, page: 0, size: 100 }),
          api.get<{ content: { status: string }[] }>("/enterprises", { params: { page: 0, size: 200 } }),
        ]);

        const dueSoonCount = pendingRes.content.filter(
          (task) => !dayjs(task.dueDate).isBefore(today) && dayjs(task.dueDate).isBefore(weekFromNow)
        ).length;
        const overdueCount = [...pendingRes.content, ...inProgressRes.content].filter((task) =>
          dayjs(task.dueDate).isBefore(today)
        ).length;

        const enterpriseStatuses = enterprisesRes.data.content ?? [];
        const activeCount = enterpriseStatuses.filter(
          (e) => e.status === "planning" || e.status === "under_construction"
        ).length;
        const planningCount = enterpriseStatuses.filter((e) => e.status === "planning").length;

        const nextMetrics: Metric[] = [
          { label: "Tarefas pendentes", value: String(pendingRes.page.totalElements), meta: `${dueSoonCount} vencem esta semana` },
          { label: "Tarefas atrasadas", value: String(overdueCount), meta: "requer atenção" },
          { label: "Projetos ativos", value: String(activeCount), meta: `${planningCount} em planeamento` },
        ];

        if (admin) {
          const [teamRes, adminRes] = await Promise.all([
            api.get<{ totalElements: number }>("/employees", { params: { page: 0, size: 1 } }),
            api.get<{ totalElements: number }>("/employees", { params: { page: 0, size: 1, role: "ADMIN" } }),
          ]);
          nextMetrics.push({
            label: "Membros da equipa",
            value: String(teamRes.data.totalElements),
            meta: `${adminRes.data.totalElements} administradores`,
          });
        }

        if (!cancelled) setMetrics(nextMetrics);
      } catch {
        if (!cancelled) setMetrics([]);
      }
    }

    load();
    return () => { cancelled = true; };
  }, [isAdmin, profileId]);

  return (
    <div>
      <h6 style={{ color: "var(--ind-accent-700)" }}>Acesso rápido</h6>
      <h1 style={{ margin: "0 0 20.4px" }}>
        {t("common.welcome", { defaultValue: "Bem-vindo" })}{user?.name ? `, ${user.name}` : ""}
      </h1>

      <div style={{ display: "grid", gridTemplateColumns: `repeat(${metrics.length || 3}, 1fr)`, gap: "13.6px", marginBottom: "27.2px" }}>
        {metrics.map((m) => (
          <BlueprintCard key={m.label} style={{ padding: "13.6px" }}>
            <span className="ind-card-kicker">{m.label}</span>
            <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 34, lineHeight: 1 }}>{m.value}</div>
            <span className="ind-card-meta">{m.meta}</span>
          </BlueprintCard>
        ))}
      </div>

      <h6 style={{ color: "var(--ind-accent-700)" }}>Navegação</h6>
      <div style={{ display: "grid", gridTemplateColumns: isAdmin() ? "repeat(3, 1fr)" : "repeat(2, 1fr)", gap: "13.6px" }}>
        <Link to="/backoffice/empreendimentos" style={{ color: "inherit" }}>
          <BlueprintCard style={{ padding: "13.6px" }}>
            <BuildOutlined style={{ fontSize: 22, color: "var(--ind-color-accent)" }} />
            <span className="ind-card-title">Empreendimentos</span>
            <p className="ind-card-body">Consultar e gerir projetos em curso.</p>
          </BlueprintCard>
        </Link>
        <Link to="/backoffice/tasks" style={{ color: "inherit" }}>
          <BlueprintCard style={{ padding: "13.6px" }}>
            <CheckSquareOutlined style={{ fontSize: 22, color: "var(--ind-color-accent)" }} />
            <span className="ind-card-title">{isAdmin() ? "Tarefas" : "Minhas Tarefas"}</span>
            <p className="ind-card-body">Consultar e gerir tarefas atribuídas.</p>
          </BlueprintCard>
        </Link>
        {isAdmin() && (
          <Link to="/backoffice/funcionarios" style={{ color: "inherit" }}>
            <BlueprintCard style={{ padding: "13.6px" }}>
              <TeamOutlined style={{ fontSize: 22, color: "var(--ind-color-accent)" }} />
              <span className="ind-card-title">Equipa</span>
              <p className="ind-card-body">Gerir membros e permissões.</p>
            </BlueprintCard>
          </Link>
        )}
      </div>
    </div>
  );
};
