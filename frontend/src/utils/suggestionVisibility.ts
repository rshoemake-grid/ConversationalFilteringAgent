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
