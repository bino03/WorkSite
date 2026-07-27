import './i18n';
import ReactDOM from "react-dom/client";
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { ConfigProvider } from "antd";
import { AuthProvider } from "./context/AuthContext";
import { ErrorBoundary } from "./components/common/ErrorBoundary";
import { antdTheme } from "./theme";
import { Login } from "./pages/Login";
import { LoginLoadingPage } from "./pages/LoginLoadingPage";
import { ForgotPassword } from "./pages/ForgotPassword";
import { NotFoundPage } from "./pages/NotFoundPage";
import AppLayout from "./layouts/AppLayout";
import { BackofficeHome } from "./pages/backoffice/BackofficeHome";
import { PrivateRoute } from "./PrivateRoute";
import EmployeesList from "./pages/backoffice/EmployeesList";
import EmployeeProfilePage from "./pages/backoffice/employee/EmployeeProfilePage";
import EnterprisesList from "./pages/enterprises/EnterprisesList";
import ConstructionStagesPage from "./pages/backoffice/enterprise/ConstructionStagesPage";
import ConstructionSubStagesPage from "./pages/backoffice/enterprise/ConstructionSubStagesPage";
import ConstructionExpensesPage from "./pages/backoffice/enterprise/ConstructionExpensesPage";
import TasksPage from "./pages/backoffice/TasksPage";
import AcceptInvitePage from "./pages/AcceptInvitePage";
import "antd/dist/reset.css";
import "./index.css";
import "./colors.css";

const App = () => (
  <BrowserRouter>
    <ConfigProvider theme={antdTheme}>
      <ErrorBoundary>
        <AuthProvider>
          <Routes>
            <Route path="/" element={<Navigate to="/login" replace />} />
            <Route path="/login" element={<Login />} />
            <Route path="/loading" element={<LoginLoadingPage />} />
            <Route path="/forgot-password" element={<ForgotPassword />} />

            {/* Rota pública de aceitação de convite */}
            <Route path="/accept-invite" element={<AcceptInvitePage />} />

            {/* Rotas protegidas do backoffice */}
            <Route path="/backoffice/*" element={<PrivateRoute><AppLayout /></PrivateRoute>}>
              <Route index element={<BackofficeHome />} />
              <Route path="funcionarios" element={<EmployeesList />} />
              <Route path="funcionarios/:id" element={<EmployeeProfilePage />} />
              <Route path="empreendimentos" element={<EnterprisesList />} />
              <Route path="empreendimentos/:enterpriseId/construction" element={<ConstructionStagesPage />} />
              <Route path="empreendimentos/:enterpriseId/construction/:stageId" element={<ConstructionSubStagesPage />} />
              <Route path="empreendimentos/:enterpriseId/construction/:stageId/:subStageId" element={<ConstructionExpensesPage />} />
              <Route path="tasks" element={<TasksPage />} />
            </Route>

            {/* 404 */}
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </AuthProvider>
      </ErrorBoundary>
    </ConfigProvider>
  </BrowserRouter>
);

ReactDOM.createRoot(document.getElementById("root")!).render(<App />);
