import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert, Button, Result, Spin } from "antd";
import { LoginOutlined, LogoutOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useAuthContext } from "@/context/AuthContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { clockIn, myTodayEntries, previewClock } from "@/services/clockService";
import type { ClockPreview } from "@/services/clockService";
import type { TimeDirection, TimeEntry } from "@/types/attendance";
import { AFTER_LOGIN_KEY } from "./afterLogin";

/**
 * A página que o QR da obra abre no telemóvel do funcionário (`/picar?c=<código>`).
 * Fora do `AppLayout` de propósito: num telemóvel, ao pé da obra, só interessa a obra,
 * o botão e as picagens de hoje.
 *
 * Fluxo: sem sessão → tenta recuperá-la dos cookies (um separador novo vem com o
 * `sessionStorage` vazio) → se não der, guarda o caminho e vai ao login, que volta
 * aqui. Com sessão → mostra "Picar ENTRADA/SAÍDA na <obra>" e um toque confirma.
 * A direção vem do backend e volta a ser conferida lá ao picar.
 */
const DIRECTION_LABEL: Record<TimeDirection, string> = { IN: "Entrada", OUT: "Saída" };

const time = (iso: string) => dayjs(iso).format("HH:mm");

type Phase = "auth" | "loading" | "ready" | "done" | "error";

export default function ClockPage() {
  const { user, restoreSession, logout } = useAuthContext();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const code = params.get("c");

  const [phase, setPhase] = useState<Phase>(user ? "loading" : "auth");
  const [preview, setPreview] = useState<ClockPreview | null>(null);
  const [today, setToday] = useState<TimeEntry[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [last, setLast] = useState<TimeEntry | null>(null);

  // 1) Sessão: recuperar dos cookies, ou ir ao login e voltar.
  useEffect(() => {
    if (user) return;
    let cancelled = false;
    sessionStorage.setItem(AFTER_LOGIN_KEY, `/picar${code ? `?c=${encodeURIComponent(code)}` : ""}`);
    restoreSession().then((restored) => {
      if (cancelled) return;
      if (restored) {
        sessionStorage.removeItem(AFTER_LOGIN_KEY);
        setPhase("loading");
      } else {
        navigate("/login", { replace: true });
      }
    });
    return () => {
      cancelled = true;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // 2) O que vai ser picado, e as picagens de hoje.
  const load = useCallback(async () => {
    setProblem(null);
    try {
      if (code) {
        const result = await previewClock(code);
        setPreview(result);
        setToday(result.today);
      } else {
        setPreview(null);
        setToday(await myTodayEntries());
      }
      setPhase("ready");
    } catch (error) {
      setProblem(ErrorHandler.getMessage(error));
      setPhase("error");
    }
  }, [code]);

  useEffect(() => {
    if (phase === "loading") void load();
  }, [phase, load]);

  const confirm = async () => {
    if (!code || !preview) return;
    setSaving(true);
    setProblem(null);
    try {
      const created = await clockIn(code, preview.nextDirection);
      setLast(created);
      setPhase("done");
      // Volta a carregar o estado: a próxima picagem já é a contrária.
      const refreshed = await previewClock(code);
      setPreview(refreshed);
      setToday(refreshed.today);
    } catch (error) {
      setProblem(ErrorHandler.getMessage(error));
      // Uma página desatualizada (ATT_023) corrige-se a recarregar o estado.
      void load();
    } finally {
      setSaving(false);
    }
  };

  const isEmployeeOrAdmin = user?.role === "EMPLOYEE" || user?.role === "ADMIN";

  return (
    <div
      style={{
        minHeight: "100vh",
        background: "var(--ind-color-bg)",
        color: "var(--ind-color-text)",
        fontFamily: "var(--ind-font-body)",
        display: "flex",
        justifyContent: "center",
        padding: "var(--ind-space-6) var(--ind-space-4)",
      }}
    >
      <div style={{ width: "100%", maxWidth: 460, display: "flex", flexDirection: "column", gap: "var(--ind-space-6)" }}>
        <header style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
          <div>
            <h6 style={{ margin: 0, textTransform: "uppercase", letterSpacing: 1, color: "var(--ind-neutral-600)" }}>
              Worksite · Picar ponto
            </h6>
            <h1 style={{ margin: 0, fontFamily: "var(--ind-font-heading)", fontSize: 28 }}>
              {user?.name ?? "…"}
            </h1>
          </div>
          {user && (
            <Button type="text" icon={<LogoutOutlined />} onClick={() => void logout()} aria-label="Sair">
              Sair
            </Button>
          )}
        </header>

        {(phase === "auth" || phase === "loading") && (
          <div style={{ textAlign: "center", padding: "var(--ind-space-8)" }}>
            <Spin size="large" />
          </div>
        )}

        {user && !isEmployeeOrAdmin && (
          <Alert type="warning" showIcon message="Esta conta não pica o ponto." />
        )}

        {phase === "error" && problem && (
          <Alert
            type="error"
            showIcon
            message={problem}
            action={
              <Button size="small" onClick={() => setPhase("loading")}>
                Tentar de novo
              </Button>
            }
          />
        )}

        {phase === "done" && last && (
          <Result
            status="success"
            title={`${DIRECTION_LABEL[last.direction]} registada às ${time(last.happenedAt)}`}
            subTitle={last.enterpriseName ?? undefined}
            style={{ padding: "var(--ind-space-4) 0" }}
          />
        )}

        {(phase === "ready" || phase === "done") && preview && (
          <section
            style={{
              background: "var(--ind-neutral-100)",
              borderRadius: 12,
              boxShadow: "var(--ind-shadow-md)",
              padding: "var(--ind-space-6)",
              display: "flex",
              flexDirection: "column",
              gap: "var(--ind-space-4)",
            }}
          >
            <div>
              <span className="ind-card-meta" style={{ margin: 0 }}>Obra</span>
              <div style={{ fontFamily: "var(--ind-font-heading)", fontSize: 26, lineHeight: 1.1 }}>
                {preview.enterpriseName}
              </div>
            </div>
            {problem && <Alert type="warning" showIcon message={problem} />}
            <Button
              type="primary"
              size="large"
              block
              loading={saving}
              icon={preview.nextDirection === "IN" ? <LoginOutlined /> : <LogoutOutlined />}
              onClick={() => void confirm()}
              style={{ height: 64, fontSize: 20 }}
            >
              Picar {DIRECTION_LABEL[preview.nextDirection].toUpperCase()}
            </Button>
            <p className="ind-card-meta" style={{ margin: 0, textAlign: "center" }}>
              Fica registada a hora de agora, nesta obra.
            </p>
          </section>
        )}

        {phase === "ready" && !code && (
          <Alert
            type="info"
            showIcon
            message="Para picar, leia o QR da obra com a câmara do telemóvel."
          />
        )}

        {(phase === "ready" || phase === "done") && (
          <section>
            <h6 style={{ margin: "0 0 var(--ind-space-2)", textTransform: "uppercase", letterSpacing: 1 }}>
              Hoje
            </h6>
            {today.length === 0 ? (
              <p className="ind-card-meta" style={{ margin: 0 }}>Ainda não picou hoje.</p>
            ) : (
              <ul style={{ listStyle: "none", margin: 0, padding: 0, display: "flex", flexDirection: "column", gap: 4 }}>
                {today.map((entry) => (
                  <li
                    key={entry.id}
                    style={{
                      display: "grid",
                      gridTemplateColumns: "56px 70px 1fr",
                      gap: "var(--ind-space-3)",
                      padding: "var(--ind-space-2) 0",
                      borderBottom: "1px solid var(--ind-color-divider)",
                    }}
                  >
                    <strong>{time(entry.happenedAt)}</strong>
                    <span>{DIRECTION_LABEL[entry.direction]}</span>
                    <span style={{ color: "var(--ind-neutral-700)" }}>{entry.enterpriseName ?? "Sem obra"}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
