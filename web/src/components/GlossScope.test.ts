import { describe, expect, it } from 'vitest';
import { GlossRegistry } from './GlossScope';

// Node has no DOM, so each fake element carries its document position and answers compareDocumentPosition from it.
const FOLLOWING = 4;
const PRECEDING = 2;
(globalThis as { Node?: unknown }).Node ??= { DOCUMENT_POSITION_FOLLOWING: FOLLOWING };

function el(position: number): HTMLElement {
  return {
    position,
    compareDocumentPosition(other: { position: number }) {
      return other.position > position ? FOLLOWING : PRECEDING;
    },
  } as unknown as HTMLElement;
}

describe('GlossRegistry: only the first mention of a term on screen carries the "?"', () => {
  it('marks the first occurrence in document order, whatever order they registered in', () => {
    const r = new GlossRegistry();
    const later = el(5);
    const earlier = el(1);
    r.add('premium', later);
    r.add('premium', earlier);
    expect(r.isFirst('premium', earlier)).toBe(true);
    expect(r.isFirst('premium', later)).toBe(false);
  });

  it('keeps each term separate', () => {
    const r = new GlossRegistry();
    const a = el(1);
    const b = el(2);
    r.add('premium', a);
    r.add('ledger', b);
    expect(r.isFirst('ledger', b)).toBe(true);
  });

  it('hands the mark on when the first mention leaves the screen, and tells every mention', () => {
    const r = new GlossRegistry();
    const first = el(1);
    const second = el(2);
    let told = 0;
    r.subscribe(() => told++);
    r.add('reversal', first);
    r.add('reversal', second);
    r.remove('reversal', first);
    expect(r.isFirst('reversal', second)).toBe(true);
    expect(told).toBe(3);
  });
});
