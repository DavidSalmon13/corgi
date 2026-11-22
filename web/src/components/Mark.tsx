/** The Plainly corgi mark, from the sign-in design. */
export function Mark({ size = 64 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 56 56" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" aria-hidden="true" className="text-ink">
      <path d="M14 27 L11 7 L25 18 Z" className="fill-accent" />
      <path d="M42 27 L45 7 L31 18 Z" className="fill-accent" />
      <path d="M12 30 C12 18 44 18 44 30 C44 41 37 48 28 48 C19 48 12 41 12 30 Z" className="fill-accent" />
      <path d="M21 39 C21 34 35 34 35 39 C35 44 21 44 21 39 Z" className="fill-card" />
      <circle cx="21" cy="30" r="1.6" fill="currentColor" />
      <circle cx="35" cy="30" r="1.6" fill="currentColor" />
      <path d="M26 37 h4 l-2 2 z" fill="currentColor" />
    </svg>
  );
}
