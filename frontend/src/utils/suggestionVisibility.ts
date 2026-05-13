import type { Message } from '../api/types';

/**
 * When the catalog total is known and fits in one page, omit suggested-answer chips.
 * Aligns with backend suppression when Retail returns totalSize.
 */
export function shouldHideSuggestedAnswersFromResponse(
  products: Message['products'] | undefined,
  productTotalSize: number | undefined,
  productPageSize: number
): boolean {
  if (productPageSize <= 0) return false;
  const n = products?.length ?? 0;
  if (n === 0) return false;
  if (productTotalSize != null && productTotalSize >= 0) {
    return productTotalSize <= productPageSize;
  }
  return false;
}

export function shouldHideSuggestedAnswersForPage(msg: Message, productPageSize: number): boolean {
  return shouldHideSuggestedAnswersFromResponse(msg.products, msg.productTotalSize, productPageSize);
}

/**
 * UI wrapper: never suppress chips for an explicit clarifying follow-up — those are not stale facet echoes.
 */
export function shouldHideSuggestedAnswersForMessage(msg: Message, productPageSize: number): boolean {
  if (msg.clarifyingQuestion?.trim()) {
    return false;
  }
  return shouldHideSuggestedAnswersForPage(msg, productPageSize);
}
