import { useParams } from "react-router-dom";
import ProfileView from "@/components/profile/ProfileView";
import { App } from "antd";
import { EmploymentCard } from "@/components/attendance/employment/EmploymentCard";
import { AttendanceMonthCard } from "@/components/attendance/timeentries/AttendanceMonthCard";

/**
 * A página de um funcionário no espaço Equipa (`/team/employees/:id`): a conta
 * (`ProfileView`) e, por baixo, a **ficha de emprego**. A ficha fica aqui e não
 * dentro do `ProfileView` porque esse componente também serve a vista rápida da
 * lista e a própria conta do utilizador, onde a ficha não tem lugar.
 */
export default function EmployeeProfilePage() {
  const { id } = useParams<{ id: string }>();
  return (
    // garantir que App (message, modal) está disponível
    <App>
      <ProfileView profileId={id!} mode="page" />
      <EmploymentCard profileId={id!} />
      <AttendanceMonthCard profileId={id!} />
    </App>
  );
}
