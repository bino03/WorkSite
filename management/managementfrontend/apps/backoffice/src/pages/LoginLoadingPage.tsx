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
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: "13.6px",
        background: "var(--ind-color-bg)",
      }}
    >
      <svg
        width="40"
        height="40"
        viewBox="0 0 24 24"
        fill="none"
        stroke="var(--ind-color-accent)"
        strokeWidth="1.5"
        style={{ animation: "spin 1s linear infinite" }}
      >
        <path d="M12 2a10 10 0 0 1 10 10" strokeLinecap="round" />
      </svg>

      <p
        style={{
          fontFamily: "var(--ind-font-heading)",
          letterSpacing: "0.06em",
          textTransform: "uppercase",
          fontSize: 13,
          color: "var(--ind-accent-700)",
          margin: 0,
        }}
      >
        {t(statusKey)}
      </p>

      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};
