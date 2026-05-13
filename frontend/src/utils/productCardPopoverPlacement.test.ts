import { describe, it, expect } from 'vitest';
import { clampPopoverCenterX, estimateProductDetailPopoverWidth, VIEWPORT_MARGIN } from './productCardPopoverPlacement';

describe('estimateProductDetailPopoverWidth', () => {
  it('matches min(320, 90% of viewport)', () => {
    expect(estimateProductDetailPopoverWidth(400)).toBe(320);
    expect(estimateProductDetailPopoverWidth(200)).toBe(180);
  });
});

describe('clampPopoverCenterX', () => {
  it('keeps center when popover fits with room on both sides', () => {
    expect(clampPopoverCenterX(200, 100, 400, VIEWPORT_MARGIN)).toBe(200);
  });

  it('shifts right when the popover would overflow the left edge', () => {
    // Center at 50, half-width 100 -> left edge at -50; must be >= margin
    expect(clampPopoverCenterX(50, 200, 400, 8)).toBe(108);
  });

  it('shifts left when the popover would overflow the right edge', () => {
    // vw 400, half 100 -> max center 292; anchor center 350 -> clamp to 292
    expect(clampPopoverCenterX(350, 200, 400, 8)).toBe(292);
  });

  it('centers in the viewport when the popover is wider than usable width', () => {
    expect(clampPopoverCenterX(80, 500, 400, 8)).toBe(200);
  });
});
