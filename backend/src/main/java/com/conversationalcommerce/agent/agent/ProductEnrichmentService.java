package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Enriches products with missing description or price by calling Product.Get.
 * Only runs when ProductFetcher is available (REST transport).
 * Fetches run in parallel (bounded pool, same cap scale as {@link InitialCatalogAggregator}) so large
 * initial catalogs do not block the chat response for many minutes.
 */
@Component
public class ProductEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(ProductEnrichmentService.class);

    private final Optional<ProductFetcher> productFetcher;
    private final ExecutorService enrichExecutor;

    public ProductEnrichmentService(Optional<ProductFetcher> productFetcher,
                                    ConversationalCommerceConfig config) {
        this.productFetcher = productFetcher != null ? productFetcher : Optional.empty();
        int threads = 8;
        if (config != null) {
            threads = Math.max(1, Math.min(32, config.initialCatalogMaxConcurrentRequests()));
        }
        this.enrichExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "product-enrich");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdownExecutor() {
        enrichExecutor.shutdown();
        try {
            if (!enrichExecutor.awaitTermination(12, TimeUnit.SECONDS)) {
                enrichExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            enrichExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Enriches products that have empty description or price by fetching full details via Product.Get.
     *
     * @param products Products from search
     * @return Enriched list (same order as input)
     */
    public List<AgentResponse.ProductResult> enrich(List<AgentResponse.ProductResult> products) {
        if (products == null || products.isEmpty() || productFetcher.isEmpty()) {
            return products;
        }
        var fetcher = productFetcher.get();
        List<CompletableFuture<AgentResponse.ProductResult>> futures = new ArrayList<>(products.size());
        for (var p : products) {
            if (!needsEnrichment(p)) {
                futures.add(CompletableFuture.completedFuture(p));
            } else {
                AgentResponse.ProductResult fp = p;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    var full = fetcher.getProduct(fp.id());
                    if (full.isEmpty()) {
                        return fp;
                    }
                    var merged = merge(fp, full.get());
                    log.debug("Enriched product {} via Product.Get", fp.id());
                    return merged;
                }, enrichExecutor));
            }
        }
        return futures.stream().map(CompletableFuture::join).toList();
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
