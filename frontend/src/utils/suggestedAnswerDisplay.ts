import type { SuggestedAnswer } from '../api/types';

/** Short storage codes from GCP conversational filtering (unnamed productAttributeValue). */
const STORAGE_LABELS: Record<string, string> = {
  S: 'Ambient',
  R: 'Refrigerated',
  D: 'Dry storage',
  F: 'Frozen',
  C: 'Refrigerated',
};

/**
 * Label for buttons and user bubble: use API displayText when it differs from value;
 * otherwise map known storage codes to readable names.
 */
export function suggestedAnswerDisplayLabel(sa: SuggestedAnswer): string {
  const v = (sa.value ?? '').trim();
  const d = (sa.displayText ?? '').trim();
  if (d && d !== v) return d;
  if (v.length <= 3 && STORAGE_LABELS[v]) return STORAGE_LABELS[v];
  return d || v;
}

/**
 * Text to send as the chat `message` field: canonical API value (e.g. S/R) when present
 * so filters and expansion match; falls back to display.
 */
export function suggestedAnswerSubmitValue(sa: SuggestedAnswer): string {
  const v = (sa.value ?? '').trim();
  if (v) return v;
  return (sa.displayText ?? '').trim();
}

/** Whether this suggestion should be hidden after a failed try (match by value, display, or resolved label). */
export function isSuggestedAnswerExcluded(sa: SuggestedAnswer, failed: Set<string>): boolean {
  const v = (sa.value ?? '').trim();
  const d = (sa.displayText ?? '').trim();
  const label = suggestedAnswerDisplayLabel(sa);
  if (v && failed.has(v)) return true;
  if (d && failed.has(d)) return true;
  if (failed.has(label)) return true;
  return false;
}
