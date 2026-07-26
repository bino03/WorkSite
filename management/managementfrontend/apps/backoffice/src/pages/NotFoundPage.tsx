import { Button, Empty } from "antd";
import { useNavigate } from "react-router-dom";
import { ArrowLeftOutlined, HomeOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";

export const NotFoundPage = () => {
  const navigate = useNavigate();
  const { t } = useTranslation();

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gradient-to-br from-slate-50 to-slate-100 px-4">
      <div className="max-w-md w-full text-center space-y-6">
        {/* Large 404 */}
        <div className="text-8xl font-bold text-transparent bg-clip-text bg-gradient-to-r from-blue-600 to-purple-600">
          404
        </div>

        {/* Empty State */}
        <Empty
          description={
            <div className="space-y-2">
              <h1 className="text-2xl font-bold text-slate-900">
                {t('notFound.title')}
              </h1>
              <p className="text-slate-600">
                {t('notFound.subtitle')}
              </p>
            </div>
          }
          style={{
            marginTop: "2rem",
          }}
        />

        {/* Action Buttons */}
        <div className="flex flex-col gap-3 pt-4">
          <Button
            type="primary"
            size="large"
            icon={<HomeOutlined />}
            onClick={() => navigate("/backoffice")}
            className="w-full"
          >
            {t('notFound.goToDashboard')}
          </Button>
          <Button
            size="large"
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(-1)}
            className="w-full"
          >
            {t('notFound.back')}
          </Button>
        </div>

        {/* Help Text */}
        <p className="text-xs text-slate-500 mt-6">
          {t('notFound.contactSupport')}
        </p>
      </div>
    </div>
  );
};
