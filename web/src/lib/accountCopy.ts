/** What the broker sees about the owner's account on the policy workspace (real-sign-in AC11). */
export function ownerAccountLine(ownerName: string, signedUpAt: string | null): string {
  const first = ownerName.trim().split(/\s+/)[0] || ownerName;
  if (!signedUpAt) return `${first} hasn't created an account yet`;
  // Policy dates are America/New_York calendar days (DECISIONS.md); show the sign-up day the same way.
  const day = new Intl.DateTimeFormat('en-US', { timeZone: 'America/New_York', month: 'short', day: 'numeric', year: 'numeric' })
    .format(new Date(signedUpAt));
  return `Signed up ${day}`;
}
