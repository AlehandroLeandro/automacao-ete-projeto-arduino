import { Navigate, Route, Routes } from "react-router-dom";

import { AppLayout } from "./components/AppLayout";
import { ProtectedRoute } from "./routes/ProtectedRoute";
import { ChangePasswordPage } from "./pages/ChangePasswordPage";
import { DashboardPage } from "./pages/DashboardPage";
import { FlowDetailPage } from "./pages/FlowDetailPage";
import { FlowPage } from "./pages/FlowPage";
import { LimePage } from "./pages/LimePage";
import { LoginPage } from "./pages/LoginPage";
import { MorePage } from "./pages/MorePage";
import { PhMeasurementDetailPage } from "./pages/PhMeasurementDetailPage";
import { PhMeasurementsPage } from "./pages/PhMeasurementsPage";
import { PendingRecordsPage } from "./pages/PendingRecordsPage";
import { ReportsPage } from "./pages/ReportsPage";
import { StopDetailPage } from "./pages/StopDetailPage";
import { StopsPage } from "./pages/StopsPage";
import { UsersAdminPage } from "./pages/UsersAdminPage";
import type { UserRole } from "./types";

const operationalRoles: UserRole[] = ["OPERATOR", "SUPERVISOR", "ADMIN"];

export function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Navigate to="/app/dashboard" replace />
          </ProtectedRoute>
        }
      />
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/change-password"
        element={
          <ProtectedRoute allowPasswordChange>
            <ChangePasswordPage />
          </ProtectedRoute>
        }
      />

      <Route element={<ProtectedRoute />}>
        <Route
          path="/app"
          element={<AppLayout />}
        >
          <Route index element={<Navigate to="/app/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="medicoes" element={<Navigate to="/app/medicoes/ph" replace />} />
          <Route element={<ProtectedRoute roles={operationalRoles} />}>
            <Route path="medicoes/ph" element={<PhMeasurementsPage />} />
            <Route path="medicoes/ph/:measurementId" element={<PhMeasurementDetailPage />} />
            <Route path="medicoes/cal" element={<LimePage />} />
            <Route path="pendencias" element={<PendingRecordsPage />} />
          </Route>
          <Route path="medicoes/vazao" element={<FlowPage />} />
          <Route path="medicoes/vazao/:measurementId" element={<FlowDetailPage />} />
          <Route path="paradas" element={<StopsPage />} />
          <Route path="paradas/:stopId" element={<StopDetailPage />} />
          <Route
            path="relatorios"
            element={
              <ProtectedRoute roles={["SUPERVISOR", "ADMIN"]}>
                <ReportsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="usuarios"
            element={
              <ProtectedRoute roles={["ADMIN"]}>
                <UsersAdminPage />
              </ProtectedRoute>
            }
          />
          <Route path="mais" element={<MorePage />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
