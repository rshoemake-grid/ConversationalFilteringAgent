package com.conversationalcommerce.agent.agent;

import java.util.Map;

/**
 * Keeps a broader Retail {@code totalSize} from the prior turn for UI copy when the current search is a
 * narrowing continuation (smaller {@code totalSize} from Retail after applying more constraints).
 */
public final class ProductTotalSizeBaseline {

    private ProductTotalSizeBaseline() {
    }

    public record Adjusted(long totalSize, boolean totalSizeIsApproximate) {
    }

    /**
     * @param totalSize              current computed display total (≥0 or unknown as -1)
     * @param totalSizeIsApproximate whether the current total was estimated (e.g. pages×pageSize)
     * @param context                request context; reads {@code previousProductFilter},
     *                               {@code previousProductTotalSize}, {@code previousProductTotalSizeIsApproximate}
     */
    public static Adjusted adjustNarrowingDisplayTotal(
            long totalSize,
            boolean totalSizeIsApproximate,
            Map<String, Object> context) {
        if (totalSize < 0) {
            return new Adjusted(totalSize, totalSizeIsApproximate);
        }
        String prevFilter = (String) context.get("previousProductFilter");
        if (prevFilter == null || prevFilter.isBlank()) {
            return new Adjusted(totalSize, totalSizeIsApproximate);
        }
        Object p = context.get("previousProductTotalSize");
        if (!(p instanceof Number prevNum)) {
            return new Adjusted(totalSize, totalSizeIsApproximate);
        }
        long prev = prevNum.longValue();
        if (prev < 0 || prev <= totalSize) {
            return new Adjusted(totalSize, totalSizeIsApproximate);
        }
        boolean prevApprox = Boolean.TRUE.equals(context.get("previousProductTotalSizeIsApproximate"));
        return new Adjusted(prev, totalSizeIsApproximate || prevApprox);
    }
}
