import { Route, Routes } from 'react-router';
import { BrokerShell } from './components/BrokerShell';
import { HomeRedirect, RequireRole } from './components/RouteGuards';
import { SignOutButton } from './components/SignOutButton';
import { ChangePolicyPage } from './pages/broker/ChangePolicyPage';
import { NewPolicyPage } from './pages/broker/NewPolicyPage';
import { PoliciesPage } from './pages/broker/PoliciesPage';
import { PolicyWorkspace } from './pages/broker/PolicyWorkspace';
import { CancellationPage } from './pages/me/CancellationPage';
import { HomePage } from './pages/me/HomePage';
import { PayPage } from './pages/me/PayPage';
import { StatementPage } from './pages/me/StatementPage';
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
      <Route path="/" element={<HomeRedirect />} />
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
        <Route path="policies/:id" element={<PolicyWorkspace />} />
        <Route path="policies/:id/change" element={<ChangePolicyPage />} />
        <Route path="*" element={<Soon />} />
      </Route>
      <Route
        path="/me"
        element={
          <RequireRole role="POLICYHOLDER">
            <HomePage />
          </RequireRole>
        }
      />
      <Route
        path="/me/pay"
        element={
          <RequireRole role="POLICYHOLDER">
            <PayPage />
          </RequireRole>
        }
      />
      <Route
        path="/me/cancellation"
        element={
          <RequireRole role="POLICYHOLDER">
            <CancellationPage />
          </RequireRole>
        }
      />
      <Route
        path="/me/statement"
        element={
          <RequireRole role="POLICYHOLDER">
            <StatementPage />
          </RequireRole>
        }
      />
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
