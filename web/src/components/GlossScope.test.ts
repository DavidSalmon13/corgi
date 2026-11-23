import { describe, expect, it } from 'vitest';
import { GlossRegistry, SilentRegistry } from './GlossScope';

// Node has no DOM, so each fake element carries its document position and answers compareDocumentPosition from it.
const FOLLOWING = 4;
const PRECEDING = 2;
(globalThis as { Node?: unknown }).Node ??= { DOCUMENT_POSITION_FOLLOWING: FOLLOWING };

/** A fake mention at `position`; `hidden` stands for one under display: none, which has no boxes. */
function el(position: number, hidden = false): HTMLElement {
  return {
    position,
    getClientRects: () => ({ length: hidden ? 0 : 1 }),
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

  it('gives the mark to the first shown mention when an earlier copy is hidden (ledger table on a phone)', () => {
    const r = new GlossRegistry();
    const hiddenTable = el(1, true);
    const phoneList = el(2);
    r.add('endorsement', hiddenTable);
    r.add('endorsement', phoneList);
    expect(r.isFirst('endorsement', phoneList)).toBe(true);
  });

  it('keeps document order inside a closed dialog, so its first mention is already marked when it opens', () => {
    const r = new GlossRegistry();
    const first = el(1, true);
    const second = el(2, true);
    r.add('returnPremium', first);
    r.add('returnPremium', second);
    expect(r.isFirst('returnPremium', first)).toBe(true);
    expect(r.isFirst('returnPremium', second)).toBe(false);
  });

  it('tells every mention to re-check on refresh, so a resize can move the mark', () => {
    const r = new GlossRegistry();
    let told = 0;
    r.subscribe(() => told++);
    r.refresh();
    expect(told).toBe(1);
  });

  it('never marks a mention inside a silent scope (the timeline\'s screen-reader list), even the only one', () => {
    const r = new SilentRegistry();
    const srCopy = el(1);
    r.add('productLiability', srCopy);
    expect(r.isFirst('productLiability', srCopy)).toBe(false);
  });
});
