package com.conversationalcommerce.agent.agent;

import java.util.List;

/** Result from product search with pagination metadata. */
public record SearchResult(
        List<AgentResponse.ProductResult> products,
        /** Token to fetch the next page, or null if no more pages. */
        String nextPageToken,
        /** Estimated total matching products (GCP may not provide; -1 if unknown). */
        long totalSize
) {
    public static SearchResult of(List<AgentResponse.ProductResult> products) {
        return new SearchResult(products != null ? products : List.of(), null, -1);
    }

    public static SearchResult of(List<AgentResponse.ProductResult> products, String nextPageToken, long totalSize) {
        return new SearchResult(products != null ? products : List.of(), nextPageToken, totalSize);
    }
}
