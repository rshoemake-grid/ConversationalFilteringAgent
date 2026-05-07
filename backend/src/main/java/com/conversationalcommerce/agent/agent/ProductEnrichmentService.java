package com.conversationalcommerce.agent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enriches products with missing description or price by calling Product.Get.
 * Only runs when ProductFetcher is available (REST transport).
 */
@Component
public class ProductEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ProductEnrichmentService.class);

    private final Optional<ProductFetcher> productFetcher;

    public ProductEnrichmentService(Optional<ProductFetcher> productFetcher) {
        this.productFetcher = productFetcher != null ? productFetcher : Optional.empty();
    }

    /**
     * Enriches products that have empty description or price by fetching full details via Product.Get.
     *
     * @param products Products from search
     * @return Enriched list (mutates items in place by replacing with merged result)
     */
    public List<AgentResponse.ProductResult> enrich(List<AgentResponse.ProductResult> products) {
        if (products == null || products.isEmpty() || productFetcher.isEmpty()) {
            return products;
        }
        var fetcher = productFetcher.get();
        var result = new ArrayList<AgentResponse.ProductResult>(products.size());
        for (var p : products) {
            if (!needsEnrichment(p)) {
                result.add(p);
                continue;
            }
            var full = fetcher.getProduct(p.id());
            if (full.isEmpty()) {
                result.add(p);
                continue;
            }
            var merged = merge(p, full.get());
            log.debug("Enriched product {} via Product.Get", p.id());
            result.add(merged);
        }
        return result;
    }

    private static boolean needsEnrichment(AgentResponse.ProductResult p) {
        if (p.id() == null || p.id().isBlank() || !p.id().contains("/products/")) {
            return false;
        }
        boolean missingDesc = p.description() == null || p.description().isBlank();
        boolean missingPrice = p.price() == null || p.price().isBlank();
        return missingDesc || missingPrice;
    }

    private static AgentResponse.ProductResult merge(AgentResponse.ProductResult fromSearch, AgentResponse.ProductResult fromGet) {
        String desc = (fromSearch.description() != null && !fromSearch.description().isBlank())
                ? fromSearch.description() : fromGet.description();
        String price = (fromSearch.price() != null && !fromSearch.price().isBlank())
                ? fromSearch.price() : fromGet.price();
        String imageUri = fromSearch.imageUri() != null ? fromSearch.imageUri() : fromGet.imageUri();
        String gtin = fromSearch.gtin() != null ? fromSearch.gtin() : fromGet.gtin();
        String productId = fromSearch.productId() != null ? fromSearch.productId() : fromGet.productId();
        var categories = fromSearch.categories() != null ? fromSearch.categories() : fromGet.categories();
        var brands = fromSearch.brands() != null ? fromSearch.brands() : fromGet.brands();
        String uri = fromSearch.uri() != null ? fromSearch.uri() : fromGet.uri();
        String availability = fromSearch.availability() != null ? fromSearch.availability() : fromGet.availability();
        var sizes = fromSearch.sizes() != null ? fromSearch.sizes() : fromGet.sizes();
        var materials = fromSearch.materials() != null ? fromSearch.materials() : fromGet.materials();
        var attributes = fromSearch.attributes() != null ? fromSearch.attributes() : fromGet.attributes();
        return new AgentResponse.ProductResult(
                fromSearch.id(),
                fromSearch.title(),
                desc != null ? desc : "",
                price != null ? price : "",
                imageUri,
                gtin,
                productId,
                categories,
                brands,
                uri,
                availability,
                sizes,
                materials,
                attributes,
                true  // detailsFetched
        );
    }
}
