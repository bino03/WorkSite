// src/components/EmployeeMiniCard.tsx
import { useEffect, useState } from "react";
import { Card, Avatar, Tag, Typography, Skeleton } from "antd";
import { UserOutlined, MailOutlined, SafetyCertificateOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import api from "@/api";
import defaultProfile from "@/assets/images/profile/profile.jpg";

const { Text } = Typography;

interface Employee {
  id: string;
  authUserId: string;
  name: string;
  email: string;
  phoneNumber?: string;
  photoUrl?: string | null;
  role: "EMPLOYEE" | "ADMIN";
  status: "unlocked" | "locked";
  createdAt: string;
  updatedAt: string;
}

interface EmployeeMiniCardProps {
  employeeId: string;
  className?: string;
}

export default function EmployeeMiniCard({ employeeId, className }: EmployeeMiniCardProps) {
  const { t } = useTranslation();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchEmployee = async () => {
      try {
        setLoading(true);
        setError(null);
        const response = await api.get(`/employees/${employeeId}`);
        setEmployee(response.data);
      } catch (err) {
        console.error("Erro ao carregar funcionário:", err);
        setError(t('employeeMiniCard.loadError'));
      } finally {
        setLoading(false);
      }
    };

    if (employeeId) {
      fetchEmployee();
    }
  }, [employeeId, t]);

  const getRoleLabel = (role: string) => {
    switch (role) {
      case "ADMIN": return t('profile.roles.admin');
      case "EMPLOYEE": return t('profile.roles.employee');
      default: return role;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case "unlocked": return "green";
      case "locked": return "red";
      default: return "default";
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case "unlocked": return t('employeeMiniCard.statusActive');
      case "locked": return t('employeeMiniCard.statusBlocked');
      default: return status;
    }
  };

  if (loading) {
    return (
      <Card className={`min-w-64 ${className}`} size="small">
        <div className="flex items-center gap-3">
          <Skeleton.Avatar active size="default" />
          <div className="flex-1">
            <Skeleton.Input active size="small" block className="mb-2" />
            <Skeleton.Input active size="small" block />
          </div>
        </div>
      </Card>
    );
  }

  if (error || !employee) {
    return (
      <Card className={`min-w-64 ${className}`} size="small">
        <div className="flex items-center gap-3 text-gray-400">
          <Avatar size="default" icon={<UserOutlined />} />
          <div>
            <Text type="secondary">{t('employeeMiniCard.notFound')}</Text>
          </div>
        </div>
      </Card>
    );
  }

  return (
    <Card
      className={`min-w-64 max-w-80 ${className}`}
      size="small"
      bodyStyle={{ padding: "12px" }}
    >
      <div className="flex items-start gap-3">
        <Avatar
          size={48}
          src={employee.photoUrl ? employee.photoUrl : defaultProfile}
          icon={!employee.photoUrl && <UserOutlined />}
          className="flex-shrink-0"
        />

        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <Text strong className="text-sm truncate" title={employee.name}>
              {employee.name}
            </Text>
          </div>

          <div className="flex items-center gap-1 mb-2">
            <MailOutlined className="text-gray-400 text-xs" />
            <Text type="secondary" className="text-xs truncate" title={employee.email}>
              {employee.email}
            </Text>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            <Tag
              color={employee.role === "ADMIN" ? "blue" : "default"}
              icon={<SafetyCertificateOutlined />}
              className="text-xs m-0"
            >
              {getRoleLabel(employee.role)}
            </Tag>

            <Tag
              color={getStatusColor(employee.status)}
              className="text-xs m-0"
            >
              {getStatusLabel(employee.status)}
            </Tag>
          </div>
        </div>
      </div>
    </Card>
  );
}
