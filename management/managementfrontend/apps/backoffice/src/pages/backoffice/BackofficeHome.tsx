import { Link } from "react-router-dom";
import { Card, Col, Row, Typography } from "antd";
import { BuildOutlined, TeamOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { useAuthContext } from "@/context/AuthContext";

const { Title, Text } = Typography;

export const BackofficeHome = () => {
  const { t } = useTranslation();
  const { user } = useAuthContext();

  return (
    <div>
      <Title level={3} style={{ marginBottom: 4 }}>
        {t("common.welcome", { defaultValue: "Bem-vindo" })}{user?.name ? `, ${user.name}` : ""}
      </Title>
      <Text type="secondary">{t("common.homeSubtitle", { defaultValue: "Acesso rápido" })}</Text>

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} sm={12} md={8}>
          <Link to="/backoffice/empreendimentos">
            <Card hoverable>
              <BuildOutlined style={{ fontSize: 24, marginBottom: 8 }} />
              <div>{t("nav.enterprises", { defaultValue: "Projetos" })}</div>
            </Card>
          </Link>
        </Col>
        {user?.role === "ADMIN" && (
          <Col xs={24} sm={12} md={8}>
            <Link to="/backoffice/funcionarios">
              <Card hoverable>
                <TeamOutlined style={{ fontSize: 24, marginBottom: 8 }} />
                <div>{t("nav.manageAccounts", { defaultValue: "Equipa" })}</div>
              </Card>
            </Link>
          </Col>
        )}
      </Row>
    </div>
  );
};
