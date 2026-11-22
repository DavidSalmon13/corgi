import { describe, expect, it } from 'vitest';

// covers AC11, T19 — insurance words may appear only inside a Term's plain-language explanation.
// A source scan, so a new screen that slips "premium" into its copy fails here, not in review.

const JARGON = [
  /\bpremiums?\b/i,
  /\bpro[- ]?rata\b/i,
  /\bprorat/i,
  /\bendorsements?\b/i,
  /\beffective date\b/i,
  /\bunderwrit/i,
  /\bdeductible\b/i,
  /\binsured party\b/i,
];

// Every screen and component, as raw source text (Vite reads them at test time).
const SOURCES = import.meta.glob<string>(['./**/*.tsx', '!./**/*.test.tsx'], { query: '?raw', import: 'default', eager: true });

/** Text a viewer could read: drops comments, imports, Term explanations and identifiers like annualPremiumCents. */
function visibleText(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '')
    .replace(/^import .*$/gm, '')
    .replace(/explain=(\{`[^`]*`\}|"[^"]*")/g, '')
    .replace(/\b[a-z]+[A-Z]\w*\b/g, ''); // camelCase identifiers
}

describe('plain language on screen', () => {
  const files = Object.entries(SOURCES);

  it('finds the screens to check', () => {
    expect(files.length).toBeGreaterThan(5);
  });

  it.each(files)('%s has no unexplained insurance jargon', (_, source) => {
    const text = visibleText(source);
    const hits = JARGON.flatMap((re) => text.match(re) ?? []);
    expect(hits).toEqual([]);
  });

  it('catches jargon that is not inside a Term', () => {
    expect(JARGON.some((re) => re.test(visibleText('<p>Your premium is due</p>')))).toBe(true);
    expect(JARGON.some((re) => re.test(visibleText('<Term explain="Premium: what you pay.">Price</Term>')))).toBe(false);
  });
});
