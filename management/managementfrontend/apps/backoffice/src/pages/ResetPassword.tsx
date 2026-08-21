import { Form, Input, Button } from "antd";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useState } from "react";
import {
  ArrowLeftOutlined,
  BuildOutlined,
  CheckCircleOutlined,
  LockOutlined,
  WarningOutlined,
} from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { resetPassword } from "@/services/authService";
import { ErrorHandler } from "@/errors/errorHandler";

/**
 * Definir a password nova a partir do link do email.
 *
 * Gémea visual da `ForgotPassword` de propósito: são dois passos do mesmo fluxo, e
 * quem chega aqui vem de lá — mudar de linguagem visual a meio faria duvidar de que
 * o link é legítimo.
 *
 * Não faz login no fim. A password acabada de definir é usada uma vez à entrada, o
 * que confirma a quem a definiu que ficou mesmo aquela.
 */
export const ResetPassword = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const token = searchParams.get("token");

  const onFinish = async (values: { password: string }) => {
    if (!token) return;

    setSubmitting(true);
    try {
      await resetPassword(token, values.password);
      setDone(true);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSubmitting(false);
    }
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
          {t("auth.title")}
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
            {t("auth.subtitle")}
          </p>
        </div>
      </div>

      {/* ── Right panel ── */}
      <div className="flex flex-1 flex-col items-center justify-center px-8" style={{ backgroundColor: "#f5f4ed" }}>
        <div className="w-full max-w-sm space-y-8">
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
              {t("auth.subtitle")}
            </p>
          </div>

          <div
            className="p-8 rounded-2xl"
            style={{
              backgroundColor: "#faf9f5",
              border: "1px solid #f0eee6",
              boxShadow: "rgba(0,0,0,0.05) 0px 4px 24px",
            }}
          >
            {!token ? (
              /* Link sem token — não vale a pena mostrar o formulário. */
              <div className="text-center space-y-4">
                <div
                  className="flex items-center justify-center w-12 h-12 mx-auto rounded-full"
                  style={{ backgroundColor: "rgba(201,100,66,0.10)" }}
                >
                  <WarningOutlined style={{ fontSize: 24, color: "#c96442" }} />
                </div>
                <div>
                  <h2 style={HEADING}>{t("resetPassword.invalidLink")}</h2>
                  <p style={{ fontSize: "0.875rem", color: "#87867f", lineHeight: 1.6, margin: 0 }}>
                    {t("resetPassword.invalidLinkDesc")}
                  </p>
                </div>
                <Button className="w-full" onClick={() => navigate("/forgot-password")} style={SECONDARY_BUTTON}>
                  {t("resetPassword.requestNew")}
                </Button>
              </div>
            ) : !done ? (
              <>
                <div className="mb-6">
                  <h2 style={HEADING}>{t("resetPassword.title")}</h2>
                  <p style={{ fontSize: "0.875rem", color: "#87867f", lineHeight: 1.5, margin: 0 }}>
                    {t("resetPassword.instructions")}
                  </p>
                </div>

                <Form layout="vertical" onFinish={onFinish} size="large">
                  <Form.Item
                    label={<span style={LABEL}>{t("resetPassword.newPassword")}</span>}
                    name="password"
                    rules={[
                      { required: true, message: t("resetPassword.passwordRequired") },
                      { min: 8, message: t("resetPassword.passwordMinLength") },
                    ]}
                  >
                    <Input.Password
                      placeholder={t("resetPassword.passwordHint")}
                      prefix={<LockOutlined style={{ color: "#b0aea5" }} />}
                    />
                  </Form.Item>

                  <Form.Item
                    label={<span style={LABEL}>{t("resetPassword.confirmPassword")}</span>}
                    name="confirmPassword"
                    dependencies={["password"]}
                    rules={[
                      { required: true, message: t("resetPassword.confirmPasswordRequired") },
                      ({ getFieldValue }) => ({
                        validator(_, value) {
                          if (!value || getFieldValue("password") === value) return Promise.resolve();
                          return Promise.reject(new Error(t("resetPassword.passwordsNoMatch")));
                        },
                      }),
                    ]}
                  >
                    <Input.Password
                      placeholder={t("resetPassword.confirmPasswordPlaceholder")}
                      prefix={<LockOutlined style={{ color: "#b0aea5" }} />}
                    />
                  </Form.Item>

                  <Form.Item style={{ marginBottom: 0 }}>
                    <Button htmlType="submit" loading={submitting} className="w-full" style={PRIMARY_BUTTON}>
                      {t("resetPassword.submit")}
                    </Button>
                  </Form.Item>
                </Form>
              </>
            ) : (
              <div className="text-center space-y-4">
                <div
                  className="flex items-center justify-center w-12 h-12 mx-auto rounded-full"
                  style={{ backgroundColor: "rgba(201,100,66,0.10)" }}
                >
                  <CheckCircleOutlined style={{ fontSize: 24, color: "#c96442" }} />
                </div>
                <div>
                  <h2 style={HEADING}>{t("resetPassword.success")}</h2>
                  <p style={{ fontSize: "0.875rem", color: "#87867f", lineHeight: 1.6, margin: 0 }}>
                    {t("resetPassword.successDesc")}
                  </p>
                </div>
                <Button className="w-full" onClick={() => navigate("/login")} style={SECONDARY_BUTTON}>
                  {t("auth.backToLogin")}
                </Button>
              </div>
            )}
          </div>

          {!done && (
            <div className="text-center">
              <button
                onClick={() => navigate("/login")}
                className="inline-flex items-center gap-1.5"
                style={{
                  fontSize: "0.875rem",
                  color: "#c96442",
                  background: "none",
                  border: "none",
                  cursor: "pointer",
                  padding: 0,
                }}
              >
                <ArrowLeftOutlined style={{ fontSize: 12 }} />
                {t("auth.backToLogin")}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

const HEADING = {
  fontFamily: "Georgia, serif",
  fontWeight: 500,
  fontSize: "1.3rem",
  lineHeight: 1.2,
  color: "#141413",
  margin: 0,
  marginBottom: "8px",
} as const;

const LABEL = { color: "#4d4c48", fontWeight: 500, fontSize: "14px" } as const;

const PRIMARY_BUTTON = {
  backgroundColor: "#c96442",
  borderColor: "#c96442",
  color: "#faf9f5",
  borderRadius: "8px",
  height: "40px",
  fontWeight: 500,
} as const;

const SECONDARY_BUTTON = {
  backgroundColor: "#e8e6dc",
  borderColor: "#e8e6dc",
  color: "#4d4c48",
  borderRadius: "8px",
  height: "40px",
  fontWeight: 500,
  marginTop: "8px",
} as const;
