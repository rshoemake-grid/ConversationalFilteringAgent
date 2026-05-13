package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.orchestration.RetailProductApiGate;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * PoC-oriented Retail Search for the <strong>initial catalog listing</strong>: large page size, optional
 * multi-page fetch into one list (parallel {@code offset} requests by default), and optional suppression of
 * {@code nextPageToken} so the UI does not paginate the first population against Google—Paging of the
 * returned list is expected on the app/API side. Refinement against an in-memory pool uses a different path
 * ({@link ConversationalCommerceAdapter#refineInMemoryProductPool}).
 * <p>
 * {@code pageToken} and {@code requestPageSizeOverride} are <strong>ignored</strong> (reserved for API compatibility);
 * Retail Search is not used for listing continuation after the initial merged pull.
 */
@Component
public class InitialCatalogAggregator {

    private static final Logger log = LoggerFactory.getLogger(InitialCatalogAggregator.class);

    /** Google Retail Search typical upper bound for {@code pageSize}. */
    private static final int PAGE_SIZE_CAP = 100;

    private final RetailSearchClient searchClient;
    private final ConversationalCommerceConfig config;
    private final RetailProductApiGate retailProductApiGate;
    private final ExecutorService catalogFetchExecutor;

    public InitialCatalogAggregator(RetailSearchClient searchClient, ConversationalCommerceConfig config,
                                    RetailProductApiGate retailProductApiGate) {
        this.searchClient = searchClient;
        this.config = config;
        this.retailProductApiGate = retailProductApiGate;
        int threads = Math.max(1, config.initialCatalogMaxConcurrentRequests());
        this.catalogFetchExecutor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "initial-catalog-fetch");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdownExecutor() {
        catalogFetchExecutor.shutdown();
        try {
            if (!catalogFetchExecutor.awaitTermination(8, TimeUnit.SECONDS)) {
                catalogFetchExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            catalogFetchExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param pageToken               ignored (no Retail continuation)
     * @param requestPageSizeOverride ignored (no Retail continuation)
     */
    public SearchResult searchCatalog(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            String pageToken,
            Integer requestPageSizeOverride) {
        if (!retailProductApiGate.mayQueryRetailSearchApis()) {
            return SearchResult.of(List.of());
        }
        if (pageToken != null && !pageToken.isBlank()) {
            log.debug("Ignoring pageToken in searchCatalog: Retail listing continuation is disabled.");
        }

        int initialPs = clampPageSize(config.initialCatalogPageSize());
        if (!config.initialCatalogFetchAllPages()) {
            retailProductApiGate.noteRetailProductListingCommitted();
            SearchResult sr = searchClient.searchWithPagination(
                    placement, branch, query, visitorId, filter, null, initialPs, null);
            return applyInitialSuppress(sr);
        }
        if (config.initialCatalogParallelPageFetches()) {
            return fetchAllPagesParallelOffsets(placement, branch, query, visitorId, filter, initialPs);
        }
        return fetchAllPagesSequentialTokens(placement, branch, query, visitorId, filter, initialPs);
    }

    private SearchResult fetchAllPagesParallelOffsets(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            int pageSize) {
        int maxProducts = Math.max(1, config.initialCatalogMaxProducts());
        int maxPagesAllowed = Math.max(1, config.initialCatalogMaxPageRequests());
        int pagesNeeded = (maxProducts + pageSize - 1) / pageSize;
        int nPages = Math.min(maxPagesAllowed, Math.max(1, pagesNeeded));

        retailProductApiGate.noteRetailProductListingCommitted();
        List<Future<OffsetPageResult>> futures = new ArrayList<>(nPages);
        for (int i = 0; i < nPages; i++) {
            final int pageIndex = i;
            final int offset = i * pageSize;
            futures.add(catalogFetchExecutor.submit(() -> {
                SearchResult sr = searchClient.searchWithPagination(
                        placement, branch, query, visitorId, filter, null, pageSize, offset);
                return new OffsetPageResult(pageIndex, sr);
            }));
        }

        OffsetPageResult[] byIndex = new OffsetPageResult[nPages];
        try {
            for (Future<OffsetPageResult> f : futures) {
                OffsetPageResult slice = f.get();
                byIndex[slice.pageIndex] = slice;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel initial catalog fetch interrupted");
            return SearchResult.of(List.of());
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            log.warn("Parallel initial catalog fetch failed: {}", c.getMessage());
            return SearchResult.of(List.of());
        }

        Map<String, AgentResponse.ProductResult> byKey = new LinkedHashMap<>();
        long totalSize = -1;
        if (byIndex[0] != null && byIndex[0].searchResult().totalSize() >= 0) {
            totalSize = byIndex[0].searchResult().totalSize();
        }
        for (int p = 0; p < nPages; p++) {
            OffsetPageResult slice = byIndex[p];
            if (slice == null) {
                continue;
            }
            for (AgentResponse.ProductResult prod : slice.searchResult().products()) {
                if (byKey.size() >= maxProducts) {
                    break;
                }
                byKey.putIfAbsent(dedupeKey(prod), prod);
            }
            if (byKey.size() >= maxProducts) {
                break;
            }
        }

        List<AgentResponse.ProductResult> list = new ArrayList<>(byKey.values());
        if (list.size() > maxProducts) {
            list = list.subList(0, maxProducts);
        }
        if (totalSize < 0) {
            totalSize = list.size();
        }
        log.debug("Initial catalog parallel aggregate: {} pages, {} products (cap {})", nPages, list.size(), maxProducts);
        return SearchResult.of(list, null, totalSize);
    }

    private SearchResult fetchAllPagesSequentialTokens(
            String placement,
            String branch,
            String query,
            String visitorId,
            String filter,
            int pageSize) {
        Map<String, AgentResponse.ProductResult> byKey = new LinkedHashMap<>();
        String fetchToken = null;
        long totalSize = -1;
        int maxProducts = Math.max(1, config.initialCatalogMaxProducts());
        int maxPages = Math.max(1, config.initialCatalogMaxPageRequests());
        String nextContinuation = null;

        retailProductApiGate.noteRetailProductListingCommitted();
        for (int page = 0; page < maxPages; page++) {
            SearchResult sr = searchClient.searchWithPagination(
                    placement, branch, query, visitorId, filter, fetchToken, pageSize, null);
            if (sr.totalSize() >= 0) {
                totalSize = sr.totalSize();
            }
            for (AgentResponse.ProductResult p : sr.products()) {
                if (byKey.size() >= maxProducts) {
                    break;
                }
                byKey.putIfAbsent(dedupeKey(p), p);
            }
            boolean atCapacity = byKey.size() >= maxProducts;
            String next = sr.nextPageToken();
            if (next != null && !next.isBlank()) {
                nextContinuation = next;
            } else {
                nextContinuation = null;
            }
            if (atCapacity) {
                break;
            }
            if (next == null || next.isBlank()) {
                break;
            }
            fetchToken = next;
        }

        List<AgentResponse.ProductResult> list = new ArrayList<>(byKey.values());
        if (list.size() > maxProducts) {
            list = list.subList(0, maxProducts);
        }
        if (totalSize < 0) {
            totalSize = list.size();
        }
        log.debug("Initial catalog sequential aggregate: {} products (cap {})", list.size(), maxProducts);
        String expose = config.initialCatalogSuppressNextPageToken() ? null : nextContinuation;
        return SearchResult.of(list, expose, totalSize);
    }

    private SearchResult applyInitialSuppress(SearchResult sr) {
        if (sr == null) {
            return SearchResult.of(List.of());
        }
        if (config.initialCatalogSuppressNextPageToken()) {
            return SearchResult.of(sr.products(), null, sr.totalSize());
        }
        return sr;
    }

    private static String dedupeKey(AgentResponse.ProductResult p) {
        if (p.id() != null && !p.id().isBlank()) {
            return p.id();
        }
        return "fallback:" + Objects.hash(p.title(), p.productId(), p.gtin(), p.description());
    }

    private static int clampPageSize(int v) {
        if (v <= 0) {
            return PAGE_SIZE_CAP;
        }
        return Math.min(v, PAGE_SIZE_CAP);
    }

    private record OffsetPageResult(int pageIndex, SearchResult searchResult) {}
}
