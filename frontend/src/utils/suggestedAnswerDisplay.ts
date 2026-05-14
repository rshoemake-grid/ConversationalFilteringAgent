import type { SuggestedAnswer } from '../api/types';

/** Short storage codes from GCP conversational filtering (unnamed productAttributeValue). */
const STORAGE_LABELS: Record<string, string> = {
  S: 'Ambient',
  R: 'Refrigerated',
  D: 'Dry storage',
  N: 'Non-refrigerated',
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
  const pack = formatNumericDoubleUnderscoreUom(v);
  if (pack) return pack;
  return d || v;
}

/** Catalog values like `3__LB` → `3 lb` for chip labels (matches backend BrandDisplayResolver). */
function formatNumericDoubleUnderscoreUom(value: string): string | null {
  const v = value.trim();
  const sep = v.indexOf('__');
  if (sep <= 0 || sep >= v.length - 2) return null;
  const numPart = v.slice(0, sep);
  const unitPart = v.slice(sep + 2);
  if (!/^\d+(?:\.\d+)?$/.test(numPart) || !/^[A-Za-z][A-Za-z0-9_]*$/.test(unitPart)) return null;
  return `${numPart} ${humanizeUomToken(unitPart)}`;
}

function humanizeUomToken(rawUnit: string): string {
  const u = rawUnit.toUpperCase().replace(/_/g, '');
  const map: Record<string, string> = {
    LB: 'lb',
    LBS: 'lb',
    OZ: 'oz',
    KG: 'kg',
    G: 'g',
    GM: 'g',
    GRAM: 'g',
    GRAMS: 'g',
    CT: 'ct',
    CNT: 'ct',
    COUNT: 'ct',
    PK: 'pk',
    PACK: 'pk',
    EA: 'each',
    EACH: 'each',
    ML: 'ml',
    L: 'L',
    LT: 'L',
    LTR: 'L',
    GAL: 'gal',
  };
  if (map[u]) return map[u];
  return rawUnit.toLowerCase().replace(/_/g, ' ');
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
