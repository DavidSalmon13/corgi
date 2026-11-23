import { describe, expect, it } from 'vitest';
import { glossParts } from './components/Glossed';
import { GLOSSARY, TERM_PATTERNS } from './lib/glossary';

// The app speaks the industry's language and explains it (DECISIONS.md, 2026-11-23): the standard insurance term,
// never a home-made substitute, and every term on screen carries a "?" with its meaning. A source scan, so a new
// screen that says "extra cover" or shows "premium" without its explanation fails here, not in review.

/** Home-made stand-ins for a standard term, with the term to use instead. */
const SUBSTITUTES: [RegExp, string][] = [
  [/\bextra cover\b/i, 'endorsement / product liability'],
  [/\bbase cover\b/i, 'general liability'],
  [/\bcover period\b/i, 'policy period'],
  [/\bcharged by the day\b/i, 'pro-rata'],
  [/\bundone in full\b/i, 'reversal'],
  [/\bmoney back\b/i, 'return premium / refund'],
  [/\bcancellation fee\b/i, 'short-rate penalty'],
  [/\byearly price\b/i, 'annual premium'],
  [/\bmoney record\b/i, 'ledger'],
  [/\bchange this policy\b/i, 'mid-term change'],
  [/\b(?:an undo|undone later)\b/i, 'reversal'],
];

/** Every term the glossary knows, as one pattern. */
const JARGON = new RegExp(`\\b(?:${TERM_PATTERNS.map(([, p]) => p).join('|')})\\b`, 'i');

// Every screen and component, plus the lib helpers that write sentences for them, as raw source text
// (Vite reads them at test time).
const SOURCES = import.meta.glob<string>(['./**/*.tsx', '!./**/*.test.tsx', './lib/*Copy.ts', './lib/ledgerRows.ts'], {
  query: '?raw',
  import: 'default',
  eager: true,
});

/** Text a viewer could read: drops comments, imports, explanations and identifiers like annualPremiumCents. */
function visibleText(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '')
    .replace(/^import .*$/gm, '')
    .replace(/explain=(\{`[^`]*`\}|"[^"]*"|\{[^}]*\})/g, '')
    .replace(/\b[a-z]+[A-Z]\w*\b/g, ''); // camelCase identifiers
}

/**
 * A screen's own JSX text outside every `<Term>` and `<Glossed>`: where a term would show without its "?".
 * Text inside a button, link or option is left out: a "?" can't sit inside another control, so those labels lean on
 * the explained term in the heading or hint next to them. Strings a screen hands to `<Glossed>` elsewhere (a blocked
 * reason, an error) are checked where they are written, in the copy helpers' substitute scan above.
 */
function unexplained(source: string): string {
  const jsx = visibleText(source)
    .replace(/<Term\b[^>]*>[\s\S]*?<\/Term>/g, '')
    .replace(/<Glossed\b[^>]*>[\s\S]*?<\/Glossed>/g, '')
    .replace(/<(button|Link|a|option)\b[^>]*>[\s\S]*?<\/\1>/g, '');
  return [...jsx.matchAll(/>([^<>{}]+)</g)].map((m) => m[1]).join(' ');
}

const files = Object.entries(SOURCES);
const screens = files.filter(([name]) => name.endsWith('.tsx'));

describe('standard insurance terms, always explained', () => {
  it('finds the screens and the copy helpers to check', () => {
    const names = files.map(([name]) => name);
    expect(screens.length).toBeGreaterThan(5);
    expect(names).toContain('./lib/addCoverCopy.ts');
    expect(names).toContain('./lib/cancelCopy.ts');
    expect(names).toContain('./lib/ledgerRows.ts');
  });

  it.each(files)('%s uses the standard term, not a substitute', (_, source) => {
    const text = visibleText(source);
    const hits = SUBSTITUTES.filter(([re]) => re.test(text)).map(([re, use]) => `${re.source} → use ${use}`);
    expect(hits).toEqual([]);
  });

  // Copy helpers return plain strings, which screens render through <Glossed>; a screen's own words must sit in a
  // <Term> or <Glossed> to get their "?".
  it.each(screens)('%s shows no insurance term without its "?"', (_, source) => {
    expect(unexplained(source).match(JARGON)?.[0] ?? null).toBeNull();
  });

  it('catches a term that is not inside a Term or Glossed', () => {
    expect(JARGON.test(unexplained('<p>Your premium is due</p>'))).toBe(true);
    expect(JARGON.test(unexplained('<Term explain={GLOSSARY.premium}>Premium</Term>'))).toBe(false);
    expect(JARGON.test(unexplained('<Glossed>{`Your premium is due`}</Glossed>'))).toBe(false);
  });

  it('has an explanation for every term it can spot', () => {
    for (const [term] of TERM_PATTERNS) expect(GLOSSARY[term].length).toBeGreaterThan(10);
  });
});

describe('Glossed', () => {
  it('marks each term the first time it appears, longest phrase first', () => {
    const parts = glossParts('Return premium: the premium given back. Another return premium.');
    expect(parts).toEqual([
      { word: 'Return premium', term: 'returnPremium' },
      ': the ',
      { word: 'premium', term: 'premium' },
      ' given back. Another return premium.',
    ]);
  });

  it('spots plurals and the past tense of reversal', () => {
    const terms = glossParts('Two endorsements, one reversed line, a rebill.').filter((p) => typeof p !== 'string');
    expect(terms).toEqual([
      { word: 'endorsements', term: 'endorsement' },
      { word: 'reversed', term: 'reversal' },
      { word: 'rebill', term: 'rebill' },
    ]);
    expect(glossParts('Reverses $730.00 × 321 ÷ 365')[0]).toEqual({ word: 'Reverses', term: 'reversal' });
  });

  it('leaves a sentence without terms as it is', () => {
    expect(glossParts('You owe $14.00.')).toEqual(['You owe $14.00.']);
  });
});
