import type { CoverKind, PublicCoverage } from '../api/types';
import { DEMO_ACCOUNTS } from '../pages/SignIn';
import { formatCents } from './money';

// The words on L1, the landing page a guest sees at `/`. Screens render these through <Glossed>, so every
// insurance term in them (general liability, premium, endorsement, pro-rata, ledger) gets its "?" explanation.

export const SIGN_IN = 'Sign in';

/** AC3: what Plainly is, in one sentence. */
export const HERO_TITLE = 'Insurance for your business, in plain words.';
export const HERO_LEAD =
  "General liability cover for small businesses, where you always see what you're covered for, what you owe and why.";


/** AC4: how someone becomes a policyholder. */
export const HOW_IT_WORKS = 'How it works';
export const STEPS: readonly { title: string; detail: string }[] = [
  { title: 'Your broker issues your policy', detail: 'and enters your email.' },
  { title: 'You get an email', detail: 'saying your policy is ready.' },
  { title: 'You create your account', detail: 'with that same email, and see your cover, pay and trace every amount.' },
];
export const CREATE_ACCOUNT = 'Create your account';
export const CREATE_ACCOUNT_NOTE = 'Use the email your broker has for you.';

/** AC5: what the policy page shows, in the standard terms. */
export const SHOWS_TITLE = 'What your policy page shows you';
export const SHOWS: readonly string[] = [
  'Your premium, and exactly what you owe today.',
  'Every endorsement, with its pro-rata charge worked out day by day.',
  'A ledger where nothing is edited: a mistake gets a reversal and a new line, so you see both.',
];

/** AC12: the covers we offer, priced from GET /api/public/coverages. */
export const OFFER_TITLE = 'What we offer';
export const OFFER_INTRO = 'Annual premiums, the same prices your broker charges.';
export const PRICES_UNAVAILABLE = "Prices aren't available right now. Your broker can tell you.";
export const PRICES_LOADING = 'Loading prices…';

const DESCRIPTIONS: Record<CoverKind, string> = {
  BASE: "Pays if someone is hurt at your business or their property is damaged, and you're held responsible.",
  PRODUCT_LIABILITY: 'Covers harm caused by something you made or sold. Add it any time; you pay pro-rata from the day it starts.',
};

/** What a cover pays for, in plain words. */
export function offerDescription(kind: CoverKind): string {
  return DESCRIPTIONS[kind] ?? ''; // a cover the API adds later still lists, without a description
}

/** The small tag beside a cover's name: the base policy, or an endorsement added to it. */
export function offerRole(role: PublicCoverage['role']): string {
  return role === 'BASE' ? 'The policy' : 'Endorsement';
}

const DAYS_IN_YEAR = 365;

/**
 * "$730.00" + "a year · $2.00 a day in a 365-day policy period". The per-day price shows only when it is a whole
 * number of cents, and names the 365-day period because a leap-year period makes it $1.9945… (DECISIONS.md,
 * 2026-11-22 "The daily price appears only when it's exact").
 */
export function offerPrice(annualPremiumCents: number): { amount: string; per: string } {
  const amount = formatCents(annualPremiumCents);
  if (annualPremiumCents % DAYS_IN_YEAR !== 0) return { amount, per: 'a year' };
  return { amount, per: `a year · ${formatCents(annualPremiumCents / DAYS_IN_YEAR)} a day in a ${DAYS_IN_YEAR}-day policy period` };
}

/** AC6: no buying online yet. */
export const NO_BUY_ONLINE = "You can't buy online yet. Ask your broker for a policy.";

/**
 * Who a guest asks for a policy: the demo broker, Sam Reyes, at the email he signs in with and a fictional phone
 * number (555-0100 to 555-0199 is reserved for fiction). The same details the API gives a policyholder (V14).
 */
export const OUR_BROKER = { name: 'Sam Reyes', email: 'sam@plainly.test', phone: '(415) 555-0142' } as const;
export const OUR_BROKER_HEADING = `Our broker, ${OUR_BROKER.name}`;
export const OUR_BROKER_NOTE = 'Sets up your policy, answers questions about cover, and makes any change you need.';

/** AC7: one-tap demo entry, built from SignIn's own account list. */
export const REVIEWER_TITLE = 'Reviewing this demo?';
export function reviewerEntries(): { label: string; note: string; href: string; initials: string }[] {
  return DEMO_ACCOUNTS.map((a) => {
    const first = a.name.split(' ')[0];
    return {
      label: `Try as ${first}, the ${a.role.toLowerCase()}`,
      note: `Opens sign-in with ${first}'s details filled in`,
      href: `/signin?demo=${encodeURIComponent(a.email)}`,
      initials: a.name.split(' ').map((p) => p[0]).join(''),
    };
  });
}
