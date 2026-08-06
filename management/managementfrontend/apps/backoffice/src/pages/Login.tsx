import { Form, Input, Button, message, Checkbox } from "antd";
import { useNavigate, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import { EyeOutlined, EyeInvisibleOutlined, BuildOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import BlueprintCard from "@/components/common/BlueprintCard";

export const Login = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const [rememberMe, setRememberMe] = useState(false);

  useEffect(() => {
    const error = (location.state as { error?: string })?.error;
    if (error) {
      message.error(error);
      // limpa o state para não repetir na próxima navegação
      navigate("/login", { replace: true, state: {} });
    }
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const onFinish = (values: { email: string; password: string }) => {
    navigate("/loading", { state: { email: values.email, password: values.password } });
  };

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", minHeight: "100vh" }} className="login-grid">
      {/* ── Left panel: brand ── */}
      <div
        style={{
          position: "relative",
          background: "var(--ind-accent-900)",
          color: "var(--ind-neutral-100)",
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          padding: "27.2px",
          gap: "13.6px",
        }}
        className="login-brand-panel"
      >
        <div
          className="ind-blueprint"
          style={{
            position: "absolute",
            inset: "27.2px",
            border: "1px solid color-mix(in srgb, #fff 20%, transparent)",
          }}
        >
          <i className="ind-corner tl" style={{ color: "rgba(255,255,255,0.5)" }} />
          <i className="ind-corner tr" style={{ color: "rgba(255,255,255,0.5)" }} />
          <i className="ind-corner bl" style={{ color: "rgba(255,255,255,0.5)" }} />
          <i className="ind-corner br" style={{ color: "rgba(255,255,255,0.5)" }} />
        </div>
        <span
          className="ind-tag ind-tag-outline"
          style={{ width: "fit-content", borderColor: "color-mix(in srgb, #fff 45%, transparent)", color: "var(--ind-neutral-100)" }}
        >
          {t("auth.title")}
        </span>
        <div style={{ display: "flex", alignItems: "center", gap: "6.8px" }}>
          <BuildOutlined style={{ fontSize: 28 }} />
          <h1 style={{ margin: 0, fontSize: 36 }}>Worksite</h1>
        </div>
        <p style={{ margin: 0, maxWidth: 360, opacity: 0.8, fontSize: 15 }}>{t("auth.subtitle")}</p>
      </div>

      {/* ── Right panel: login form ── */}
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: "27.2px" }}>
        <div style={{ width: "100%", maxWidth: 380 }}>
          <div style={{ marginBottom: "20.4px" }}>
            <h6 style={{ color: "var(--ind-accent-700)" }}>Worksite</h6>
            <h2 style={{ margin: 0 }}>{t("auth.login")}</h2>
          </div>

          <BlueprintCard elevation="sm" style={{ padding: "20.4px", gap: "13.6px" }}>
            <Form layout="vertical" onFinish={onFinish} size="large">
              <Form.Item
                label={t("auth.email")}
                name="email"
                rules={[{ required: true, message: t("auth.emailRequired") }]}
              >
                <Input placeholder="nome@worksite.pt" />
              </Form.Item>

              <Form.Item
                label={t("auth.password")}
                name="password"
                rules={[{ required: true, message: t("auth.emailRequired") }]}
              >
                <Input.Password
                  placeholder="••••••••"
                  iconRender={(visible) => (visible ? <EyeOutlined /> : <EyeInvisibleOutlined />)}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Checkbox checked={rememberMe} onChange={(e) => setRememberMe(e.target.checked)}>
                  {t("auth.rememberMe")}
                </Checkbox>
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Button htmlType="submit" type="primary" block>
                  {t("auth.login")}
                </Button>
              </Form.Item>
            </Form>
          </BlueprintCard>

          <div style={{ textAlign: "center", marginTop: "10.2px" }}>
            <a
              href="#"
              style={{ fontSize: 13 }}
              onClick={(e) => {
                e.preventDefault();
                navigate("/forgot-password");
              }}
            >
              {t("auth.forgotPassword")}
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
