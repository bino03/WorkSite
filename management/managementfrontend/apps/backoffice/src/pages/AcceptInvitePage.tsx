import { useState, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { Form, Input, Button, Card, message, Typography } from "antd";
import { Mail, Lock, User, CheckCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import api from "@/api";

const { Title, Text } = Typography;

type AcceptInvitePayload = {
  token: string;
  password: string;
  name: string;
};

export default function AcceptInvitePage() {
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [token, setToken] = useState<string | null>(null);

  useEffect(() => {
    const tokenParam = searchParams.get("token");
    if (!tokenParam) {
      message.error(t('invite.invalidToken'));
      navigate("/login");
    } else {
      setToken(tokenParam);
    }
  }, [searchParams, navigate]);

  async function onFinish(values: { name: string; password: string }) {
    if (!token) return;

    setSubmitting(true);
    try {
      const payload: AcceptInvitePayload = {
        token: token,
        password: values.password,
        name: values.name.trim(),
      };

      await api.post("/auth/accept-invite", payload);

      message.success(t('invite.accountCreated'));

      // Redirecionar para login após 2 segundos
      setTimeout(() => {
        navigate("/login");
      }, 2000);

    } catch (err: any) {
      const apiMsg: string | undefined = err?.response?.data?.message || err?.message;
      message.error(apiMsg ?? t('invite.acceptFailed'));
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return null; // Ou um loading spinner
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 p-4">
      <Card className="w-full max-w-md shadow-lg">
        <div className="text-center mb-6">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-blue-100 rounded-full mb-4">
            <Mail size={32} className="text-blue-600" />
          </div>
          <Title level={2} className="mb-2">
            {t('invite.title')}
          </Title>
          <Text type="secondary">
            {t('invite.subtitle')}
          </Text>
        </div>

        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          disabled={submitting}
        >
          <Form.Item
            name="name"
            label={t('invite.fullName')}
            rules={[
              { required: true, message: t('invite.nameRequired') },
              { min: 3, message: t('invite.nameMinLength') },
            ]}
          >
            <Input
              prefix={<User size={16} className="text-gray-400" />}
              placeholder={t('invite.namePlaceholder')}
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="password"
            label={t('auth.password')}
            rules={[
              { required: true, message: t('invite.passwordRequired') },
              { min: 8, message: t('invite.passwordMinLength') },
            ]}
          >
            <Input.Password
              prefix={<Lock size={16} className="text-gray-400" />}
              placeholder={t('invite.passwordHint')}
              size="large"
            />
          </Form.Item>

          <Form.Item
            name="confirmPassword"
            label={t('invite.confirmPassword')}
            dependencies={["password"]}
            rules={[
              { required: true, message: t('invite.confirmPasswordRequired') },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("password") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error(t('invite.passwordsNoMatch')));
                },
              }),
            ]}
          >
            <Input.Password
              prefix={<Lock size={16} className="text-gray-400" />}
              placeholder={t('invite.confirmPasswordPlaceholder')}
              size="large"
            />
          </Form.Item>

          <Form.Item className="mb-0 mt-6">
            <Button
              type="primary"
              htmlType="submit"
              loading={submitting}
              size="large"
              block
              icon={<CheckCircle size={18} />}
            >
              {submitting ? t('invite.creating') : t('invite.createAccount')}
            </Button>
          </Form.Item>
        </Form>

        <div className="text-center mt-6">
          <Text type="secondary" className="text-sm">
            {t('invite.hasAccount')}{" "}
            <a href="/login" className="text-blue-600 hover:text-blue-700">
              {t('invite.loginHere')}
            </a>
          </Text>
        </div>
      </Card>
    </div>
  );
}
