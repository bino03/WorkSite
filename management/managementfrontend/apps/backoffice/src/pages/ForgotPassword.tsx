import { Form, Input, Button } from "antd";
import { useNavigate } from "react-router-dom";
import { useState } from "react";
import { ArrowLeftOutlined, MailOutlined, CheckCircleOutlined, BuildOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

import { requestPasswordReset } from "@/services/authService";
import { ErrorHandler } from "@/errors/errorHandler";

export const ForgotPassword = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [submitted, setSubmitted] = useState(false);
  const [submittedEmail, setSubmittedEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);

  /**
   * O ecrã de sucesso é o mesmo exista ou não conta com este email — o backend
   * responde `204` nos dois casos, e o texto de `auth.emailSentDesc` já está
   * escrito nesses termos ("se existir uma conta associada a…").
   */
  const onFinish = async (values: { email: string }) => {
    setSubmitting(true);
    try {
      await requestPasswordReset(values.email.trim());
      setSubmittedEmail(values.email.trim());
      setSubmitted(true);
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

      {/* ── Right panel ── */}
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
            {!submitted ? (
              <>
                {/* Header */}
                <div className="mb-6">
                  <h2
                    style={{
                      fontFamily: "Georgia, serif",
                      fontWeight: 500,
                      fontSize: "1.3rem",
                      lineHeight: 1.2,
                      color: "#141413",
                      margin: 0,
                      marginBottom: "8px",
                    }}
                  >
                    {t('auth.recoverPassword')}
                  </h2>
                  <p style={{ fontSize: "0.875rem", color: "#87867f", lineHeight: 1.5, margin: 0 }}>
                    {t('auth.recoverInstructions')}
                  </p>
                </div>

                <Form layout="vertical" onFinish={onFinish} size="large">
                  <Form.Item
                    label={<span style={{ color: "#4d4c48", fontWeight: 500, fontSize: "14px" }}>{t('auth.email')}</span>}
                    name="email"
                    rules={[
                      { required: true, message: t('auth.emailRequired') },
                      { type: "email", message: t('auth.emailInvalid') },
                    ]}
                  >
                    <Input
                      placeholder="exemplo@email.com"
                      prefix={<MailOutlined style={{ color: "#b0aea5" }} />}
                    />
                  </Form.Item>

                  <Form.Item style={{ marginBottom: 0 }}>
                    <Button
                      htmlType="submit"
                      loading={submitting}
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
                      {t('auth.sendEmail')}
                    </Button>
                  </Form.Item>
                </Form>
              </>
            ) : (
              /* Success state */
              <div className="text-center space-y-4">
                <div
                  className="flex items-center justify-center w-12 h-12 mx-auto rounded-full"
                  style={{ backgroundColor: "rgba(201,100,66,0.10)" }}
                >
                  <CheckCircleOutlined style={{ fontSize: 24, color: "#c96442" }} />
                </div>
                <div>
                  <h2
                    style={{
                      fontFamily: "Georgia, serif",
                      fontWeight: 500,
                      fontSize: "1.3rem",
                      lineHeight: 1.2,
                      color: "#141413",
                      margin: 0,
                      marginBottom: "8px",
                    }}
                  >
                    {t('auth.emailSent')}
                  </h2>
                  <p style={{ fontSize: "0.875rem", color: "#87867f", lineHeight: 1.6, margin: 0 }}>
                    {t('auth.emailSentDesc', { email: submittedEmail })}
                  </p>
                </div>
                <Button
                  className="w-full"
                  onClick={() => navigate("/login")}
                  style={{
                    backgroundColor: "#e8e6dc",
                    borderColor: "#e8e6dc",
                    color: "#4d4c48",
                    borderRadius: "8px",
                    height: "40px",
                    fontWeight: 500,
                    marginTop: "8px",
                  }}
                >
                  {t('auth.backToLogin')}
                </Button>
              </div>
            )}
          </div>

          {/* Back to login */}
          {!submitted && (
            <div className="text-center">
              <button
                onClick={() => navigate("/login")}
                className="inline-flex items-center gap-1.5"
                style={{ fontSize: "0.875rem", color: "#c96442", background: "none", border: "none", cursor: "pointer", padding: 0 }}
              >
                <ArrowLeftOutlined style={{ fontSize: 12 }} />
                {t('auth.backToLogin')}
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
