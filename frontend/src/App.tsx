import type { ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { DispatcherPage } from './pages/DispatcherPage';
import { DriverPage } from './pages/DriverPage';
import { LoginPage } from './pages/LoginPage';

/** Shows the page only when logged in; otherwise sends the user to /login. */
function RequireAuth({ children }: { children: ReactNode }) {
  const { token } = useAuth();
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
}

/** The whole app: login state around it, and one route (URL) per page. */
export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<RequireAuth><DispatcherPage /></RequireAuth>} />
          <Route path="/driver" element={<RequireAuth><DriverPage /></RequireAuth>} />
          {/* Any other URL: back to the dispatcher page */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
