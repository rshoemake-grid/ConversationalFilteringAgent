package com.conversationalcommerce.agent.agent;

import java.util.Optional;

/**
 * Fetches full product details from GCP Retail API (Product.Get).
 * Used to enrich search results when fields like description or price are missing.
 */
public interface ProductFetcher {

    /**
     * Fetches a product by its full resource name.
     *
     * @param productName Full resource name, e.g. projects/.../products/6052075
     * @return The full product, or empty if not found or on error
     */
    Optional<AgentResponse.ProductResult> getProduct(String productName);
}
