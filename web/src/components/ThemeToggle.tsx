import { useEffect, useState } from 'react';

type Theme = 'light' | 'dark';
const STORAGE_KEY = 'plainly-theme';

function currentTheme(): Theme {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}

/** Light/dark switch. The choice is a per-viewer convenience, kept in localStorage when allowed. */
export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(currentTheme);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', theme === 'dark');
    root.classList.toggle('light', theme === 'light');
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Storage blocked (private mode): the toggle still works for this visit.
    }
  }, [theme]);

  const next: Theme = theme === 'dark' ? 'light' : 'dark';
  return (
    <button
      type="button"
      onClick={() => setTheme(next)}
      aria-label={`Switch to ${next} mode`}
      className="inline-flex min-h-11 items-center gap-2 rounded-control border-[1.5px] border-line px-3 text-sm font-semibold text-ink"
    >
      <span aria-hidden="true">{theme === 'dark' ? '☾' : '☀'}</span>
      {theme === 'dark' ? 'Dark' : 'Light'}
    </button>
  );
}
