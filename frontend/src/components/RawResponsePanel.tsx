import { useMemo, useRef, useEffect } from 'react';
import type { ChatResponse } from '../api/types';

interface HistoryEntry {
  rawResponse?: string | null;
  fallbackResponse?: ChatResponse | null;
  /** True when every suggested chip for this response was tried and returned no products */
  exhaustedSuggestedChipsNoProducts?: boolean;
  /** Unique productAttributeValue.name values parsed from raw (e.g. attributes.brands) */
  filterableAttributeNamesFromRaw?: string[];
}

interface RawResponsePanelProps {
  history: HistoryEntry[];
}

function prettyPrintJson(raw: string): string {
  try {
    const parsed = JSON.parse(raw);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return raw;
  }
}

function formatFilterableHint(entry: HistoryEntry): string | null {
  if (!entry.exhaustedSuggestedChipsNoProducts) return null;
  const names = entry.filterableAttributeNamesFromRaw;
  const lines: string[] = [
    '=== Suggested chips exhausted (no products) — filterable attribute names from raw JSON ===',
  ];
  if (names && names.length > 0) {
    for (const n of names) lines.push(`- ${n}`);
  } else {
    lines.push(
      '(No productAttributeValue.name on suggestions in this raw payload — chips may use unnamed facets; see Raw GCP JSON below.)'
    );
  }
  return lines.join('\n');
}

/** Show full raw GCP API response (all attributes) first, then our processed response. */
function formatEntry(entry: HistoryEntry): string {
  const parts: string[] = [];
  const hint = formatFilterableHint(entry);
  if (hint) parts.push(hint);
  // Raw GCP API response - contains all available attributes (conversationalSearchResult, conversationalFilteringResult, suggestedAnswers with productAttributeValue, etc.)
  if (entry.rawResponse?.trim()) {
    parts.push('=== Raw GCP API Response ===\n' + prettyPrintJson(entry.rawResponse));
  }
  // Our processed response (text, products with attributes, suggestedAnswers, etc.)
  if (entry.fallbackResponse) {
    const resp = { ...entry.fallbackResponse } as Record<string, unknown>;
    // Include parsed rawResponse in processed so product/suggestion attributes are visible
    if (typeof resp.rawResponse === 'string' && resp.rawResponse.trim()) {
      try {
        resp.rawResponse = JSON.parse(resp.rawResponse) as unknown;
      } catch {
        /* keep as string */
      }
    }
    parts.push('=== Processed Response ===\n' + JSON.stringify(resp, null, 2));
  }
  return parts.join('\n\n');
}

export function RawResponsePanel({ history }: RawResponsePanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const content = useMemo(() => {
    const formatted = history.map(formatEntry).filter(Boolean);
    return formatted.length > 0 ? formatted.join('\n\n---\n\n') : null;
  }, [history]);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [content]);

  return (
    <div className="raw-response-panel">
      <h3 className="raw-response-panel__title">Raw API Response</h3>
      <div ref={scrollRef} className="raw-response-panel__scroll">
        <pre className="raw-response-panel__content">
          {content ?? 'Send a message to see the API response.'}
        </pre>
      </div>
    </div>
  );
}
