import type { ReactNode } from 'react';

/** A broker and how to reach them. {@code email} and {@code phone} are null when the broker has none on file. */
export type Broker = { name: string; email?: string | null; phone?: string | null };

/** "(415) 555-0142" → "+14155550142", for a tel: link that works from any phone. */
export function telHref(phone: string): string {
  const digits = phone.replace(/\D/g, '');
  return `tel:${digits.length === 10 ? `+1${digits}` : `+${digits}`}`;
}

const ROW =
  'flex min-h-11 items-center gap-2.5 text-[15px] font-semibold text-accent-ink no-underline hover:text-ink [overflow-wrap:anywhere]';

function Icon({ children }: { children: ReactNode }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="flex-none">
      {children}
    </svg>
  );
}

/**
 * Who to talk to about cover, with their email and phone as links: on P1, P6 and the landing page. The heading is
 * the caller's ("Your broker, Sam Reyes" for a policyholder, "Our broker, Sam Reyes" for a guest).
 */
export function BrokerContact({ heading, note, broker }: { heading: string; note: string; broker: Broker }) {
  return (
    <div className="flex flex-col gap-1">
      <span className="font-semibold">{heading}</span>
      <span className="text-sm leading-snug text-ink-2">{note}</span>
      {(broker.email || broker.phone) && (
        <ul className="m-0 mt-1 flex list-none flex-col p-0 sm:flex-row sm:flex-wrap sm:gap-x-6">
          {broker.email && (
            <li>
              <a href={`mailto:${broker.email}`} className={ROW}>
                <Icon>
                  <rect x="3" y="5" width="18" height="14" rx="2" />
                  <path d="M3 7l9 6 9-6" />
                </Icon>
                {broker.email}
              </a>
            </li>
          )}
          {broker.phone && (
            <li>
              <a href={telHref(broker.phone)} className={`${ROW} tabular-nums`}>
                <Icon>
                  <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z" />
                </Icon>
                {broker.phone}
              </a>
            </li>
          )}
        </ul>
      )}
    </div>
  );
}
