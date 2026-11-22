import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { Link } from 'react-router';
import { api } from '../../api/client';
import type { PaymentView } from '../../api/types';
import { Mark } from '../../components/Mark';
import { StatusPill } from '../../components/StatusPill';
import { payFailedCopy, payPagePhase, payPaidCopy, payWaitingCopy } from '../../lib/paymentCopy';

export const PRIMARY =
  'flex min-h-[52px] w-full items-center justify-center rounded-xl bg-accent text-[17px] font-bold text-on-accent disabled:opacity-60';
export const SECONDARY =
  'flex min-h-12 w-full items-center justify-center rounded-xl border-[1.5px] border-line text-base font-semibold text-ink no-underline';

/** A payment the owner has sent to their bank from the pay page. */
export type Submitted = { paymentId: number; amountCents: number; last4: string | null; at: string };

/** Splits "Your $14.00 payment is on its way." so the amount keeps tabular figures, as in the design. */
function Headline({ text, amount }: { text: string; amount: string }) {
  const [before, after] = text.split(amount);
  return (
    <h1 className="m-0 text-[26px] leading-tight">
      {before}
      <span className="tabular-nums">{amount}</span>
      {after}
    </h1>
  );
}

/**
 * P5 after the card is sent: waiting for your bank, then paid or didn't go through. The page asks the API every
 * two seconds; the API only knows what Stripe's signed webhook told it, so the browser never decides the outcome.
 */
export function PaymentProgress({
  submitted,
  ownerName,
  onRetry,
}: {
  submitted: Submitted;
  ownerName: string;
  onRetry: (newCard: boolean) => void;
}) {
  const queryClient = useQueryClient();
  const payment = useQuery({
    queryKey: ['payment', submitted.paymentId],
    queryFn: () => api<PaymentView>(`/api/me/payments/${submitted.paymentId}`),
    refetchInterval: (q) => {
      const status = q.state.data?.status;
      return status === 'SUCCEEDED' || status === 'FAILED' ? false : 2000;
    },
  });
  const phase = payPagePhase({ submitted: true, status: payment.data?.status ?? 'OPEN' });

  useEffect(() => {
    // P1 should show the new state as soon as the owner goes back.
    if (phase === 'paid' || phase === 'failed') void queryClient.invalidateQueries({ queryKey: ['my-cover'] });
  }, [phase, queryClient]);

  const amount = payment.data?.amountCents ?? submitted.amountCents;
  const last4 = payment.data?.cardLast4 ?? submitted.last4;

  if (phase === 'paid') {
    const copy = payPaidCopy(ownerName, payment.data?.amountDueCents ?? 0);
    return (
      <>
        <section aria-live="polite" className="mt-16 flex flex-col items-center gap-3 text-center">
          <Mark size={88} />
          <StatusPill tone="good">{copy.title}</StatusPill>
          <h1 className="m-0 text-[28px]">{copy.headline}</h1>
          <p className="m-0 max-w-[300px] text-base leading-normal text-ink-2">{copy.body}</p>
        </section>
        <Link to="/me" className={`${SECONDARY} mt-4`}>
          Back to your cover
        </Link>
      </>
    );
  }

  if (phase === 'failed') {
    const copy = payFailedCopy(amount);
    const formatted = copy.headline.match(/\$[\d,]+\.\d\d/)?.[0] ?? '';
    return (
      <>
        <section role="alert" className="mt-10 flex flex-col gap-3.5 rounded-2xl border-[1.5px] border-bad bg-card px-5 py-6">
          <span className="self-start">
            <StatusPill tone="bad" dot>
              {copy.title}
            </StatusPill>
          </span>
          <Headline text={copy.headline} amount={formatted} />
          <p className="m-0 text-base leading-normal text-ink-2">{copy.body}</p>
        </section>
        <div className="flex flex-col gap-2.5">
          <button type="button" className={PRIMARY} onClick={() => onRetry(false)}>
            Try again
          </button>
          <button type="button" className={SECONDARY} onClick={() => onRetry(true)}>
            Use a different card
          </button>
        </div>
      </>
    );
  }

  const copy = payWaitingCopy(amount, last4, payment.data?.createdAt ?? submitted.at);
  const formatted = copy.headline.match(/\$[\d,]+\.\d\d/)?.[0] ?? '';
  return (
    <>
      <section aria-live="polite" className="mt-10 flex flex-col gap-3.5 rounded-2xl border border-line bg-card px-5 py-6">
        <span className="self-start">
          <StatusPill tone="wait" dot>
            {copy.title}
          </StatusPill>
        </span>
        <Headline text={copy.headline} amount={formatted} />
        <p className="m-0 text-base leading-normal text-ink-2">{copy.body}</p>
        <div className="border-t border-dashed border-line pt-3 text-sm text-ink-2 tabular-nums">{copy.meta}</div>
      </section>
      <Link to="/me" className={`${SECONDARY} mt-1`}>
        Back to your cover
      </Link>
    </>
  );
}
