// The insurance words the app uses, each with the plain explanation shown behind its "?".
// Screens use the standard term, never a home-made substitute; `Glossed` and `Term` show the explanation.

export type GlossaryTerm =
  | 'endorsement'
  | 'midTermChange'
  | 'annualPremium'
  | 'premium'
  | 'proRata'
  | 'effectiveDate'
  | 'policyPeriod'
  | 'reversal'
  | 'rebill'
  | 'returnPremium'
  | 'shortRate'
  | 'ledger'
  | 'generalLiability'
  | 'productLiability';

export const GLOSSARY: Record<GlossaryTerm, string> = {
  endorsement:
    'A change made to a policy after it starts, such as adding a coverage. It applies from its effective date to the end of the policy period.',
  midTermChange: 'A change made while the policy is running: an endorsement, a correction or a cancellation.',
  annualPremium: 'The price of a coverage for a full policy year.',
  premium: 'The price paid for insurance.',
  proRata:
    'Charged by the day: the annual premium × the days covered ÷ the days in the policy period, so every day costs the same.',
  effectiveDate: 'The first day a coverage protects the business.',
  policyPeriod: 'The dates the policy runs, from its start date up to its end date.',
  reversal:
    'A ledger line that cancels an earlier line in full. The earlier line stays visible, so nothing is edited or hidden.',
  rebill: 'The corrected charge, worked out again after a reversal.',
  returnPremium:
    'Premium given back for the days a cancelled policy no longer covers: the annual premium × the unused days ÷ the days in the policy period.',
  shortRate:
    'An extra fee some insurers keep when a policy is cancelled early. Plainly never charges one.',
  ledger: "The policy's money record: every charge, payment and refund, in order. Lines are never edited.",
  generalLiability: 'Pays if someone is hurt or their property is damaged because of the business.',
  productLiability: 'Pays if something the business sells hurts someone or damages their property.',
};

/**
 * How each term is spotted inside a sentence, longest phrases first so "annual premium" wins over "premium".
 * Plurals and the "-d" form of reversal are matched too.
 */
export const TERM_PATTERNS: [GlossaryTerm, string][] = [
  ['returnPremium', 'return premium'],
  ['annualPremium', 'annual premiums?'],
  ['shortRate', 'short-rate(?: penalty)?'],
  ['midTermChange', 'mid-term changes?'],
  ['generalLiability', 'general liability'],
  ['productLiability', 'product liability'],
  ['effectiveDate', 'effective dates?'],
  ['policyPeriod', 'policy period'],
  ['endorsement', 'endorsements?'],
  ['proRata', 'pro-rata'],
  ['reversal', 'reversals?|reversed'],
  ['rebill', 'rebills?|rebilled'],
  ['ledger', 'ledger'],
  ['premium', 'premiums?'],
];
