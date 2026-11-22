import { describe, expect, it } from 'vitest';
import type { HistoryAction, StatementCorrection, StatementRow, StatementTrace } from '../api/types';
import { actionDetail, actionMoney, actionStatus, actionTitle, actionTone, ledgerChips } from './historyCopy';

// covers AC2, AC3, AC4, AC5, AC6, T5 — the words on B5 "History" for the broker

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

function row(over: Partial<Omit<StatementRow, 'trace'>> & { trace?: Partial<StatementTrace> }): StatementRow {
  const { trace, ...rest } = over;
  const kind = rest.kind ?? 'CHARGE';
  return {
    id: 'LE-0001',
    kind,
    date: '2026-09-01',
    title: 'Base cover',
    amountCents: 120_000,
    balanceAfterCents: 120_000,
    pending: false,
    status: null,
    undoneOn: null,
    correctionId: null,
    ...rest,
    trace: { kind, maths: { ...noMaths, resultCents: rest.amountCents ?? 120_000 }, cause: noCause, money: null, refs: [], ...trace },
  };
}

function action(over: Partial<HistoryAction>): HistoryAction {
  return {
    id: 'PTX-0001',
    kind: 'CHANGE',
    changeType: 'ISSUE',
    date: '2026-09-01',
    actorName: 'Sam Reyes',
    amountCents: 120_000,
    pending: false,
    status: null,
    failureReason: null,
    cardLast4: null,
    reason: null,
    correction: null,
    rows: [],
    ...over,
  };
}

const issue = action({
  rows: [
    row({
      trace: { maths: { ...noMaths, annualCents: 120_000, days: 365, termDays: 365, from: '2026-09-01', to: '2027-09-01', resultCents: 120_000 } },
    }),
  ],
});

const extraCover = action({
  id: 'PTX-0002',
  changeType: 'ADD_COVERAGE',
  date: '2026-10-15',
  amountCents: 64_200,
  rows: [
    row({
      id: 'LE-0003',
      date: '2026-10-15',
      title: 'Extra cover from Oct 15',
      amountCents: 64_200,
      undoneOn: '2026-11-05',
      trace: { maths: { ...noMaths, annualCents: 73_000, days: 321, termDays: 365, from: '2026-10-15', to: '2027-09-01', resultCents: 64_200 } },
    }),
  ],
});

const correctionCard: StatementCorrection = {
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

const correction = action({
  id: 'PTX-0003',
  changeType: 'CORRECT',
  date: '2026-11-05',
  amountCents: 1_400,
  reason: REASON,
  correction: correctionCard,
  rows: [
    row({
      id: 'LE-0006',
      kind: 'REVERSAL',
      date: '2026-11-05',
      title: 'Extra cover from Oct 15 reversed',
      amountCents: -64_200,
      correctionId: 'PTX-0003',
      trace: { maths: { ...noMaths, annualCents: 73_000, days: 321, termDays: 365, from: '2026-10-15', resultCents: -64_200, undoes: true } },
    }),
    row({
      id: 'LE-0007',
      date: '2026-11-05',
      title: 'Extra cover from Oct 8',
      amountCents: 65_600,
      correctionId: 'PTX-0003',
      trace: { maths: { ...noMaths, annualCents: 73_000, days: 328, termDays: 365, from: '2026-10-08', to: '2027-09-01', resultCents: 65_600 } },
    }),
  ],
});

const cancel = action({
  id: 'PTX-0004',
  changeType: 'CANCEL',
  date: '2027-03-01',
  amountCents: -59_836,
  reason: 'Business closed.',
  rows: [
    row({
      id: 'LE-0009',
      kind: 'CREDIT',
      title: 'Base cover, money back from Mar 1',
      amountCents: -59_836,
      trace: { maths: { ...noMaths, annualCents: 120_000, days: 182, termDays: 365, from: '2027-03-01', to: '2027-09-01', resultCents: -59_836 } },
    }),
  ],
});

const paid = action({
  id: 'PAY-0001',
  kind: 'PAYMENT',
  changeType: null,
  actorName: 'Maya Okafor',
  amountCents: -120_000,
  status: 'PAID',
  cardLast4: '4242',
  rows: [
    row({
      id: 'LE-0002',
      kind: 'PAYMENT',
      title: 'Payment, card ending 4242',
      amountCents: -120_000,
      status: 'PAID',
      trace: {
        refs: [
          { kind: 'LINE', id: 'LE-0002' },
          { kind: 'STRIPE_PAYMENT', id: 'pi_3QpaidA' },
          { kind: 'STRIPE_EVENT', id: 'evt_1QpaidA' },
        ],
      },
    }),
  ],
});

const declined = action({
  id: 'PAY-0002',
  kind: 'PAYMENT',
  changeType: null,
  date: '2026-10-15',
  actorName: 'Maya Okafor',
  amountCents: 0,
  status: 'DECLINED',
  failureReason: 'Your card was declined.',
  cardLast4: '0002',
  rows: [row({ id: 'LE-0004', kind: 'PAYMENT', amountCents: 0, status: 'DECLINED' })],
});

const waiting = action({
  id: 'PAY-0004',
  kind: 'PAYMENT',
  changeType: null,
  date: '2026-11-22',
  actorName: 'Maya Okafor',
  amountCents: -1_400,
  pending: true,
  status: 'WAITING',
  cardLast4: '4242',
  rows: [
    row({
      id: 'PAY-0004',
      kind: 'PAYMENT',
      amountCents: -1_400,
      pending: true,
      status: 'WAITING',
      trace: { refs: [{ kind: 'STRIPE_PAYMENT', id: 'pi_3Qwait' }] },
    }),
  ],
});

const refundRefs = [
  { kind: 'CHANGE' as const, id: 'PTX-0003' },
  { kind: 'REFUND' as const, id: 'RF-0001' },
  { kind: 'STRIPE_REFUND' as const, id: 're_3Qback' },
];

const refundSent = action({
  id: 'RF-0001',
  kind: 'REFUND',
  changeType: null,
  date: '2026-11-06',
  amountCents: 1_400,
  status: 'REFUND_SENT',
  cardLast4: '4242',
  rows: [row({ id: 'LE-0010', kind: 'REFUND', amountCents: 1_400, status: 'REFUND_SENT', trace: { refs: refundRefs } })],
});

const refundFailed = action({
  ...refundSent,
  amountCents: 1_400,
  pending: true,
  status: 'REFUND_FAILED',
  failureReason: 'The card was closed.',
  rows: [row({ id: 'RF-0001', kind: 'REFUND', amountCents: 1_400, pending: true, status: 'REFUND_ON_ITS_WAY', trace: { refs: refundRefs } })],
});

const refundOnItsWay = action({ ...refundFailed, status: 'REFUND_ON_ITS_WAY', failureReason: null });

describe('actionTitle', () => {
  it('names each change with its ID', () => {
    expect(actionTitle(issue)).toBe('Policy created · PTX-0001');
    expect(actionTitle(extraCover)).toBe('Extra cover added · PTX-0002');
    expect(actionTitle(correction)).toBe('Start date corrected · PTX-0003');
    expect(actionTitle(cancel)).toBe('Policy cancelled · PTX-0004');
  });

  it('names payments by card, and calls a declined one an attempt', () => {
    expect(actionTitle(paid)).toBe('Payment, card ending 4242 · PAY-0001');
    expect(actionTitle(declined)).toBe('Payment attempt, card ending 0002 · PAY-0002');
    expect(actionTitle(waiting)).toBe('Payment, card ending 4242 · PAY-0004');
  });

  it('names refunds by card and says when one failed', () => {
    expect(actionTitle(refundSent)).toBe('Refund to card ending 4242 · RF-0001');
    expect(actionTitle(refundFailed)).toBe('Refund failed, card ending 4242 · RF-0001');
  });

  it('copes with no card on file', () => {
    expect(actionTitle({ ...paid, cardLast4: null })).toBe('Payment by card · PAY-0001');
  });
});

describe('actionMoney', () => {
  it('signs a settled amount', () => {
    expect(actionMoney(issue)).toBe('+$1,200.00');
    expect(actionMoney(paid)).toBe('−$1,200.00');
    expect(actionMoney(declined)).toBe('$0.00');
  });

  it('shows a correction as its net (AC6)', () => {
    expect(actionMoney(correction)).toBe('+$14.00 net');
  });

  it('marks money on its way as pending', () => {
    expect(actionMoney(waiting)).toBe('−$14.00 pending');
    expect(actionMoney(refundFailed)).toBe('+$14.00 pending');
  });
});

describe('actionDetail', () => {
  it('shows the sums behind a charge', () => {
    expect(actionDetail(issue)).toBe('Base cover for a year, Sep 1, 2026 to Sep 1, 2027. $1,200.00 × 365 ÷ 365.');
    expect(actionDetail(extraCover)).toBe('Extra cover from Oct 15. $730.00 × 321 ÷ 365.');
  });

  it('shows a correction as the undo plus the corrected line (AC6)', () => {
    expect(actionDetail(correction)).toBe(
      'Start moved from Oct 15 to Oct 8. Oct 15 line undone in full (−$642.00), corrected line added ($730.00 × 328 ÷ 365 = +$656.00).',
    );
  });

  it('shows the money back on a cancellation', () => {
    expect(actionDetail(cancel)).toBe('Base cover, money back from Mar 1. $1,200.00 × 182 ÷ 365.');
  });

  it("shows Stripe's status and refs for a payment", () => {
    expect(actionDetail(paid)).toBe('Paid. Confirmed by Stripe (pi_3QpaidA, evt_1QpaidA).');
    expect(actionDetail(waiting)).toBe(
      'Waiting for the bank. Stripe reports it as processing (pi_3Qwait). Shown as paid only once Stripe confirms.',
    );
  });

  it('gives the decline reason Stripe sent (AC5)', () => {
    expect(actionDetail(declined)).toBe('Didn’t go through. Stripe said: “Your card was declined.” No money moved.');
    expect(actionDetail({ ...declined, failureReason: null })).toBe('Didn’t go through. Card declined, no money moved.');
  });

  it("shows a refund's real state, with Stripe's reason when it failed (AC5)", () => {
    expect(actionDetail(refundSent)).toBe('Sent. Confirmed by Stripe (re_3Qback). Banks take 5–10 days to show it.');
    expect(actionDetail(refundOnItsWay)).toBe('On its way. Asked Stripe to send it (re_3Qback). Shown as sent once Stripe confirms.');
    expect(actionDetail(refundFailed)).toBe(
      'Stripe couldn’t send it: “The card was closed.” The customer still sees it as owed to them until a retry goes through.',
    );
    expect(actionDetail({ ...refundFailed, failureReason: null })).toBe(
      'Stripe couldn’t send it. The customer still sees it as owed to them until a retry goes through.',
    );
  });
});

describe('actionStatus', () => {
  it('has no status word for a change', () => {
    expect(actionStatus(issue)).toBeNull();
  });

  it('says plainly where money stands, including a failed refund', () => {
    expect(actionStatus(paid)).toBe('Paid');
    expect(actionStatus(declined)).toBe('Didn’t go through');
    expect(actionStatus(waiting)).toBe('Waiting for the bank');
    expect(actionStatus(refundSent)).toBe('Refund sent');
    expect(actionStatus(refundOnItsWay)).toBe('Refund on its way');
    expect(actionStatus(refundFailed)).toBe('Refund failed');
  });
});

describe('actionTone', () => {
  it('colours the rail dot as in B5 (AC2)', () => {
    expect(actionTone(issue)).toBe('neutral');
    expect(actionTone(extraCover)).toBe('neutral');
    expect(actionTone(cancel)).toBe('neutral');
    expect(actionTone(correction)).toBe('accent');
    expect(actionTone(paid)).toBe('good');
    expect(actionTone(refundSent)).toBe('good');
    expect(actionTone(declined)).toBe('bad');
    expect(actionTone(refundFailed)).toBe('bad');
    expect(actionTone(waiting)).toBe('wait');
    expect(actionTone(refundOnItsWay)).toBe('wait');
  });
});

describe('ledgerChips', () => {
  it('lists the ledger lines and strikes the ones later undone (AC4)', () => {
    expect(ledgerChips(extraCover)).toEqual([{ id: 'LE-0003', undone: true, row: extraCover.rows[0] }]);
    expect(ledgerChips(correction).map((c) => c.id)).toEqual(['LE-0006', 'LE-0007']);
  });

  it('leaves out money that has no ledger line yet', () => {
    expect(ledgerChips(waiting)).toEqual([]);
  });
});
