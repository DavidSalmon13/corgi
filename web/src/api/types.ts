// Response shapes from the Spring Boot API (api/src/main/java/app/plainly/**). Money is integer cents.

import type { IsoDate } from '../lib/dates';

export type Role = 'BROKER' | 'POLICYHOLDER';

/** auth.SignedInUser: who the session belongs to. Never carries a password. */
export type SignedInUser = {
  id: number;
  role: Role;
  displayName: string;
  subtitle: string | null;
  customerId: number | null;
  email: string;
};

/** POST /api/auth/signin body. */
export type SignInRequest = { email: string; password: string };

/** POST /api/auth/signup body. The email must be the one the broker entered for the business. */
export type SignUpRequest = { email: string; password: string; confirmPassword: string };

export type EntryType = 'CHARGE' | 'CREDIT' | 'REVERSAL' | 'PAYMENT' | 'REFUND';
export type CoverKind = 'BASE' | 'PRODUCT_LIABILITY';
export type PolicyStatus = 'ACTIVE' | 'CANCELLED';

/** ledger.LedgerLine */
export type LedgerLine = {
  id: number;
  displayId: string;
  entryDate: IsoDate;
  type: EntryType;
  description: string;
  amountCents: number;
  calcAnnualCents: number | null;
  calcDays: number | null;
  calcTermDays: number | null;
  coverFrom: IsoDate | null;
  coverTo: IsoDate | null;
  coverageId: number | null;
  policyTransactionDisplayId: string | null;
  reversesDisplayId: string | null;
  reversedByDisplayId: string | null;
  balanceAfterCents: number;
  /** The Stripe PaymentIntent behind a PAYMENT line (pi_…); null for other lines. */
  stripePaymentIntentId?: string | null;
  /** The Stripe event that settled a PAYMENT or REFUND line (evt_…); null for other lines. */
  stripeEventId?: string | null;
  /** The refund behind a REFUND line (RF-…) and Stripe's id for it (re_…); null for other lines. */
  refundDisplayId?: string | null;
  stripeRefundId?: string | null;
};

/** policy.PolicyViews.Coverage */
export type Coverage = {
  id: number;
  kind: CoverKind;
  label: string;
  annualPremiumCents: number;
  from: IsoDate;
  to: IsoDate;
  /** When a correction replaced this cover: the start it had before. Null when it was never corrected. */
  previousStart?: IsoDate | null;
  /** When product liability was removed: the day it stopped, at the start of that day. Null while it runs. */
  endedOn?: IsoDate | null;
};

/** policy.IssuePlan: what issuing would charge, from POST /api/broker/policies/preview. */
export type IssuePlan = {
  start: IsoDate;
  end: IsoDate;
  lines: {
    kind: CoverKind;
    description: string;
    calc: { annualCents: number; days: number; termDays: number; amountCents: number };
    from: IsoDate;
    to: IsoDate;
  }[];
  totalCents: number;
};

/** POST /api/broker/policies body. Dates are yyyy-MM-dd. */
export type IssueRequest = {
  legalName: string;
  businessDescription: string;
  ownerName: string;
  ownerEmail: string;
  startDate: string;
  productLiability: boolean;
};

export type Issued = { policyId: number; policyNumber: string; transactionDisplayId: string; totalCents: number };

/** The arithmetic behind a charge: annualCents × days ÷ termDays, rounded once. */
export type ChargeCalc = { annualCents: number; days: number; termDays: number; amountCents: number };

/** POST /api/broker/policies/{id}/coverages(/preview) body. The date is yyyy-MM-dd. */
export type AddCoverRequest = { kind: CoverKind; startDate: string };

/** policy.AddCoveragePlan: what adding extra cover would charge. */
export type AddCoverPlan = {
  policyId: number;
  kind: CoverKind;
  label: string;
  from: IsoDate;
  to: IsoDate;
  calc: ChargeCalc;
  totalCents: number;
};

/** policy.AddCoverageService.Added: what adding saved, and the policy's new balance. */
export type CoverAdded = {
  policyId: number;
  transactionDisplayId: string;
  entries: { id: number; displayId: string }[];
  totalCents: number;
  balanceCents: number;
};

/** 422 body from ApiErrorHandler. */
export type ValidationBody = { error: 'validation'; message: string; fields: Record<string, string> };

export type DueReason =
  | 'FIRST_YEAR'
  | 'EXTRA_COVER'
  | 'CORRECTION'
  | 'CANCELLATION'
  | 'COVER_REMOVED'
  | 'CHANGE'
  | 'NOTHING_DUE';

/** me.MyCoverController.MyCover, from GET /api/me/cover. */
export type MyCover = {
  business: { legalName: string; description: string; ownerName: string };
  /** {@code end} is the original term end; {@code cancelledOn} is the day all cover stopped, or null. */
  policy: { policyNumber: string; status: PolicyStatus; start: IsoDate; end: IsoDate; cancelledOn?: IsoDate | null };
  covers: Coverage[];
  amountDueCents: number;
  dueReason: DueReason;
  /** The day the newest change after issuing starts; null when nothing has changed. */
  latestChangeFrom: IsoDate | null;
  /** The broker's reason for the newest change, shown word for word; null when none was given. */
  latestChangeReason?: string | null;
  /** Money we owe them that is on its way back to their card; null when no refund is pending. */
  refundWaiting?: RefundWaiting | null;
  /** How the cancellation was worked out; null unless the policy is cancelled. */
  cancellation?: Cancellation | null;
  broker: { name: string };
  today: IsoDate;
  /** The latest payment the owner submitted; null before their first one. */
  payment: PaymentSummary | null;
  /** False when Stripe isn't set up: the pay button explains instead of opening the card form. */
  paymentsEnabled: boolean;
};

/** Where a payment stands. Only a Stripe webhook moves it past OPEN. */
export type PaymentStatus = 'OPEN' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED';

/** billing: a payment as P1 and P5 show it. */
export type PaymentSummary = {
  status: PaymentStatus;
  amountCents: number;
  cardLast4: string | null;
  /** When it was submitted (ISO instant). */
  createdAt: string;
};

/** POST /api/me/payments: what the pay page needs to take a card. */
export type StartedPayment = {
  paymentId: number;
  amountCents: number;
  clientSecret: string;
  publishableKey: string;
  savedCard: { brand: string; last4: string } | null;
};

/** GET /api/me/payments/{id}: polled by the pay page until Stripe settles it. */
export type PaymentView = {
  paymentId: number;
  status: PaymentStatus;
  amountCents: number;
  cardLast4: string | null;
  createdAt: string;
  /** What's still owed after this payment, from the ledger. */
  amountDueCents: number;
};

/** policy.PolicyViews.ListRow */
export type PolicyListRow = {
  policyId: number;
  customerName: string;
  ownerName: string;
  policyNumber: string;
  status: PolicyStatus;
  balanceCents: number;
  owesMoney: boolean;
  /** A payment is with the bank and not settled yet. */
  paymentWaiting: boolean;
  lastAction: { type: string; recordedOn: IsoDate } | null;
};

/** policy.PolicyViews.Detail */
export type PolicyDetail = {
  policyId: number;
  policyNumber: string;
  status: PolicyStatus;
  start: IsoDate;
  end: IsoDate;
  /** The day all cover stopped; null unless cancelled. {@code end} stays the original term end. */
  cancelledOn?: IsoDate | null;
  today: IsoDate;
  customer: {
    legalName: string;
    businessDescription: string;
    ownerName: string;
    ownerEmail: string;
    /** When the owner created their account; null until they do. */
    ownerSignedUpAt: string | null;
  };
  balanceCents: number;
  /** A payment is with the bank and not settled yet. */
  paymentWaiting: boolean;
  coverages: Coverage[];
  ledger: LedgerLine[];
  transactions: PolicyTransaction[];
  /** Money going back to cards, oldest first. */
  refunds: RefundView[];
};

/** policy.PolicyViews.Transaction. {@code reason}: the broker's words for a correction, shown as written. */
export type PolicyTransaction = {
  displayId: string;
  type: string;
  effectiveDate: IsoDate;
  recordedOn: IsoDate;
  reason?: string | null;
};

/** me.MyCoverService: a pending refund as P1 shows it. */
export type RefundWaiting = { amountCents: number; cardLast4: string | null };

/** POST /api/broker/policies/{id}/corrections(/preview) body. The date is yyyy-MM-dd; preview ignores the reason. */
export type CorrectRequest = { startDate: string; reason: string };

/** policy.CorrectionPlan: the undo, the corrected charge and any refund, before anything is saved. */
export type CorrectionPlan = {
  policyId: number;
  coverageId: number;
  oldStart: IsoDate;
  newStart: IsoDate;
  undo: { entryId: number; entryDisplayId: string; label: string; calc: ChargeCalc; amountCents: number };
  rebill: { label: string; from: IsoDate; to: IsoDate; calc: ChargeCalc };
  /** Positive: the customer owes more. Negative: we owe them. */
  netCents: number;
  balanceAfterCents: number;
  refunds: PlannedRefund[];
  refundTotalCents: number;
};

/** One piece of a refund, against one earlier payment. */
export type PlannedRefund = { paymentId: number; paymentDisplayId: string; cardLast4: string | null; amountCents: number };

/** policy.CorrectCoverageService.Corrected, the 201 body. */
export type Corrected = {
  policyId: number;
  transactionDisplayId: string;
  undoDisplayId: string;
  rebillDisplayId: string;
  netCents: number;
  balanceCents: number;
  refunds: { displayId: string; paymentId: number; amountCents: number }[];
};

export type RefundStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED';

/** billing: a refund as B3 lists it. Only a Stripe webhook moves it to SUCCEEDED. */
export type RefundView = {
  id: number;
  displayId: string;
  status: RefundStatus;
  amountCents: number;
  cardLast4: string | null;
  attempt: number;
  policyTransactionDisplayId: string | null;
  stripeRefundId: string | null;
  failureReason: string | null;
};

/** billing.RefundService.Attempted: where a refund stands after POST /api/broker/refunds/{id}/retry. */
export type RefundRetried = { id: number; displayId: string; status: RefundStatus; attempt: number };

/** POST /api/broker/policies/{id}/cancellation(/preview) body. The date is yyyy-MM-dd; preview ignores the reason. */
export type CancelRequest = { cancelDate: string; reason: string };

/** policy.CancelPolicyPlan.Credit: one cover's unused days, given back. {@code amountCents} is negative. */
export type CancelCredit = {
  coverageId: number;
  kind: CoverKind;
  label: string;
  from: IsoDate;
  to: IsoDate;
  calc: ChargeCalc;
  amountCents: number;
};

/** The policy change that left money owing before the cancellation. */
export type KeptBackFor = 'ISSUE' | 'ADD_COVERAGE' | 'CORRECT' | 'REMOVE_COVERAGE';

/** policy.CancelPolicyPlan: the credits, what they pay off, and any refund, before anything is saved. */
export type CancelPlan = {
  policyId: number;
  cancelDate: IsoDate;
  policyStart: IsoDate;
  policyEnd: IsoDate;
  usedDays: number;
  unusedDays: number;
  termDays: number;
  credits: CancelCredit[];
  /** Negative: we already owed the customer. */
  balanceBeforeCents: number;
  /** The part of the credit that pays off what was still owed. */
  keptBackCents: number;
  keptBackFor: KeptBackFor | null;
  balanceAfterCents: number;
  /** Still owed for the days covered, when the credit is smaller than the balance. */
  stillOwedCents: number;
  /** Latest payment first. */
  refunds: PlannedRefund[];
  /** All credits together, negative. */
  creditTotalCents: number;
  refundTotalCents: number;
};

/** policy.CancelPolicyService.Cancelled, the 201 body. */
export type Cancelled = {
  policyId: number;
  cancelDate: IsoDate;
  transactionDisplayId: string;
  creditDisplayIds: string[];
  balanceCents: number;
  refundTotalCents: number;
  refunds: { displayId: string; paymentId: number; amountCents: number }[];
};

export type RemoveCoverRequest = { endDate: string; reason: string };

/**
 * policy.RemoveCoverPlan: removing product liability, priced before anything is saved. One credit for its unused
 * days, what that pays off, and any return premium to cards.
 */
export type RemoveCoverPlan = {
  policyId: number;
  coverageId: number;
  endDate: IsoDate;
  coverFrom: IsoDate;
  policyEnd: IsoDate;
  credit: CancelCredit;
  /** The credit, negative. */
  creditCents: number;
  /** Negative: we already owed the customer. */
  balanceBeforeCents: number;
  keptBackCents: number;
  keptBackFor: KeptBackFor | null;
  balanceAfterCents: number;
  stillOwedCents: number;
  /** Latest payment first. */
  refunds: PlannedRefund[];
  refundTotalCents: number;
};

/** policy.RemoveCoverService.Removed, the 201 body. */
export type CoverRemoved = {
  policyId: number;
  endDate: IsoDate;
  transactionDisplayId: string;
  creditDisplayId: string;
  balanceCents: number;
  refundTotalCents: number;
  refunds: { displayId: string; paymentId: number; amountCents: number }[];
};

/** me.MyCoverService.Cancellation: how a cancellation was worked out, for P1's cancelled card and P6. */
export type Cancellation = {
  endsOn: IsoDate;
  reason: string | null;
  usedDays: number;
  unusedDays: number;
  termDays: number;
  /** Negative amounts. */
  credits: { kind: CoverKind; annualCents: number; days: number; amountCents: number }[];
  keptBackCents: number;
  keptBackFor: KeptBackFor | null;
  refundCents: number;
  /** Null when nothing goes back to the card; PENDING until Stripe confirms every refund. */
  refundStatus: 'PENDING' | 'SUCCEEDED' | null;
  cardLast4: string | null;
  settledOn: IsoDate | null;
};

// --- statement.MyStatementController, from GET /api/me/statement (P3, P4) ---

export type BannerState = 'OWES' | 'PAYMENT_WAITING' | 'REFUND_WAITING' | 'PAID_UP';
export type MoneyStatus = 'PAID' | 'DECLINED' | 'WAITING' | 'REFUND_SENT' | 'REFUND_ON_ITS_WAY';
export type RefKind = 'CHANGE' | 'LINE' | 'UNDOES' | 'STRIPE_PAYMENT' | 'REFUND' | 'STRIPE_REFUND' | 'STRIPE_EVENT';

/** The numbers behind an amount: by the day for charges, credits and reversals; balance before → after for money. */
export type StatementMaths = {
  annualCents: number | null;
  days: number | null;
  termDays: number | null;
  from: IsoDate | null;
  to: IsoDate | null;
  resultCents: number;
  /** A reversal: these are the undone line's numbers, with the sign flipped. */
  undoes: boolean;
  balanceBeforeCents: number | null;
  balanceAfterCents: number | null;
};

/** What caused a row: the policy change (null type for a payment) and who made it. */
export type StatementCause = {
  transactionType: 'ISSUE' | 'ADD_COVERAGE' | 'CORRECT' | 'CANCEL' | 'REMOVE_COVERAGE' | string | null;
  effectiveDate: IsoDate | null;
  recordedOn: IsoDate | null;
  actorName: string | null;
  reason: string | null;
  oldStart: IsoDate | null;
  newStart: IsoDate | null;
};

export type StatementTrace = {
  kind: EntryType;
  maths: StatementMaths;
  cause: StatementCause;
  money: { cardLast4: string | null; date: IsoDate; status: MoneyStatus } | null;
  refs: { kind: RefKind; id: string }[];
};

export type StatementRow = {
  /** LE-… for a ledger line; the payment or refund id for a pending row. */
  id: string;
  kind: EntryType;
  date: IsoDate;
  title: string;
  amountCents: number;
  balanceAfterCents: number;
  /** Money on its way: shown, but not counted in the balance. */
  pending: boolean;
  status: MoneyStatus | null;
  undoneOn: IsoDate | null;
  correctionId: string | null;
  trace: StatementTrace;
};

export type StatementCorrection = {
  transactionDisplayId: string;
  recordedOn: IsoDate;
  oldStart: IsoDate;
  newStart: IsoDate;
  /** Positive when cover now starts earlier (more days). */
  dayDelta: number;
  netCents: number;
  /** Only when the yearly price divides into whole cents a day. */
  perDayCents: number | null;
  reason: string | null;
  actorName: string | null;
  originalLineId: string;
  reversalLineId: string;
  rebillLineId: string;
};

export type Statement = {
  header: { businessName: string; policyNumber: string; balanceCents: number };
  banner: { state: BannerState; amountCents: number; dueReason: DueReason | null; cardLast4: string | null };
  rows: StatementRow[];
  corrections: StatementCorrection[];
  today: IsoDate;
};

// --- statement.BrokerHistoryController, from GET /api/broker/policies/{id}/history (B5) ---

export type HistoryActionKind = 'CHANGE' | 'PAYMENT' | 'REFUND';

/** Where a payment or refund stands, as the broker needs to know it: unlike MoneyStatus, a failed refund is named. */
export type HistoryStatus = 'PAID' | 'DECLINED' | 'WAITING' | 'REFUND_SENT' | 'REFUND_ON_ITS_WAY' | 'REFUND_FAILED';

/** One card on the B5 timeline: a policy change, a payment attempt or a refund, with the statement rows behind it. */
export type HistoryAction = {
  /** PTX-… for a change, PAY-… for a payment, RF-… for a refund. */
  id: string;
  kind: HistoryActionKind;
  /** ISSUE, ADD_COVERAGE, CORRECT, CANCEL or REMOVE_COVERAGE for a change; null otherwise. */
  changeType: 'ISSUE' | 'ADD_COVERAGE' | 'CORRECT' | 'CANCEL' | 'REMOVE_COVERAGE' | string | null;
  date: IsoDate;
  actorName: string | null;
  /** The sum of the rows (a correction's net). Positive means the customer owes more. */
  amountCents: number;
  /** Money on its way: shown, but not counted in the balance. */
  pending: boolean;
  status: HistoryStatus | null;
  /** Stripe's words for a declined card or a failed refund. */
  failureReason: string | null;
  cardLast4: string | null;
  /** The broker's reason for a correction or cancellation, as written. */
  reason: string | null;
  correction: StatementCorrection | null;
  rows: StatementRow[];
};

export type History = {
  businessName: string;
  policyNumber: string;
  balanceCents: number;
  actions: HistoryAction[];
  today: IsoDate;
};

// --- timeline.MyTimelineController, from GET /api/me/timeline (P2) ---

export type TimelineTerm = {
  start: IsoDate;
  /** Always the original term end. */
  end: IsoDate;
  /** The day all cover stopped, or null. */
  cancelledOn: IsoDate | null;
  today: IsoDate;
};

export type TimelineCover = {
  id: number;
  kind: CoverKind;
  /** The cover's dates before any cancellation. */
  from: IsoDate;
  to: IsoDate;
  /** The day a removal stopped this cover (start of that day), else null. */
  endedOn?: IsoDate | null;
  /** The start a correction replaced, else null. */
  previousStart: IsoDate | null;
  annualCents: number;
  /** Only when the yearly price divides into whole cents a day. */
  perDayCents: number | null;
  /** From `from` to `to`, the cancellation day or `endedOn`, whichever comes first. */
  daysKept: number;
  /** The sum of `rows`: this cover's settled lines still in force on the statement. */
  chargedCents: number;
  rows: StatementRow[];
  /** The latest correction of this cover, else null. */
  correction: StatementCorrection | null;
};

export type Timeline = { term: TimelineTerm; covers: TimelineCover[] };
