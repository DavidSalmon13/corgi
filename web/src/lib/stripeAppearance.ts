import type { Appearance } from '@stripe/stripe-js';

/** Reads a Plainly colour token from the live theme, so Stripe's card form matches light and dark mode. */
function token(name: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

/** Stripe Payment Element styling built from the same tokens as the rest of the app. */
export function stripeAppearance(): Appearance {
  return {
    theme: 'flat',
    variables: {
      fontFamily: "'Source Sans 3', system-ui, sans-serif",
      fontSizeBase: '16px',
      colorPrimary: token('--pl-accent', '#E9A23B'),
      colorBackground: token('--pl-card', '#FFFDF8'),
      colorText: token('--pl-ink', '#1D2433'),
      colorTextSecondary: token('--pl-ink-2', '#555C69'),
      colorDanger: token('--pl-bad', '#B0281F'),
      borderRadius: '10px',
      spacingUnit: '4px',
    },
    rules: {
      '.Input': { border: `1.5px solid ${token('--pl-line', '#E4DCCD')}`, padding: '12px 14px', boxShadow: 'none' },
      '.Input:focus': { borderColor: token('--pl-accent', '#E9A23B'), boxShadow: 'none' },
      '.Label': { fontWeight: '600', fontSize: '14px' },
    },
  };
}

export const STRIPE_FONTS = [{ cssSrc: 'https://fonts.googleapis.com/css2?family=Source+Sans+3:wght@400;600&display=swap' }];
