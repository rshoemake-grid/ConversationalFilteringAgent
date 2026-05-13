/**
 * Unique `productAttributeValue.name` values (e.g. attributes.brands) from raw GCP
 * conversational JSON — same result shapes as suggestedAnswers extraction.
 */
export function extractFilterableAttributeNamesFromRaw(rawJson: string | null | undefined): string[] {
  if (!rawJson?.trim()) return [];
  const names = new Set<string>();

  const collectFromSuggestedItem = (item: unknown): void => {
    if (!item || typeof item !== 'object') return;
    const obj = item as Record<string, unknown>;
    const pav = obj.productAttributeValue ?? obj.product_attribute_value;
    if (pav && typeof pav === 'object') {
      const n = (pav as Record<string, unknown>).name;
      if (typeof n === 'string' && n.trim()) {
        names.add(n.trim());
      }
    }
  };

  const walkSuggestedArray = (arr: unknown): void => {
    if (!Array.isArray(arr)) return;
    for (const item of arr) collectFromSuggestedItem(item);
  };

  const parseChunk = (obj: unknown): void => {
    if (!obj || typeof obj !== 'object') return;
    const m = obj as Record<string, unknown>;
    const cfr = m.conversationalFilteringResult ?? m.conversational_filtering_result;
    if (cfr && typeof cfr === 'object') {
      const cfrObj = cfr as Record<string, unknown>;
      walkSuggestedArray(cfrObj.suggestedAnswers ?? cfrObj.suggested_answers);
      const fq = cfrObj.followupQuestion ?? cfrObj.followup_question;
      if (fq && typeof fq === 'object') {
        const fqObj = fq as Record<string, unknown>;
        walkSuggestedArray(fqObj.suggestedAnswers ?? fqObj.suggested_answers);
      }
    }
    const csr = m.conversationalSearchResult ?? m.conversational_search_result;
    if (csr && typeof csr === 'object') {
      const csrObj = csr as Record<string, unknown>;
      walkSuggestedArray(csrObj.suggestedAnswers ?? csrObj.suggested_answers);
    }
  };

  try {
    const parsed = JSON.parse(rawJson);
    if (Array.isArray(parsed)) {
      for (const chunk of parsed) parseChunk(chunk);
    } else {
      parseChunk(parsed);
    }
  } catch {
    /* not a single JSON value — try NDJSON below */
  }
  if (names.size === 0 && rawJson.includes('\n')) {
    for (const line of rawJson.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      try {
        parseChunk(JSON.parse(trimmed));
      } catch {
        // ignore
      }
    }
  }
  return [...names].sort((a, b) => a.localeCompare(b));
}
