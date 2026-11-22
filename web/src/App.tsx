import { Link, Route, Routes } from 'react-router';
import { BrokerShell } from './components/BrokerShell';
import { HomeRedirect, RequireRole } from './components/RouteGuards';
import { NewPolicyPage } from './pages/broker/NewPolicyPage';
import { PoliciesPage } from './pages/broker/PoliciesPage';
import { SignIn } from './pages/SignIn';

// Placeholder until B1–B3 (T15–T17) and P1 (T18) land.
function Soon() {
  return (
    <div className="px-5 py-10">
      <p className="text-ink-2">This page is on its way.</p>
      <Link to="/signin" className="mt-2 inline-block font-semibold">
        Switch person
      </Link>
    </div>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<HomeRedirect />} />
      <Route path="/signin" element={<SignIn />} />
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
        <Route path="*" element={<Soon />} />
      </Route>
      <Route
        path="/me/*"
        element={
          <RequireRole role="POLICYHOLDER">
            <Soon />
          </RequireRole>
        }
      />
      <Route path="*" element={<HomeRedirect />} />
    </Routes>
  );
}
