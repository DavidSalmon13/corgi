import type { Corrected, CorrectionPlan, RefundView } from '../api/types';
import type { IsoDate } from './dates';

// What the broker reads on B4's Correct tab and B3 after a correction, and the one sentence the customer gets.
// Test-first skeleton (T3): T11 writes the words. Every function throws until then.

function notBuilt(): never {
  throw new Error('not built yet (T11)');
}

/** "Extra cover · Product liability ($730.00 a year)": which cover is being corrected. */
export function correctingWhich(_annualCents: number): string {
  return notBuilt();
}

/** "Undo: extra cover from Oct 15 · $730.00 × 321 ÷ 365 · −$642.00" */
export function undoLine(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** "Corrected: extra cover from Oct 8 · $730.00 × 328 ÷ 365 · +$656.00" */
export function correctedLine(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** "+$14.00 (7 more days × $2.00)" */
export function netChange(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** "7 more days × $2.00 · −$642.00 + $656.00": how the net change adds up. */
export function netWhy(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** The sentence the customer reads about this correction. */
export function customerWillSeeCorrection(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** What happens to the money when the broker saves: added to the balance, or refunded. */
export function settleLine(_plan: CorrectionPlan, _ownerName: string): string {
  return notBuilt();
}

/** "Save correction", or "Save and refund $14.00" when money goes back to a card. */
export function correctSaveLabel(_plan: CorrectionPlan): string {
  return notBuilt();
}

/** The confirm dialog: what changes, then the three amounts. */
export function confirmSummary(_plan: CorrectionPlan, _ownerName: string): { sentence: string; amounts: string } {
  return notBuilt();
}

/** The once-only banner on B3 after saving, and the ids it links. */
export function correctedBanner(_ownerName: string, _saved: Corrected, _cardLast4: string | null): {
  message: string;
  refs: string;
} {
  return notBuilt();
}

/** "The $14.00 refund didn't go through. Stripe couldn't send the money to card ending 4242." */
export function failedRefundText(_refund: RefundView): string {
  return notBuilt();
}

/** B3 timeline: "from Oct 8 · was Oct 15", or "from Oct 8" when the cover was never corrected. */
export function coverWasLabel(_from: IsoDate, _previousStart: IsoDate | null): string {
  return notBuilt();
}
