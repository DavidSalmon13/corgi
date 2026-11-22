import { useQuery } from '@tanstack/react-query';
import { Route, Routes } from 'react-router';

type Health = { status: string; database: string };

async function fetchHealth(): Promise<Health> {
  const res = await fetch('/api/health');
  if (!res.ok) throw new Error(`API answered ${res.status}`);
  return res.json();
}

// Placeholder shell: the real screens (SignIn, B1–B3, P1) replace this in T13–T18.
function Scaffold() {
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth });
  return (
    <main className="mx-auto max-w-xl px-4 py-12">
      <h1 className="text-3xl font-bold">Plainly</h1>
      <p className="mt-2">Business insurance, explained in plain language.</p>
      <p className="mt-6 text-sm" data-testid="api-status">
        API: {health.isPending ? 'checking…' : health.isError ? 'unreachable' : health.data.status}
      </p>
    </main>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="*" element={<Scaffold />} />
    </Routes>
  );
}
