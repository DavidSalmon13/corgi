import { Fragment, type ReactNode } from 'react';
import { GLOSSARY, TERM_PATTERNS, type GlossaryTerm } from '../lib/glossary';
import { Term } from './Term';

const MATCHER = new RegExp(`\\b(${TERM_PATTERNS.map(([, p]) => `(?:${p})`).join('|')})\\b`, 'gi');

function termOf(word: string): GlossaryTerm | null {
  for (const [term, pattern] of TERM_PATTERNS) {
    if (new RegExp(`^(?:${pattern})$`, 'i').test(word)) return term;
  }
  return null;
}

/** Splits a sentence into plain text and the glossary terms in it, each term marked once (its first time). */
export function glossParts(text: string): (string | { word: string; term: GlossaryTerm })[] {
  const parts: (string | { word: string; term: GlossaryTerm })[] = [];
  const seen = new Set<GlossaryTerm>();
  let last = 0;
  for (const m of text.matchAll(MATCHER)) {
    const term = termOf(m[0]);
    if (!term || seen.has(term)) continue;
    seen.add(term);
    if (m.index > last) parts.push(text.slice(last, m.index));
    parts.push({ word: m[0], term });
    last = m.index + m[0].length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
}

/**
 * A sentence from a copy helper, shown with every insurance term in it marked with a "?" and its explanation.
 * Copy helpers return plain strings, so screens render them through this instead of as bare text.
 */
export function Glossed({ children }: { children: string }): ReactNode {
  return (
    <>
      {glossParts(children).map((part, i) =>
        typeof part === 'string' ? (
          <Fragment key={i}>{part}</Fragment>
        ) : (
          <Term key={i} explain={GLOSSARY[part.term]}>
            {part.word}
          </Term>
        ),
      )}
    </>
  );
}
