import { Navigate, Outlet, Route, Routes } from 'react-router';
import { BrokerShell } from './components/BrokerShell';
import { Home, RequireRole } from './components/RouteGuards';
import { SignOutButton } from './components/SignOutButton';
import { ChangePolicyPage } from './pages/broker/ChangePolicyPage';
import { HistoryPage } from './pages/broker/HistoryPage';
import { NewPolicyPage } from './pages/broker/NewPolicyPage';
import { PoliciesPage } from './pages/broker/PoliciesPage';
import { PolicyWorkspace } from './pages/broker/PolicyWorkspace';
import { CancellationPage } from './pages/me/CancellationPage';
import { HomePage } from './pages/me/HomePage';
import { LegacyRedirect, MeLanding } from './pages/me/MeRoutes';
import { PayPage } from './pages/me/PayPage';
import { StatementPage } from './pages/me/StatementPage';
import { TimelinePage } from './pages/me/TimelinePage';
import { SignIn } from './pages/SignIn';
import { SignUp } from './pages/SignUp';

// Placeholder for broker pages not built yet in this spec.
function Soon() {
  return (
    <div className="px-5 py-10">
      <p className="text-ink-2">This page is on its way.</p>
      <SignOutButton className="mt-2 -ml-2" />
    </div>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/signup" element={<SignUp />} />
      <Route
        path="/broker"
        element={
          <RequireRole role="BROKER">
            <BrokerShell />
          </RequireRole>
        }
      >
        <Route path="policies" element={<PoliciesPage />} />
        <Route path="policies/new" element={<NewPolicyPage />} />
        <Route path="customers/:customerId/policies/new" element={<NewPolicyPage />} />
        <Route path="policies/:id" element={<PolicyWorkspace />} />
        <Route path="policies/:id/change" element={<ChangePolicyPage />} />
        <Route path="policies/:id/history" element={<HistoryPage />} />
        <Route path="*" element={<Soon />} />
      </Route>
      {/* more-policies AC7, AC8: /me picks the single policy's home or "Your policies"; each page lives under its policy. */}
      <Route
        path="/me"
        element={
          <RequireRole role="POLICYHOLDER">
            <Outlet />
          </RequireRole>
        }
      >
        <Route index element={<MeLanding />} />
        <Route path="policies" element={<Navigate to="/me" replace />} />
        <Route path="policies/:policyId" element={<HomePage />} />
        <Route path="policies/:policyId/pay" element={<PayPage />} />
        <Route path="policies/:policyId/cancellation" element={<CancellationPage />} />
        <Route path="policies/:policyId/statement" element={<StatementPage />} />
        <Route path="policies/:policyId/timeline" element={<TimelinePage />} />
        <Route path="pay" element={<LegacyRedirect page="pay" />} />
        <Route path="cancellation" element={<LegacyRedirect page="cancellation" />} />
        <Route path="statement" element={<LegacyRedirect page="statement" />} />
        <Route path="timeline" element={<LegacyRedirect page="timeline" />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
