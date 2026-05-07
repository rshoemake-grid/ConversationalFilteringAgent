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
        return searchWithPagination(placement, branch, query, visitorId, filter, null).products();
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
        return searchWithPagination(placement, branch, query, visitorId, filter, pageToken, null);
    }

    /** Overload with page size override for per-request control. */
    SearchResult searchWithPagination(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            String pageToken,
            Integer pageSizeOverride
    );
}
