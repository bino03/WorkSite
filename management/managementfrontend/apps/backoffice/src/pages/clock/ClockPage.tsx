import { useCallback, useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Alert, Button, Result, Spin } from "antd";
import { HomeOutlined, LoginOutlined, LogoutOutlined, QrcodeOutlined } from "@ant-design/icons";
import dayjs from "dayjs";
import { useAuthContext } from "@/context/AuthContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { clockIn, myTodayEntries, previewClock } from "@/services/clockService";
import type { ClockPreview } from "@/services/clockService";
import type { TimeDirection, TimeEntry } from "@/types/attendance";
import { QrScanner } from "@/components/attendance/clock/QrScanner";
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
  // `?ler=1` vem do botão "Picar ponto" do ecrã inicial: abre logo a câmara.
  const [scanning, setScanning] = useState(!code && params.get("ler") === "1");

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
    sessionStorage.setItem(AFTER_LOGIN_KEY, `/picar${window.location.search}`);
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

  // Um QR lido dentro da app muda o `?c=` sem recarregar a página: voltar a carregar.
  useEffect(() => {
    if (user) setPhase("loading");
  }, [code]); // eslint-disable-line react-hooks/exhaustive-deps

  /** O QR traz `<app>/picar?c=<código>`; tudo o que não seja isso não é um QR de picagem. */
  const onScanned = useCallback(
    (text: string) => {
      setScanning(false);
      let scannedCode: string | null = null;
      try {
        const url = new URL(text);
        if (url.pathname.endsWith("/picar")) scannedCode = url.searchParams.get("c");
      } catch {
        scannedCode = null;
      }
      if (!scannedCode) {
        setProblem("Este QR não é um QR de picagem da obra.");
        setPhase("error");
        return;
      }
      navigate(`/picar?c=${encodeURIComponent(scannedCode)}`, { replace: true });
    },
    [navigate]
  );

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
            <div style={{ display: "flex", gap: 4 }}>
              <Button type="text" icon={<HomeOutlined />} onClick={() => navigate("/backoffice")} aria-label="Início">
                Início
              </Button>
              <Button type="text" icon={<LogoutOutlined />} onClick={() => void logout()} aria-label="Sair">
                Sair
              </Button>
            </div>
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

        {scanning && <QrScanner onDetected={onScanned} onCancel={() => setScanning(false)} />}

        {/* Sem QR lido: o botão que abre a câmara. Também depois de picar, para a obra
            seguinte (manhã numa, tarde noutra). */}
        {!scanning && user && isEmployeeOrAdmin && (phase === "ready" || phase === "done" || phase === "error") && (
          <Button
            type={code ? "default" : "primary"}
            size="large"
            block
            icon={<QrcodeOutlined />}
            onClick={() => {
              setProblem(null);
              setScanning(true);
            }}
            style={code ? undefined : { height: 64, fontSize: 20 }}
          >
            {code ? "Ler o QR de outra obra" : "Ler QR da obra"}
          </Button>
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
