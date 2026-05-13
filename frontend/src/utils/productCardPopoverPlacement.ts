/** Horizontal inset from viewport edges when clamping the detail popover. */
export const VIEWPORT_MARGIN = 8;

/**
 * Returns a fixed `left` position (CSS px) for a popover using `transform: translateX(-50%)`,
 * so the box stays within `[margin, viewportWidth - margin]`.
 */
export function clampPopoverCenterX(
  anchorCenterX: number,
  popoverWidth: number,
  viewportWidth: number,
  margin = VIEWPORT_MARGIN
): number {
  const half = popoverWidth / 2;
  const minCenter = margin + half;
  const maxCenter = viewportWidth - margin - half;
  if (minCenter > maxCenter) {
    return viewportWidth / 2;
  }
  return Math.min(Math.max(anchorCenterX, minCenter), maxCenter);
}
