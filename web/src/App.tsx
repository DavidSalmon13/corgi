import { useQuery } from '@tanstack/react-query';
import { Route, Routes } from 'react-router';
import { ThemeToggle } from './components/ThemeToggle';

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
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold">Plainly</h1>
        <ThemeToggle />
      </div>
      <p className="mt-2 text-ink-2">Business insurance, explained in plain language.</p>
      <div className="mt-6 rounded-card border-[1.5px] border-line bg-card p-4">
        <p className="text-lg font-bold text-accent-ink tabular-nums">$1,200.00 due</p>
        <span className="mt-2 inline-flex rounded-full bg-wait-soft px-2.5 py-0.5 text-[13px] font-semibold text-wait">
          Waiting for bank
        </span>
        <p className="mt-2 font-mono text-[13px] text-ink-2">LE-0001 · PTX-0001</p>
      </div>
      <p className="mt-6 text-sm text-ink-2" data-testid="api-status">
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
