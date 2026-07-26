import { Form, Input, Button, message, Checkbox } from "antd";
import { useNavigate, useLocation } from "react-router-dom";
import { useState, useEffect } from "react";
import { EyeOutlined, EyeInvisibleOutlined, BuildOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

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
    <div className="flex h-screen w-screen overflow-hidden" style={{ backgroundColor: "#f5f4ed" }}>
      {/* ── Left panel: brand ── */}
      <div
        className="relative hidden md:flex md:w-1/2 flex-col justify-between overflow-hidden p-10"
        style={{
          borderRadius: "0 24px 24px 0",
          background: "linear-gradient(135deg, #30302e 0%, #4d4c48 100%)",
        }}
      >
        <div
          className="inline-flex items-center gap-2 self-start px-4 py-1.5 text-sm font-medium"
          style={{
            borderRadius: "24px",
            backgroundColor: "rgba(245,244,237,0.18)",
            backdropFilter: "blur(8px)",
            color: "#faf9f5",
            border: "1px solid rgba(245,244,237,0.25)",
          }}
        >
          {t('auth.title')}
        </div>

        <div className="space-y-4">
          <BuildOutlined style={{ fontSize: 40, color: "#c96442" }} />
          <h1
            style={{
              fontFamily: "Georgia, serif",
              fontWeight: 500,
              fontSize: "2.25rem",
              lineHeight: 1.2,
              color: "#faf9f5",
            }}
          >
            Worksite
          </h1>
          <p style={{ fontSize: "1.06rem", lineHeight: 1.6, color: "rgba(250,249,245,0.80)", maxWidth: "24rem" }}>
            {t('auth.subtitle')}
          </p>
        </div>
      </div>

      {/* ── Right panel: login form ── */}
      <div
        className="flex flex-1 flex-col items-center justify-center px-8"
        style={{ backgroundColor: "#f5f4ed" }}
      >
        <div className="w-full max-w-sm space-y-8">
          {/* Logo / title */}
          <div className="text-center space-y-2">
            <h1
              style={{
                fontFamily: "Georgia, serif",
                fontWeight: 500,
                fontSize: "2rem",
                lineHeight: 1.1,
                color: "#141413",
                margin: 0,
              }}
            >
              Worksite
            </h1>
            <p style={{ fontSize: "0.75rem", letterSpacing: "0.12em", textTransform: "uppercase", color: "#87867f" }}>
              {t('auth.subtitle')}
            </p>
          </div>

          {/* Form card */}
          <div
            className="p-8 rounded-2xl"
            style={{
              backgroundColor: "#faf9f5",
              border: "1px solid #f0eee6",
              boxShadow: "rgba(0,0,0,0.05) 0px 4px 24px",
            }}
          >
            <Form layout="vertical" onFinish={onFinish} size="large">
              <Form.Item
                label={<span style={{ color: "#4d4c48", fontWeight: 500, fontSize: "14px" }}>{t('auth.email')}</span>}
                name="email"
                rules={[{ required: true, message: t('auth.emailRequired') }]}
              >
                <Input placeholder="exemplo@email.com" />
              </Form.Item>

              <Form.Item
                label={<span style={{ color: "#4d4c48", fontWeight: 500, fontSize: "14px" }}>{t('auth.password')}</span>}
                name="password"
                rules={[{ required: true, message: t('auth.emailRequired') }]}
              >
                <Input.Password
                  placeholder="••••••••"
                  iconRender={(visible) =>
                    visible ? (
                      <EyeOutlined style={{ cursor: "pointer", color: "#8a8984" }} />
                    ) : (
                      <EyeInvisibleOutlined style={{ cursor: "pointer", color: "#8a8984" }} />
                    )
                  }
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Checkbox
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                >
                  <span style={{ color: "#5e5d59", fontSize: "14px" }}>{t('auth.rememberMe')}</span>
                </Checkbox>
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Button
                  htmlType="submit"
                  className="w-full"
                  style={{
                    backgroundColor: "#c96442",
                    borderColor: "#c96442",
                    color: "#faf9f5",
                    borderRadius: "8px",
                    height: "40px",
                    fontWeight: 500,
                  }}
                >
                  {t('auth.login')}
                </Button>
              </Form.Item>
            </Form>
          </div>

          <div className="text-center">
            <a
              href="#"
              style={{ fontSize: "0.875rem", color: "#c96442" }}
              onClick={(e) => { e.preventDefault(); navigate("/forgot-password"); }}
            >
              {t('auth.forgotPassword')}
            </a>
          </div>
        </div>
      </div>

    </div>
  );
};
