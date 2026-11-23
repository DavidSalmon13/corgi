import { describe, expect, it } from 'vitest';
import { telHref } from './BrokerContact';

describe('telHref', () => {
  it('dials a US number with its country code', () => {
    expect(telHref('(415) 555-0142')).toBe('tel:+14155550142');
  });

  it('keeps a number that already has one', () => {
    expect(telHref('+44 20 7946 0958')).toBe('tel:+442079460958');
  });
});
