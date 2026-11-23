import { Link } from 'react-router';
import { Glossed } from '../components/Glossed';
import { Mark } from '../components/Mark';
import { ThemeToggle } from '../components/ThemeToggle';
import {
  CREATE_ACCOUNT,
  CREATE_ACCOUNT_NOTE,
  DEMO_BADGE,
  FOOTER,
  HERO_LEAD,
  HERO_TITLE,
  HOW_IT_WORKS,
  NO_BUY_ONLINE,
  PREVIEW,
  REVIEWER_TITLE,
  SHOWS,
  SHOWS_TITLE,
  SIGN_IN,
  STEPS,
  reviewerEntries,
} from '../lib/landingCopy';

const SECTION_HEAD = 'm-0 mb-3 text-[13px] font-bold uppercase tracking-[0.06em] text-ink-2';
const CARD = 'rounded-[14px] border-[1.5px] border-line bg-card p-4';

/**
 * L1, the page a guest sees at `/` (design/plainly/L1-Landing.dc.html): what Plainly is, how someone becomes a
 * policyholder, what their policy page will show, and a one-tap way into the demo accounts. No buying online.
 */
export function Landing() {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[430px] flex-col gap-10 px-5 pt-5 pb-7 sm:max-w-[760px] sm:gap-14 sm:px-8 sm:pt-8">
        <header className="flex items-center justify-between">
          <span className="flex items-center gap-2">
            <Mark size={30} />
            <span className="text-xl font-bold tracking-[-0.02em]">Plainly</span>
          </span>
          <Link to="/signin" className="px-1 py-2.5 text-base font-bold text-accent-ink no-underline">
            {SIGN_IN}
          </Link>
        </header>

        <main className="flex flex-col gap-10 sm:gap-14">
          <Hero />
          <HowItWorks />
          <Shows />
          <Reviewer />
        </main>

        <footer className="mt-auto flex items-end justify-between gap-4">
          <p className="m-0 text-sm leading-normal text-ink-2">{FOOTER}</p>
          <ThemeToggle />
        </footer>
      </div>
    </div>
  );
}

/** AC3: the one-sentence promise, shown beside a miniature of the policyholder's cover page. */
function Hero() {
  return (
    <section aria-labelledby="landing-title" className="grid gap-[18px] sm:grid-cols-[1fr_300px] sm:items-center sm:gap-10">
      <div className="flex flex-col gap-[18px]">
        <span className="self-start rounded-full bg-accent-soft px-2.5 py-1 text-xs font-bold uppercase tracking-[.06em] text-accent-ink">
          {DEMO_BADGE}
        </span>
        <h1 id="landing-title" className="m-0 text-4xl leading-[1.1] font-bold tracking-[-0.025em] sm:text-5xl">
          {HERO_TITLE}
        </h1>
        <p className="m-0 text-lg leading-[1.45] text-ink-2">
          <Glossed>{HERO_LEAD}</Glossed>
        </p>
        <p className="m-0 text-sm text-ink-2">{NO_BUY_ONLINE}</p>
      </div>
      <Preview />
    </section>
  );
}

/** A still picture of P1's "you owe … and why" card: the promise shown, not told. Its terms keep their "?". */
function Preview() {
  return (
    <figure className="relative m-0 h-[150px]" aria-label="Example of a policy page">
      <div aria-hidden="true" className={`${CARD} absolute inset-[14px_0_0_18px] bg-track`} />
      <div className={`${CARD} absolute inset-[0_18px_14px_0] flex flex-col gap-1.5`}>
        <span className="text-[13px] text-ink-2">{PREVIEW.label}</span>
        <span className="text-[17px] leading-[1.35]">
          You owe <b className="text-accent-ink">{PREVIEW.owe}</b>. <Glossed>{PREVIEW.why}</Glossed>
        </span>
        <span className="mt-auto self-start border-b-[1.5px] border-dotted border-accent-ink text-sm font-semibold text-accent-ink">
          {PREVIEW.how}
        </span>
      </div>
    </figure>
  );
}

/** AC4: broker issues → email → create your account, with the sign-up button in this section. */
function HowItWorks() {
  return (
    <section aria-labelledby="how-title">
      <h2 id="how-title" className={SECTION_HEAD}>
        {HOW_IT_WORKS}
      </h2>
      <ol className="m-0 flex list-none flex-col gap-4 p-0">
        {STEPS.map((s, i) => (
          <li key={s.title} className="flex items-start gap-3.5">
            <span
              aria-hidden="true"
              className="flex size-[30px] flex-none items-center justify-center rounded-full bg-accent-soft font-bold text-accent-ink"
            >
              {i + 1}
            </span>
            <span className="pt-0.5 text-base leading-[1.45]">
              <b>{s.title}</b> {s.detail}
            </span>
          </li>
        ))}
      </ol>
      <div className="mt-5 flex flex-col gap-2 sm:max-w-[360px]">
        <Link
          to="/signup"
          className="flex min-h-[52px] items-center justify-center rounded-[12px] bg-accent px-5 text-[17px] font-bold text-on-accent no-underline"
        >
          {CREATE_ACCOUNT}
        </Link>
        <p className="m-0 text-center text-sm text-ink-2">{CREATE_ACCOUNT_NOTE}</p>
      </div>
    </section>
  );
}

/** AC5: the standard terms a policy page uses, each with its "?". */
function Shows() {
  return (
    <section aria-labelledby="shows-title">
      <h2 id="shows-title" className={SECTION_HEAD}>
        {SHOWS_TITLE}
      </h2>
      <ul className={`${CARD} m-0 flex list-none flex-col gap-3 text-base leading-[1.45]`}>
        {SHOWS.map((line) => (
          <li key={line}>
            <Glossed>{line}</Glossed>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** AC7: one tap into each demo account; sign-in fills in the details from `?demo=`. */
function Reviewer() {
  return (
    <section aria-labelledby="reviewer-title">
      <h2 id="reviewer-title" className={SECTION_HEAD}>
        {REVIEWER_TITLE}
      </h2>
      <ul className="m-0 grid list-none gap-2.5 p-0 sm:grid-cols-2">
        {reviewerEntries().map((e, i) => (
          <li key={e.href}>
            <Link
              to={e.href}
              className="flex min-h-[60px] items-center gap-3 rounded-[12px] border-[1.5px] border-line bg-card px-3.5 py-2.5 text-ink no-underline hover:border-accent"
            >
              <span
                aria-hidden="true"
                className={`flex size-9 flex-none items-center justify-center rounded-full text-sm font-bold ${
                  i === 0 ? 'bg-accent text-on-accent' : 'bg-track text-ink'
                }`}
              >
                {e.initials}
              </span>
              <span className="flex flex-1 flex-col">
                <span className="font-bold">{e.label}</span>
                <span className="text-[13px] text-ink-2">{e.note}</span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
