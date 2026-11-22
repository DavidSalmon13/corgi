import { describe, expect, it } from 'vitest';
import type { Statement, StatementCorrection, StatementRow, StatementTrace } from '../api/types';
import { bannerCopy, correctionNetMaths, correctionSummary, drawerCopy, rowNote, rowTitle } from './statementCopy';

// covers AC3, AC4, AC5, AC6, AC7, T5 — the words on P3 "Your statement" and P4 "How we got this"

const noMaths = {
  annualCents: null,
  days: null,
  termDays: null,
  from: null,
  to: null,
  undoes: false,
  balanceBeforeCents: null,
  balanceAfterCents: null,
};
const noCause = {
  transactionType: null,
  effectiveDate: null,
  recordedOn: null,
  actorName: null,
  reason: null,
  oldStart: null,
  newStart: null,
};
const REASON = "Customer's signed request was dated Oct 8; cover was entered a week late.";
const correctCause = {
  transactionType: 'CORRECT',
  effectiveDate: '2026-10-08',
  recordedOn: '2026-11-05',
  actorName: 'Sam Reyes',
  reason: REASON,
  oldStart: '2026-10-15',
  newStart: '2026-10-08',
};

function row(over: Partial<Omit<StatementRow, 'trace'>> & { trace?: Partial<StatementTrace> }): StatementRow {
  const { trace, ...rest } = over;
  const kind = rest.kind ?? 'CHARGE';
  return {
    id: 'LE-0007',
    kind,
    date: '2026-11-05',
    title: 'Extra cover from Oct 8',
    amountCents: 65_600,
    balanceAfterCents: 1_400,
    pending: false,
    status: null,
    undoneOn: null,
    correctionId: null,
    ...rest,
    trace: { kind, maths: { ...noMaths, resultCents: rest.amountCents ?? 65_600 }, cause: noCause, money: null, refs: [], ...trace },
  };
}

const rebill = row({
  correctionId: 'PTX-0003',
  trace: {
    maths: { ...noMaths, annualCents: 73_000, days: 328, termDays: 365, from: '2026-10-08', to: '2027-09-01', resultCents: 65_600 },
    cause: correctCause,
    refs: [
      { kind: 'CHANGE', id: 'PTX-0003' },
      { kind: 'LINE', id: 'LE-0007' },
    ],
  },
});

const reversal = row({
  id: 'LE-0006',
  kind: 'REVERSAL',
  title: 'Extra cover from Oct 15 reversed',
  amountCents: -64_200,
  correctionId: 'PTX-0003',
  trace: {
    maths: { ...noMaths, annualCents: 73_000, days: 321, termDays: 365, from: '2026-10-15', to: '2027-09-01', resultCents: -64_200, undoes: true },
    cause: correctCause,
    refs: [
      { kind: 'CHANGE', id: 'PTX-0003' },
      { kind: 'LINE', id: 'LE-0006' },
      { kind: 'UNDOES', id: 'LE-0003' },
    ],
  },
});

const payment = row({
  id: 'LE-0005',
  kind: 'PAYMENT',
  date: '2026-10-15',
  title: 'Payment, card ending 4242',
  amountCents: -64_200,
  balanceAfterCents: 0,
  status: 'PAID',
  trace: {
    maths: { ...noMaths, resultCents: -64_200, balanceBeforeCents: 64_200, balanceAfterCents: 0 },
    cause: { ...noCause, recordedOn: '2026-10-15', actorName: 'Maya Okafor' },
    money: { cardLast4: '4242', date: '2026-10-15', status: 'PAID' },
    refs: [
      { kind: 'LINE', id: 'LE-0005' },
      { kind: 'STRIPE_PAYMENT', id: 'pi_3Q' },
      { kind: 'STRIPE_EVENT', id: 'evt_1Q' },
    ],
  },
});

const refund = row({
  id: 'LE-0008',
  kind: 'REFUND',
  title: 'Money back to card ending 4242',
  amountCents: 1_400,
  balanceAfterCents: 0,
  status: 'REFUND_SENT',
  trace: {
    maths: { ...noMaths, resultCents: 1_400, balanceBeforeCents: -1_400, balanceAfterCents: 0 },
    cause: { ...correctCause, effectiveDate: '2026-10-22', newStart: '2026-10-22' },
    money: { cardLast4: '4242', date: '2026-11-05', status: 'REFUND_SENT' },
    refs: [
      { kind: 'CHANGE', id: 'PTX-0003' },
      { kind: 'REFUND', id: 'RF-0001' },
      { kind: 'STRIPE_REFUND', id: 're_3Q' },
      { kind: 'STRIPE_EVENT', id: 'evt_1Q' },
    ],
  },
});

const correction: StatementCorrection = {
  transactionDisplayId: 'PTX-0003',
  recordedOn: '2026-11-05',
  oldStart: '2026-10-15',
  newStart: '2026-10-08',
  dayDelta: 7,
  netCents: 1_400,
  perDayCents: 200,
  reason: REASON,
  actorName: 'Sam Reyes',
  originalLineId: 'LE-0003',
  reversalLineId: 'LE-0006',
  rebillLineId: 'LE-0007',
};

function banner(over: Partial<Statement['banner']>): Statement['banner'] {
  return { state: 'PAID_UP', amountCents: 0, dueReason: null, cardLast4: null, ...over };
}

describe('statement banner', () => {
  it('says what is owed and why, with the same reason as the home screen', () => {
    expect(bannerCopy(banner({ state: 'OWES', amountCents: 1_400, dueReason: 'CORRECTION' }))).toEqual({
      title: 'You owe $14.00',
      detail: 'We fixed when your extra cover started.',
    });
  });

  it('waits for the bank while a payment is with it', () => {
    expect(bannerCopy(banner({ state: 'PAYMENT_WAITING', amountCents: 1_400 }))).toEqual({
      title: 'Waiting for your bank',
      detail: "You paid $14.00. We'll show it as paid once your bank confirms.",
    });
  });

  it('says we owe them and which card the money goes back to', () => {
    expect(bannerCopy(banner({ state: 'REFUND_WAITING', amountCents: 1_400, cardLast4: '4242' })).title).toBe(
      "We owe you $14.00. It's on its way to your card ending 4242.",
    );
  });

  it('says when they are all paid up', () => {
    expect(bannerCopy(banner({})).title).toBe("You're all paid up");
  });
});

describe('statement rows', () => {
  it('keeps the title the line was posted with', () => {
    expect(rowTitle(rebill)).toBe('Extra cover from Oct 8');
  });

  it('names the card on a payment still with the bank', () => {
    const waiting = row({
      kind: 'PAYMENT',
      title: 'Payment',
      pending: true,
      status: 'WAITING',
      trace: { money: { cardLast4: '4242', date: '2026-11-22', status: 'WAITING' } },
    });
    expect(rowTitle(waiting)).toBe('Payment, card ending 4242');
  });

  it('shows the arithmetic under a charge, and when it was undone', () => {
    expect(rowNote(rebill)).toBe('$730.00 × 328 ÷ 365');
    expect(rowNote({ ...rebill, undoneOn: '2026-11-05' })).toBe('$730.00 × 328 ÷ 365 · undone Nov 5');
  });

  it('marks money on its way and a declined try', () => {
    expect(rowNote(row({ kind: 'PAYMENT', pending: true, status: 'WAITING' }))).toBe('Waiting for your bank');
    expect(rowNote(row({ kind: 'REFUND', pending: true, status: 'REFUND_ON_ITS_WAY' }))).toBe('Refund on its way');
    expect(rowNote(row({ kind: 'PAYMENT', amountCents: 0, status: 'DECLINED' }))).toBe(
      "Didn't go through · no money moved",
    );
    expect(rowNote(payment)).toBe('Paid');
    expect(rowNote(refund)).toBe('Refund sent · banks take 5–10 days to show it');
  });

  it('says a reversal undoes a line in full', () => {
    expect(rowNote(reversal)).toBe('Undoes the Oct 15 line in full');
  });
});

describe('correction card', () => {
  it('reads old → new, the day difference and the daily price', () => {
    expect(correctionSummary(correction)).toBe('Oct 15 → Oct 8 · 7 more days × $2.00');
    expect(correctionSummary({ ...correction, newStart: '2026-10-22', dayDelta: -7, netCents: -1_400 })).toBe(
      'Oct 15 → Oct 22 · 7 fewer days × $2.00',
    );
  });

  it('leaves the daily price out when it is not a whole number of cents', () => {
    expect(correctionSummary({ ...correction, perDayCents: null, dayDelta: 1 })).toBe('Oct 15 → Oct 8 · 1 more day');
  });

  it('adds up the undo and the corrected line', () => {
    expect(correctionNetMaths(-64_200, 65_600)).toBe('−$642.00 + $656.00 = +$14.00');
    expect(correctionNetMaths(-64_200, 62_800)).toBe('−$642.00 + $628.00 = −$14.00');
  });
});

describe('how we got this', () => {
  it('explains a corrected charge by the day, with the change and who made it', () => {
    const d = drawerCopy(rebill);
    expect(d.line).toBe('Extra cover from Oct 8 · Nov 5, 2026');
    expect(d.amount).toBe('+$656.00');
    expect(d.plain).toBe('The right price for your extra cover, now that it starts on Oct 8.');
    expect(d.formula).toBe('$730.00 a year × 328 days ÷ 365 = $656.00');
    expect(d.formulaNote).toBe(
      "Charged by the day, Oct 8, 2026 to Sep 1, 2027. The end date isn't counted. Rounded to the cent once.",
    );
    expect(d.actionWhat).toBe('Start date corrected: Oct 15 → Oct 8');
    expect(d.actionWho).toBe('Sam Reyes, your broker · Nov 5, 2026');
    expect(d.reason).toBe(REASON);
    expect(d.payment).toBeNull();
    expect(d.refs).toEqual([
      { label: 'Change', id: 'PTX-0003' },
      { label: 'Line', id: 'LE-0007' },
    ]);
  });

  it('explains a reversal with the undone numbers and what it undoes', () => {
    const d = drawerCopy(reversal);
    expect(d.amount).toBe('−$642.00');
    expect(d.plain).toBe("An undo of the Oct 15 line, in full. We don't edit lines, so the original stays on your statement.");
    expect(d.formula).toBe('Undoes $730.00 × 321 ÷ 365 = $642.00');
    expect(d.formulaNote).toBe('The same numbers as the original line, with the sign flipped.');
    expect(d.refs.map((r) => r.label)).toEqual(['Change', 'Line', 'Undoes']);
  });

  it('explains a payment by the balance before and after, and the card', () => {
    const d = drawerCopy(payment);
    expect(d.plain).toBe('You paid by card.');
    expect(d.formula).toBe('Balance $642.00 → $0.00');
    expect(d.actionWhat).toBe('You paid by card');
    expect(d.actionWho).toBe('Maya Okafor · Oct 15, 2026');
    expect(d.reason).toBeNull();
    expect(d.payment).toEqual({
      what: 'Card ending 4242 · Oct 15, 2026',
      status: 'Paid',
      note: 'Stripe confirmed the payment, so we marked it paid.',
    });
    expect(d.refs.map((r) => r.label)).toEqual(['Line', 'Stripe payment', 'Stripe event']);
  });

  it('explains a refund, the change that owed it and every id', () => {
    const d = drawerCopy(refund);
    expect(d.amount).toBe('+$14.00');
    expect(d.formula).toBe('Balance −$14.00 → $0.00');
    expect(d.actionWhat).toBe('Start date corrected: Oct 15 → Oct 22');
    expect(d.payment?.status).toBe('Refund sent');
    expect(d.payment?.note).toBe('Stripe has sent it. Banks take 5–10 days to show a refund.');
    expect(d.refs.map((r) => r.label)).toEqual(['Change', 'Refund', 'Stripe refund', 'Stripe event']);
  });

  it('names the change for issuing, adding and cancelling, and falls back when the broker is unknown', () => {
    const issue = { ...correctCause, transactionType: 'ISSUE', effectiveDate: '2026-09-01', reason: null };
    expect(drawerCopy(row({ trace: { cause: issue } })).actionWhat).toBe('Policy started on Sep 1, 2026');
    expect(drawerCopy(row({ trace: { cause: { ...issue, transactionType: 'ADD_COVERAGE', effectiveDate: '2026-10-15' } } })).actionWhat).toBe(
      'Extra cover added from Oct 15',
    );
    expect(drawerCopy(row({ trace: { cause: { ...issue, transactionType: 'CANCEL', effectiveDate: '2026-12-01' } } })).actionWhat).toBe(
      'Policy cancelled from Dec 1, 2026',
    );
    expect(drawerCopy(row({ trace: { cause: { ...issue, actorName: null } } })).actionWho).toBe('Your broker · Nov 5, 2026');
  });

  it('says a declined try moved no money', () => {
    const d = drawerCopy(
      row({
        kind: 'PAYMENT',
        amountCents: 0,
        status: 'DECLINED',
        trace: {
          maths: { ...noMaths, resultCents: 0, balanceBeforeCents: 64_200, balanceAfterCents: 64_200 },
          cause: { ...noCause, recordedOn: '2026-10-15', actorName: 'Maya Okafor' },
          money: { cardLast4: '0002', date: '2026-10-15', status: 'DECLINED' },
        },
      }),
    );
    expect(d.plain).toBe('Your bank said no, so no money moved.');
    expect(d.payment?.status).toBe("Didn't go through");
  });
});

describe('drawer: when no money moved for the line itself', () => {
  it('says an undo settles together with the corrected line', () => {
    expect(drawerCopy(reversal).noMoney).toBe(
      "No money moved for this line on its own. It's settled together with the corrected line.",
    );
  });

  it('says a charge or credit goes into the balance', () => {
    expect(drawerCopy(rebill).noMoney).toBe(
      'No money moved for this line on its own. It adds to your balance, which you pay by card.',
    );
    expect(drawerCopy(row({ kind: 'CREDIT', amountCents: -1_400 })).noMoney).toBe(
      'No money moved for this line on its own. It comes off your balance.',
    );
  });

  it('leaves it out when the row is a payment or refund', () => {
    expect(drawerCopy(payment).noMoney).toBeNull();
    expect(drawerCopy(refund).noMoney).toBeNull();
  });
});

// covers broker-history AC7, T6 — the same drawer in the broker's voice: same maths and refs, no "you"/"your"
describe('how we got this, for the broker', () => {
  const all = [rebill, reversal, payment, refund, row({ kind: 'CREDIT', amountCents: -1_400 })];

  it('keeps the policyholder voice by default', () => {
    expect(drawerCopy(rebill, 'policyholder')).toEqual(drawerCopy(rebill));
    expect(drawerCopy(payment).refsNote).toBe('Quote these if you ever need to ask us or your bank about this line.');
  });

  it('never says "you" or "your"', () => {
    for (const r of all) {
      const text = JSON.stringify(drawerCopy(r, 'broker'));
      expect(text, r.id).not.toMatch(/\byou(r|'re|'ll)?\b/i);
    }
  });

  it('shows the same maths, amount and references as the policyholder sees', () => {
    for (const r of all) {
      const b = drawerCopy(r, 'broker');
      const p = drawerCopy(r);
      expect(b.amount).toBe(p.amount);
      expect(b.formula).toBe(p.formula);
      expect(b.refs).toEqual(p.refs);
      expect(b.reason).toBe(p.reason);
      expect(b.line).toBe(p.line);
    }
  });

  it('explains a change and names the broker who made it', () => {
    const d = drawerCopy(rebill, 'broker');
    expect(d.plain).toBe('The right price for the extra cover, now that it starts on Oct 8.');
    expect(d.actionWhat).toBe('Start date corrected: Oct 15 → Oct 8');
    expect(d.actionWho).toBe('Sam Reyes · Nov 5, 2026');
    expect(d.noMoney).toBe('No money moved for this line on its own. It adds to the balance, which the customer pays by card.');
    expect(drawerCopy(reversal, 'broker').plain).toBe(
      "An undo of the Oct 15 line, in full. Lines are never edited, so the original stays on the statement.",
    );
  });

  it('explains a payment and a refund from the customer side', () => {
    const p = drawerCopy(payment, 'broker');
    expect(p.plain).toBe('The customer paid by card.');
    expect(p.actionWhat).toBe('The customer paid by card');
    expect(p.actionWho).toBe('Maya Okafor · Oct 15, 2026');
    expect(p.payment?.what).toBe('Card ending 4242 · Oct 15, 2026');
    const r = drawerCopy(refund, 'broker');
    expect(r.plain).toBe('Money owed to the customer, going back to their card.');
    expect(r.refsNote).toBe('Use these to find this line in Stripe or to quote it to support.');
  });

  it('words a payment still with the bank for the broker', () => {
    const waiting = row({
      id: 'PAY-0004',
      kind: 'PAYMENT',
      amountCents: -1_400,
      pending: true,
      status: 'WAITING',
      trace: {
        maths: { ...noMaths, resultCents: -1_400, balanceBeforeCents: 1_400, balanceAfterCents: 0 },
        cause: { ...noCause, recordedOn: '2026-11-22', actorName: 'Maya Okafor' },
        money: { cardLast4: '4242', date: '2026-11-22', status: 'WAITING' },
      },
    });
    const d = drawerCopy(waiting, 'broker');
    expect(d.plain).toBe("The payment is with the customer's bank.");
    expect(d.formulaNote).toBe("Not counted in the balance until it's confirmed.");
    expect(d.payment?.status).toBe('Waiting for the bank');
    expect(d.payment?.note).toBe('Shown as paid once the bank confirms.');
  });
});
