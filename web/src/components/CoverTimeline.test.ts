import { describe, expect, it } from 'vitest';
import { outsideLabelPlacement } from './CoverTimeline';

describe('outsideLabelPlacement: a narrow bar\'s label stays inside the timeline', () => {
  it('sits just after a bar that ends early in the period', () => {
    expect(outsideLabelPlacement({ left: 10, width: 15 }, null)).toEqual({ left: 'calc(25.00% + 8px)' });
  });

  it('sits after the today marker when that comes later than the bar', () => {
    expect(outsideLabelPlacement({ left: 10, width: 15 }, 40)).toEqual({ left: 'calc(40.00% + 8px)' });
  });

  it('goes before a short bar that runs to the end of the term, instead of off the edge', () => {
    // An endorsement added in the last ~80 days of a one-year term: 80% to 100%.
    expect(outsideLabelPlacement({ left: 80, width: 20 }, 85)).toEqual({ right: 'calc(20.00% + 8px)' });
  });
});
