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
  /** The Stripe event that settled a PAYMENT line (evt_…); null for other lines. */
  stripeEventId?: string | null;
};

/** policy.PolicyViews.Coverage */
export type Coverage = {
  id: number;
  kind: CoverKind;
  label: string;
  annualPremiumCents: number;
  from: IsoDate;
  to: IsoDate;
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

export type DueReason = 'FIRST_YEAR' | 'EXTRA_COVER' | 'CHANGE' | 'NOTHING_DUE';

/** me.MyCoverController.MyCover, from GET /api/me/cover. */
export type MyCover = {
  business: { legalName: string; description: string; ownerName: string };
  policy: { policyNumber: string; status: PolicyStatus; start: IsoDate; end: IsoDate };
  covers: Coverage[];
  amountDueCents: number;
  dueReason: DueReason;
  /** The day the newest change after issuing starts; null when nothing has changed. */
  latestChangeFrom: IsoDate | null;
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
  transactions: { displayId: string; type: string; effectiveDate: IsoDate; recordedOn: IsoDate }[];
};
