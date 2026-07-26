import { useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuthContext } from "@/context/AuthContext";
import { useTranslation } from "react-i18next";

export const LoginLoadingPage = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuthContext();
  const { t } = useTranslation();
  const [statusKey] = useState("loginLoading.verifying");

  const { email, password } = (location.state ?? {}) as {
    email?: string;
    password?: string;
  };

  useEffect(() => {
    if (!email || !password) {
      navigate("/login", { replace: true });
      return;
    }

    (async () => {
      try {
        const userInfo = await login(email, password);
        sessionStorage.setItem("welcome_name", userInfo.name);

        if (userInfo.role === "ADMIN" || userInfo.role === "EMPLOYEE") {
          navigate("/backoffice", { replace: true });
        } else {
          navigate("/login", {
            replace: true,
            state: { error: t("loginLoading.loginFailed") },
          });
        }
      } catch {
        navigate("/login", {
          replace: true,
          state: { error: t("loginLoading.loginFailed") },
        });
      }
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div
      style={{
        height: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 32,
        background: "#f5f4ed",
      }}
    >
      {/* Custom spinner */}
      <div
        style={{
          position: "relative",
          width: 60,
          height: 60,
        }}
      >
        <svg
          width="60"
          height="60"
          viewBox="0 0 60 60"
          style={{ animation: "spin 1.2s linear infinite" }}
        >
          <circle
            cx="30"
            cy="30"
            r="26"
            fill="none"
            stroke="#c96442"
            strokeWidth="4"
            strokeDasharray="40 160"
            strokeLinecap="round"
          />
        </svg>
      </div>

      <p
        style={{
          fontSize: 16,
          fontWeight: 600,
          color: "#c96442",
          margin: 0,
          letterSpacing: "0.3px",
        }}
      >
        {t(statusKey)}
      </p>

      <style>{`
        @keyframes spin {
          0% {
            transform: rotate(0deg);
          }
          100% {
            transform: rotate(360deg);
          }
        }
      `}</style>
    </div>
  );
};
