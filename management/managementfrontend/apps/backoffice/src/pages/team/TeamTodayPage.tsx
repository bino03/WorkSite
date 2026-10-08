import { useAuth } from "@/hooks/useAuth";

/**
 * O início do espaço Equipa. Provisório: o painel "Hoje" (quem está a trabalhar,
 * faltas, pedidos de férias, mapa) é a tarefa 6 do plano frontend-equipa — só faz
 * sentido depois dos ecrãs que produzem esses dados.
 */
export default function TeamTodayPage() {
  const { userName } = useAuth();

  return (
    <div>
      <span className="ind-card-kicker">Equipa</span>
      <h1 style={{ marginTop: 4 }}>Olá{userName ? `, ${userName}` : ""}</h1>
      <p className="ind-card-meta">O resumo do dia aparece aqui.</p>
    </div>
  );
}
