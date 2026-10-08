import { Navigate, useParams } from "react-router-dom";

/**
 * `/backoffice/funcionarios/:id` → `/team/employees/:id`. A gestão de contas mudou-se
 * para o espaço Equipa a 2026-10-07; os links antigos (favoritos, notificações)
 * continuam a abrir o perfil certo.
 */
export default function LegacyEmployeeRedirect() {
  const { id } = useParams();
  return <Navigate to={`/team/employees/${id}`} replace />;
}
