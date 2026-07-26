// src/pages/employees/EmployeeProfilePage.tsx

import { useParams } from "react-router-dom";
import ProfileView from "@/components/profile/ProfileView";
import { App } from "antd";

export default function EmployeeProfilePage() {
  const { id } = useParams<{ id: string }>();
  return (
    // garantir que App (message, modal) está disponível
    <App>
      <ProfileView profileId={id!} mode="page" />
    </App>
  );
}
