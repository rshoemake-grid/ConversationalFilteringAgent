package com.conversationalcommerce.agent.agent;

import java.util.List;

/**
 * Abstraction for the GCP Retail Search API (product search).
 */
public interface RetailSearchClient {

    /**
     * Search for products and return results.
     *
     * @return list of product results, or empty list if none
     */
    default List<AgentResponse.ProductResult> search(
            String placement,
            String branch,
            String query,
            String visitorId
    ) {
        return search(placement, branch, query, visitorId, null);
    }

    /**
     * Search for products with an optional filter.
     *
     * @param filter optional filter expression (e.g. brands: ANY("Nike") or attributes.brandId: ANY("BHB/NPM"))
     * @return list of product results, or empty list if none
     */
    default List<AgentResponse.ProductResult> search(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter
    ) {
        return searchWithPagination(placement, branch, query, visitorId, filter, null, null).products();
    }

    /**
     * Search with pagination. Returns products, nextPageToken, and totalSize.
     *
     * @param pageToken optional token from a previous response to fetch next page
     * @param pageSizeOverride optional page size (null = use config default)
     */
    default SearchResult searchWithPagination(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            String pageToken
    ) {
        return searchWithPagination(placement, branch, query, visitorId, filter, pageToken, null, null);
    }

    /**
     * @param pageToken optional continuation token (mutually exclusive with {@code offset} on the Retail API)
     * @param pageSizeOverride page size (null = config default)
     * @param offset when non-null and &gt;= 0, Retail Search uses offset-based paging instead of {@code pageToken}
     */
    default SearchResult searchWithPagination(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            String pageToken,
            Integer pageSizeOverride
    ) {
        return searchWithPagination(placement, branch, query, visitorId, filter, pageToken, pageSizeOverride, null);
    }

    SearchResult searchWithPagination(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            String pageToken,
            Integer pageSizeOverride,
            Integer offset
    );
}
