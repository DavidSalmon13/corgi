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

/** 422 body from ApiErrorHandler. */
export type ValidationBody = { error: 'validation'; message: string; fields: Record<string, string> };

export type DueReason = 'FIRST_YEAR' | 'CHANGE' | 'NOTHING_DUE';

/** me.MyCoverController.MyCover, from GET /api/me/cover. */
export type MyCover = {
  business: { legalName: string; description: string; ownerName: string };
  policy: { policyNumber: string; status: PolicyStatus; start: IsoDate; end: IsoDate };
  covers: Coverage[];
  amountDueCents: number;
  dueReason: DueReason;
  broker: { name: string };
  today: IsoDate;
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
  coverages: Coverage[];
  ledger: LedgerLine[];
  transactions: { displayId: string; type: string; effectiveDate: IsoDate; recordedOn: IsoDate }[];
};
