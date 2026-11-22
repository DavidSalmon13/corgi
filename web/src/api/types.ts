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

export type EntryType = 'CHARGE' | 'REVERSAL' | 'PAYMENT' | 'REFUND';
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

export type DueReason = 'FIRST_YEAR' | 'EXTRA_COVER' | 'CORRECTION' | 'CHANGE' | 'NOTHING_DUE';

/** me.MyCoverController.MyCover, from GET /api/me/cover. */
export type MyCover = {
  business: { legalName: string; description: string; ownerName: string };
  policy: { policyNumber: string; status: PolicyStatus; start: IsoDate; end: IsoDate };
  covers: Coverage[];
  amountDueCents: number;
  dueReason: DueReason;
  /** The day the newest change after issuing starts; null when nothing has changed. */
  latestChangeFrom: IsoDate | null;
  /** The broker's reason for the newest change, shown word for word; null when none was given. */
  latestChangeReason?: string | null;
  /** Money we owe them that is on its way back to their card; null when no refund is pending. */
  refundWaiting?: RefundWaiting | null;
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
