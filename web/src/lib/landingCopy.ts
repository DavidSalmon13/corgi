import { DEMO_ACCOUNTS } from '../pages/SignIn';

// The words on L1, the landing page a guest sees at `/`. Screens render these through <Glossed>, so every
// insurance term in them (general liability, premium, endorsement, pro-rata, ledger) gets its "?" explanation.

export const SIGN_IN = 'Sign in';

/** AC3: what Plainly is, in one sentence. */
export const HERO_TITLE = 'Insurance for your business, in plain words.';
export const HERO_LEAD =
  "General liability cover for small businesses, where you always see what you're covered for, what you owe and why.";

/** The miniature P1 card beside the hero: shows the promise rather than telling it. */
export const PREVIEW = {
  label: 'Your cover · Juniper & Rye Bakery',
  owe: '$14.00',
  why: "Your broker moved your product liability endorsement's start a week earlier.",
  how: 'How we got this: 7 days × $2.00',
} as const;

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

/** AC6: no buying online yet. */
export const NO_BUY_ONLINE = "You can't buy online yet. Ask your broker for a policy.";

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
