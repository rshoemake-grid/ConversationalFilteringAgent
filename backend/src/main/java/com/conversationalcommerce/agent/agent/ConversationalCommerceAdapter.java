package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import com.conversationalcommerce.agent.orchestration.ContextUtils;
import com.conversationalcommerce.agent.orchestration.LastFilteringQuestionStore;
import com.conversationalcommerce.agent.ranking.VertexAiRankingService;
import com.conversationalcommerce.agent.web.ChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter that wraps the GCP Conversational Commerce API as a ConversationalAgent.
 */
@Component
public class ConversationalCommerceAdapter implements ConversationalAgent {

    public static final String AGENT_ID = "conversational-commerce";
    private static final String FALLBACK_CLARIFY = "I found many %s matching your search. To narrow it down, could you tell me more? " +
            "For example: What type or form? (e.g. raw, cooked, peeled — or style, color) Any size or price range?";

    private static final Logger log = LoggerFactory.getLogger(ConversationalCommerceAdapter.class);

    private final ConversationalCommerceClient client;
    private final ProductEnrichmentService enrichmentService;
    private final ConversationalCommerceConfig config;
    private final Optional<ClarifyingQuestionGenerator> clarifyingGenerator;
    private final ProductPoolNarrower productPoolNarrower;
    private final VertexAiRankingService vertexAiRankingService;
    private final LastFilteringQuestionStore lastFilteringQuestionStore;
    private final InitialCatalogAggregator initialCatalogAggregator;

    public ConversationalCommerceAdapter(
            ConversationalCommerceClient client,
            ProductEnrichmentService enrichmentService,
            ConversationalCommerceConfig config,
            Optional<ClarifyingQuestionGenerator> clarifyingGenerator,
            ProductPoolNarrower productPoolNarrower,
            VertexAiRankingService vertexAiRankingService,
            LastFilteringQuestionStore lastFilteringQuestionStore,
            InitialCatalogAggregator initialCatalogAggregator) {
        this.client = client;
        this.enrichmentService = enrichmentService;
        this.config = config;
        this.clarifyingGenerator = clarifyingGenerator != null ? clarifyingGenerator : Optional.empty();
        this.productPoolNarrower = productPoolNarrower;
        this.vertexAiRankingService = vertexAiRankingService;
        this.lastFilteringQuestionStore = lastFilteringQuestionStore;
        this.initialCatalogAggregator = initialCatalogAggregator;
    }

    @Override
    public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
        String visitorId = getVisitorId(context);
        String productPageToken = (String) context.get("productPageToken");
        String prevRefinedForLoadMore = (String) context.get("previousRefinedQuery");
        String prevFilter = (String) context.get("previousProductFilter");
        Integer productPageSizeOverride = context.get("productPageSize") instanceof Number n ? n.intValue() : null;

        // Legacy clients may send productPageToken; we do not call Retail for continuation — the initial merge returns the full list (up to caps).
        if (productPageToken != null && !productPageToken.isBlank() && prevRefinedForLoadMore != null && !prevRefinedForLoadMore.isBlank()) {
            log.debug("Ignoring productPageToken: Retail product pagination after the initial listing is disabled.");
            return AgentResponse.builder()
                    .text("All products for this search are already included above. Scroll the list or refine your request.")
                    .conversationId(conversationId != null ? conversationId : "")
                    .refinedQuery(prevRefinedForLoadMore.trim())
                    .products(List.of())
                    .queryType("SIMPLE_PRODUCT_SEARCH")
                    .source("app")
                    .productTotalSize(-1)
                    .productNextPageToken(null)
                    .productFilter(prevFilter)
                    .build();
        }

        @SuppressWarnings("unchecked")
        List<ChatRequest.ProductPoolInput> poolInputs =
                (List<ChatRequest.ProductPoolInput>) context.get("productPool");
        if (poolInputs != null && !poolInputs.isEmpty()) {
            if (!shouldSkipProductPoolForStorageSelection(message, context)) {
                return refineInMemoryProductPool(conversationId, message, context, visitorId, poolInputs);
            }
            log.debug("Storage-type chip with product pool: using full catalog search + stock filter instead of pool-only refine.");
        }

        String imageBase64 = (String) context.get("imageBase64");
        String userInput = (message != null && !message.isBlank()) ? message.trim()
                : (imageBase64 != null ? "Find products similar to this image" : "");
        final String query = expandShortAttributeValue(userInput, context);
        final String originalUserInput = userInput;

        var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                config.placement(),
                config.branch(),
                query,
                visitorId,
                conversationId != null ? conversationId : "",
                imageBase64
        );

        var result = client.search(request);

        List<AgentResponse.ProductResult> products = List.of();
        SearchResult searchResult = null;
        String productFilterUsed = null;
        String searchQuery = result.refinedQuery();
        boolean usedNoPreferenceRecovery = false;
        boolean usedStorageTypeRecovery = false;
        String storageTypeFilter = null;

        // When user said "Any"/"no preference" and API returns empty refinedQuery, use previous refined query to search.
        if (isNoPreference(query) && (searchQuery == null || searchQuery.isEmpty())) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                usedNoPreferenceRecovery = true;
                log.debug("No-preference recovery (queryType={}): using previousRefinedQuery \"{}\"", result.queryType(), searchQuery);
            }
        }

        // When user selected a storage-type suggested answer (S, R, D), use previousRefinedQuery for product search
        // and add storage filter. Avoids treating "DRY_STORAGE" as search text (matches "dry storage" in names).
        if (!usedNoPreferenceRecovery && isStorageTypeSelection(query, originalUserInput, context)) {
            String prevRefined = (String) context.get("previousRefinedQuery");
            if (prevRefined != null && !prevRefined.isBlank()) {
                searchQuery = prevRefined.trim();
                storageTypeFilter = buildStorageTypeFilter(expandStorageTypeValue(query));
                usedStorageTypeRecovery = true;
                log.debug("Storage-type selection recovery: using previousRefinedQuery \"{}\" + filter {}", searchQuery, storageTypeFilter);
            }
        }

        String sessionFilterForMerge = sanitizeSessionProductFilter((String) context.get("previousProductFilter"));
        searchQuery = RetailListingQueryResolver.chooseSearchQuery(
                query,
                searchQuery,
                usedStorageTypeRecovery,
                usedNoPreferenceRecovery,
                result.queryType(),
                (String) context.get("previousRefinedQuery"),
                sessionFilterForMerge);

        if (searchQuery != null && !searchQuery.isEmpty()) {
            try {
                String filter = buildBrandFilterWhenApplicable(query, result.suggestedAnswers(), context);
                String packFacet = PackFacetRetailFilter.buildPackFacetFilterIfSuggestionSelected(
                        query, result.suggestedAnswers(), context);
                filter = ConversationalCommerceAdapter.combineRetailFilters(filter, packFacet);
                if (storageTypeFilter != null) {
                    filter = (filter != null && !filter.isBlank())
                            ? filter + " AND " + storageTypeFilter
                            : storageTypeFilter;
                }
                String session = sessionFilterForMerge;
                if (storageTypeFilter != null) {
                    session = RetailSessionFilterUtils.stripStorageAttributeAnyFilters(session);
                }
                filter = combineRetailFilters(session, filter);
                productFilterUsed = filter;
                searchResult = initialCatalogAggregator.searchCatalog(
                        config.placement(),
                        config.branch(),
                        searchQuery,
                        visitorId,
                        filter,
                        null,
                        null);
                products = enrichmentService.enrich(searchResult.products());
            } catch (Exception e) {
                log.warn("Product search failed (may use gRPC; try transport=rest for full REST): {}", e.getMessage());
            }
        }

        String text = result.text() != null ? result.text() : "";
        if (usedNoPreferenceRecovery || usedStorageTypeRecovery) {
            if (!products.isEmpty()) {
                int n = products.size();
                text = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
            } else if (!usedStorageTypeRecovery) {
                text = "No products found.";
            }
            // When storage-type recovery returns no products, fall through to previous-agent fallback below
        }
        List<AgentResponse.ProductResult> productsToReturn = products;
        int productCountThreshold = config.productCountThreshold();
        if (products.isEmpty()) {
            log.info("Products empty; userQueryType={}", result.queryType() != null ? result.queryType() : "(none)");
        }
        String refinedQuery = (searchQuery != null && !searchQuery.isBlank())
                ? searchQuery.trim()
                : result.refinedQuery();
        boolean isSearchingFallback = text.startsWith("Searching for:");
        boolean useConvoCommerceOnly = "convo_commerce".equals(context.get("orchestrationMode"));
        boolean hasRefinedQuery = refinedQuery != null && !refinedQuery.isEmpty();

        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers = result.suggestedAnswers() != null ? result.suggestedAnswers() : List.of();
        String responseSource = result.source() != null ? result.source() : "agent";

        // When only one suggested answer is given, auto-run it instead of asking
        boolean usedAutoRunSingleSuggestion = false;
        if (suggestedAnswers.size() == 1 && "SIMPLE_PRODUCT_SEARCH".equals(result.queryType())) {
            String baseQuery = (String) context.get("previousRefinedQuery");
            if (baseQuery == null || baseQuery.isBlank()) baseQuery = result.refinedQuery();
            if (baseQuery != null && !baseQuery.isBlank()) {
                String soleValue = suggestedAnswers.get(0).value();
                if (soleValue != null && !soleValue.isBlank()) {
                    var autoResult = tryAutoRunSingleSuggestion(baseQuery.trim(), soleValue, result.suggestedAnswers(), context, visitorId);
                    if (autoResult != null) {
                        products = autoResult.products();
                        productsToReturn = autoResult.products();
                        text = autoResult.text();
                        suggestedAnswers = List.of();
                        responseSource = "app";
                        usedAutoRunSingleSuggestion = true;
                        if (autoResult.filterUsed() != null && !autoResult.filterUsed().isBlank()) {
                            productFilterUsed = autoResult.filterUsed();
                        }
                    }
                }
            }
        }

        // When SIMPLE_PRODUCT_SEARCH / INTENT_REFINEMENT or storage-type recovery, no products: show previous question with suggested answers minus the one tried
        boolean simpleProductSearchNoProducts = ("SIMPLE_PRODUCT_SEARCH".equals(result.queryType()) || "INTENT_REFINEMENT".equals(result.queryType()))
                && products.isEmpty()
                && (text == null || text.isEmpty() || text.startsWith("Searching for:"));
        boolean storageTypeRecoveryNoProducts = usedStorageTypeRecovery && products.isEmpty();
        boolean usedPreviousAgentFallback = false;
        if (simpleProductSearchNoProducts || storageTypeRecoveryNoProducts) {
            String prevText = (String) context.get("previousAssistantText");
            @SuppressWarnings("unchecked")
            var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
            List<ConversationalCommerceClient.SuggestedAnswer> remaining = prevList != null && !prevList.isEmpty()
                    ? prevList.stream()
                            .filter(m -> !originalUserInput.trim().equals(m.getOrDefault("value", "").trim())
                                    && !originalUserInput.trim().equalsIgnoreCase(m.getOrDefault("displayText", "").trim()))
                            .map(m -> new ConversationalCommerceClient.SuggestedAnswer(
                                    m.getOrDefault("displayText", m.getOrDefault("value", "")),
                                    m.getOrDefault("value", "")))
                            .toList()
                    : (suggestedAnswers != null ? suggestedAnswers.stream()
                            .filter(sa -> {
                                String v = sa.value() != null ? sa.value().trim() : "";
                                String d = sa.displayText() != null ? sa.displayText().trim() : "";
                                return !originalUserInput.trim().equals(v)
                                        && !originalUserInput.trim().equalsIgnoreCase(d);
                            })
                            .toList() : List.of());

            // When only one storage option remains, auto-run it instead of re-asking (no previousAssistantText required).
            if (storageTypeRecoveryNoProducts && remaining.size() == 1) {
                String prevRefined = (String) context.get("previousRefinedQuery");
                if (prevRefined != null && !prevRefined.isBlank()) {
                    String soleValue = remaining.get(0).value();
                    String filterValue = expandStorageTypeValue(soleValue);
                    if (filterValue == null && soleValue != null) {
                        filterValue = soleValue.trim();
                    }
                    if (filterValue != null) {
                        try {
                            String stFilter = combineRetailFilters(
                                    RetailSessionFilterUtils.stripStorageAttributeAnyFilters(
                                            sanitizeSessionProductFilter((String) context.get("previousProductFilter"))),
                                    buildStorageTypeFilter(filterValue));
                            SearchResult autoSr = initialCatalogAggregator.searchCatalog(
                                    config.placement(), config.branch(), prevRefined.trim(), visitorId, stFilter, null, null);
                            List<AgentResponse.ProductResult> autoProducts = enrichmentService.enrich(autoSr.products());
                            products = autoProducts;
                            productsToReturn = autoProducts;
                            searchResult = autoSr;
                            if (!autoProducts.isEmpty()) {
                                int n = autoProducts.size();
                                text = n == 1 ? "I found 1 product matching your request." : "I found " + n + " products matching your request.";
                                productFilterUsed = stFilter;
                                suggestedAnswers = List.of();
                            } else {
                                String body = storageReaskBody(prevText, true, context);
                                boolean already = body.contains("No products found for that option.");
                                text = (storageTypeRecoveryNoProducts && !already)
                                        ? "No products found for that option.\n\n" + body
                                        : body;
                                suggestedAnswers = remaining;
                                productFilterUsed = stFilter;
                            }
                            responseSource = "app";
                            usedPreviousAgentFallback = true;
                        } catch (Exception e) {
                            log.warn("Auto-run last storage option failed: {}", e.getMessage());
                        }
                    }
                }
            }

            if (!usedPreviousAgentFallback && !remaining.isEmpty()) {
                String body = storageReaskBody(prevText, storageTypeRecoveryNoProducts, context);
                boolean alreadyHasPrefix = body.contains("No products found for that option.");
                text = (storageTypeRecoveryNoProducts && !alreadyHasPrefix)
                        ? "No products found for that option.\n\n" + body
                        : body;
                suggestedAnswers = remaining;
                responseSource = "app";
                usedPreviousAgentFallback = true;
            }
        }
        int clarifyingCount = ClarifyingFollowUpPolicy.effectiveProductCountForClarifying(products, searchResult);
        boolean agentProvidedClarifying = text != null && !text.isEmpty() && !text.startsWith("Searching for:")
                && text.contains("?");
        String clarifyingQuestion = null; // Shown after products in UI
        if (useConvoCommerceOnly) {
            // Approach A: Pass through agent response as-is; no app overrides
            // When agent provided a clarifying question, show it with products (don't clear)
            // Never clear products when user said "Any" and we recovered with previousRefinedQuery
            if (!products.isEmpty() && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold) && !usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion && !agentProvidedClarifying) {
                productsToReturn = List.of();
            } else if (!products.isEmpty() && isSearchingFallback) {
                // Replace "Searching for: X" placeholder with a proper count when we have products
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                if (agentProvidedClarifying && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                    clarifyingQuestion = text;
                    text = countPhrase;
                } else {
                    text = countPhrase;
                }
                responseSource = "app";
            } else if (!products.isEmpty() && agentProvidedClarifying && !isSearchingFallback
                    && !usedNoPreferenceRecovery && !usedStorageTypeRecovery) {
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                if (ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                    clarifyingQuestion = text;
                }
                text = countPhrase;
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                // Skip if we already handled via previous-agent fallback above.
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                if (ClarifyingFollowUpPolicy.agentTextConflictsWithEmptyProductSearch(result.queryType())) {
                    text = "No products found.";
                } else {
                    text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                }
                responseSource = "app";
            }
        }
        if (!useConvoCommerceOnly) {
            // Approach B: Use our clarifying logic when many products; when agent provided one, show it with products
            if (!products.isEmpty() && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold) && !usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion) {
                String categoryHint = refinedQuery != null ? refinedQuery : "products";
                int productCount = clarifyingCount;
                int n = products.size();
                String countPhrase = n == 1
                        ? "I found 1 product matching your request."
                        : "I found " + n + " products matching your request.";
                String clarifyingText = agentProvidedClarifying
                        ? text
                        : (clarifyingGenerator
                                .map(gen -> gen.generate(categoryHint, productCount))
                                .filter(t -> t != null && !t.isBlank())
                                .orElse(FALLBACK_CLARIFY.formatted(categoryHint)));
                clarifyingQuestion = clarifyingText;
                text = countPhrase;
                productsToReturn = products;
                responseSource = "app";
            } else if (!products.isEmpty()) {
                if (!usedNoPreferenceRecovery && !usedStorageTypeRecovery && !usedAutoRunSingleSuggestion) {
                    int n = products.size();
                    String countPhrase = n == 1
                            ? "I found 1 product matching your request."
                            : "I found " + n + " products matching your request.";
                    if (!text.isEmpty() && !isSearchingFallback && text.contains("?")
                            && ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                        clarifyingQuestion = text;
                        text = countPhrase;
                    } else if (text.isEmpty() || isSearchingFallback) {
                        text = countPhrase;
                    } else if (ClarifyingFollowUpPolicy.shouldOfferClarifyingFollowUp(clarifyingCount, productCountThreshold)) {
                        text = countPhrase + "\n\n" + text;
                    } else {
                        if (text.contains("?")) {
                            text = countPhrase;
                        } else {
                            text = (text.isEmpty() || isSearchingFallback) ? countPhrase : countPhrase + "\n\n" + text;
                        }
                    }
                }
                responseSource = "app";
            } else if (products.isEmpty() && hasRefinedQuery && !usedPreviousAgentFallback) {
                // Always show "No products found" when search returns empty. Include agent context if meaningful (not placeholder).
                boolean hasMeaningfulAgentText = text != null && !text.isEmpty() && !text.startsWith("Searching for:");
                if (ClarifyingFollowUpPolicy.agentTextConflictsWithEmptyProductSearch(result.queryType())) {
                    text = "No products found.";
                } else {
                    text = (hasMeaningfulAgentText ? text + "\n\n" : "") + "No products found.";
                }
                responseSource = "app";
            }
        }

        // GCP often echoes stock/storage facet chips on the same turn as product hits (including after the user
        // picked a storage type). Strip those when we return products unless the clarifying line asks about
        // stock/storage (S/R/D chips are then valid answers). Non-storage chips (e.g. brands) are always preserved.
        if (productsToReturn != null && !productsToReturn.isEmpty()
                && !ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice(clarifyingQuestion)) {
            suggestedAnswers = ClarifyingFollowUpPolicy.withoutStorageSuggestions(suggestedAnswers);
        }

        if (suggestedAnswers.isEmpty() && ((text != null && text.contains("?")) || (clarifyingQuestion != null && clarifyingQuestion.contains("?")))) {
            suggestedAnswers = List.of(new ConversationalCommerceClient.SuggestedAnswer("Any", "ANY"));
        }
        suggestedAnswers = applyStorageTypeDisplayMapping(suggestedAnswers);
        // Ensure no-preference/storage-type recovery always returns products when we have them
        if ((usedNoPreferenceRecovery || usedStorageTypeRecovery) && !products.isEmpty()) {
            productsToReturn = products;
            responseSource = "app";
            log.info("{}: returning {} products", usedStorageTypeRecovery ? "Storage-type recovery" : "No-preference recovery", productsToReturn.size());
        }
        long totalSize = searchResult != null ? searchResult.totalSize() : -1;
        String nextPageToken = searchResult != null ? searchResult.nextPageToken() : null;
        boolean totalSizeIsApproximate = false;
        if (totalSize < 0 && productsToReturn != null && !productsToReturn.isEmpty()) {
            int pageSize = getEffectivePageSize(productPageSizeOverride);
            int pagesEstimate = (int) Math.ceil((double) productsToReturn.size() / pageSize)
                    + (nextPageToken != null && !nextPageToken.isBlank() ? 1 : 0);
            totalSize = Math.max(productsToReturn.size(), (long) pagesEstimate * pageSize);
            totalSizeIsApproximate = true;
        }
        var narrowedDisplayTotal = ProductTotalSizeBaseline.adjustNarrowingDisplayTotal(totalSize, totalSizeIsApproximate, context);
        totalSize = narrowedDisplayTotal.totalSize();
        totalSizeIsApproximate = narrowedDisplayTotal.totalSizeIsApproximate();
        // Normalize "I found N products" to "Showing N of Y products" when we have totalSize
        if (totalSize >= 0 && productsToReturn != null && !productsToReturn.isEmpty()
                && text != null && text.matches("I found \\d+ product(s)? matching your request\\.")) {
            String prefix = totalSizeIsApproximate ? "at least " : "";
            text = productsToReturn.size() == 1
                    ? "Showing 1 of " + prefix + totalSize + " product"
                    : "Showing " + productsToReturn.size() + " of " + prefix + totalSize + " products";
        }
        long rawCatalogTotal = searchResult != null ? searchResult.totalSize() : -1;
        if (shouldSuppressSuggestedAnswersForSinglePage(productsToReturn, rawCatalogTotal, productPageSizeOverride)) {
            suggestedAnswers = List.of();
        }
        return AgentResponse.builder()
                .text(text)
                .conversationId(result.conversationId())
                .refinedQuery(refinedQuery)
                .products(productsToReturn)
                .queryType(result.queryType())
                .source(responseSource)
                .rawResponse(result.rawResponse())
                .suggestedAnswers(suggestedAnswers)
                .productTotalSize(totalSize)
                .productTotalSizeIsApproximate(totalSizeIsApproximate)
                .productNextPageToken(nextPageToken)
                .productFilter(productFilterUsed)
                .clarifyingQuestion(clarifyingQuestion)
                .build();
    }

    /**
     * Follow-up path: conversational filtering + in-memory whittling of the product pool from the prior VAISR search;
     * optional Vertex AI semantic reranking does not hit the full Retail catalog for listing.
     */
    private AgentResponse refineInMemoryProductPool(
            String conversationId,
            String message,
            Map<String, Object> context,
            String visitorId,
            List<ChatRequest.ProductPoolInput> poolInputs) {

        List<AgentResponse.ProductResult> pool = ProductPoolMapper.toProductResults(poolInputs);
        if (pool.isEmpty()) {
            return AgentResponse.builder()
                    .text("No products were provided in the pool to refine.")
                    .conversationId(conversationId != null ? conversationId : "")
                    .products(List.of())
                    .queryType("SIMPLE_PRODUCT_SEARCH")
                    .source("app")
                    .productTotalSize(0)
                    .build();
        }

        String imageBase64 = (String) context.get("imageBase64");
        String userInput = (message != null && !message.isBlank()) ? message.trim()
                : (imageBase64 != null ? "Find products similar to this image" : "");
        final String query = expandShortAttributeValue(userInput, context);

        var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                config.placement(),
                config.branch(),
                query,
                visitorId,
                conversationId != null ? conversationId : "",
                imageBase64
        );

        var result = client.search(request);

        List<AgentResponse.ProductResult> narrowed = productPoolNarrower.narrow(
                pool,
                userInput,
                result.refinedQuery(),
                result.suggestedAnswers());

        Boolean useRerank = (Boolean) context.get("useSemanticReranking");
        boolean rerankAllowed = !Boolean.FALSE.equals(useRerank);
        String rankQuery = (result.refinedQuery() != null && !result.refinedQuery().isBlank())
                ? result.refinedQuery().trim()
                : userInput;

        if (rerankAllowed && vertexAiRankingService != null && narrowed.size() >= 2 && rankQuery != null && !rankQuery.isBlank()) {
            narrowed = vertexAiRankingService.rank(rankQuery, narrowed);
        }

        String refined = result.refinedQuery();
        String agentText = result.text() != null ? result.text() : "";
        String countPart = narrowed.isEmpty()
                ? "No products in your current selection matched that request."
                : (narrowed.size() == 1
                        ? "Showing 1 product from your selection."
                        : "Showing " + narrowed.size() + " products from your selection.");
        String text = (!agentText.isBlank() && !agentText.startsWith("Searching for:"))
                ? countPart + "\n\n" + agentText
                : (narrowed.isEmpty() && !agentText.isBlank() ? agentText : countPart);

        List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers =
                applyStorageTypeDisplayMapping(
                        result.suggestedAnswers() != null ? result.suggestedAnswers() : List.of());

        Integer poolPageOverride = context.get("productPageSize") instanceof Number n ? n.intValue() : null;
        if (!narrowed.isEmpty() && narrowed.size() <= getEffectivePageSize(poolPageOverride)) {
            suggestedAnswers = List.of();
        }

        return AgentResponse.builder()
                .text(text)
                .conversationId(result.conversationId())
                .refinedQuery(refined)
                .products(narrowed)
                .queryType(result.queryType())
                .source(result.source() != null ? result.source() : "agent")
                .rawResponse(result.rawResponse())
                .suggestedAnswers(suggestedAnswers)
                .productTotalSize(narrowed.size())
                .productTotalSizeIsApproximate(false)
                .productNextPageToken(null)
                .productFilter("in_memory_pool_refine")
                .build();
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    private String getVisitorId(Map<String, Object> context) {
        return ContextUtils.getVisitorId(context, config.defaultVisitorId());
    }

    private int getEffectivePageSize(Integer override) {
        if (override != null && override > 0) return override;
        return config.productSearchPageSize();
    }

    /** Expand short codes (e.g. F, C) to canonical values before sending to GCP to avoid RETAIL_IRRELEVANT. */
    private String expandShortAttributeValue(String query, Map<String, Object> context) {
        if (query == null || query.isBlank()) return query;
        String trimmed = query.trim();

        // 1. Try previousSuggestedAnswers: if user input matches displayText, use value (handles display text from click)
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        String resolved = trimmed;
        if (prevList != null) {
            for (var m : prevList) {
                String displayText = m.getOrDefault("displayText", "").trim();
                String value = m.getOrDefault("value", "").trim();
                if (!displayText.isEmpty() && trimmed.equalsIgnoreCase(displayText) && !value.equals(trimmed)) {
                    log.debug("Expanding user input \"{}\" to value \"{}\" from previous suggested answer", trimmed, value);
                    resolved = value;
                    break;
                }
            }
        }

        // 2. Run through attribute-value-expansion (e.g. C->REFRIGERATED, brands with spaces) - chain so short codes from step 1 get canonical
        var expansion = config.getAttributeValueExpansion();
        if (expansion != null) {
            for (var attrEntry : expansion.entrySet()) {
                if (attrEntry.getValue() == null) continue;
                String canonical = findExpansionMatch(attrEntry.getValue(), resolved);
                if (canonical != null && !canonical.equals(resolved)) {
                    log.debug("Expanding \"{}\" to \"{}\" for attribute {}", resolved, canonical, attrEntry.getKey());
                    return canonical;
                }
            }
        }

        return resolved;
    }

    private static String findExpansionMatch(java.util.Map<String, String> mapping, String input) {
        if (input == null || mapping == null) return null;
        if (mapping.containsKey(input)) return mapping.get(input);
        for (var e : mapping.entrySet()) {
            if (e.getKey().equalsIgnoreCase(input)) return e.getValue();
        }
        return null;
    }

    private static final java.util.Set<String> STORAGE_TYPE_VALUES = java.util.Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "F", "C", "S", "R", "D", "N"
    );

    private static final java.util.Set<String> NON_BRAND_VALUES = java.util.Set.of(
            "FROZEN", "REFRIGERATED", "AMBIENT", "DRY_STORAGE", "COLD", "F", "C", "S", "R", "D", "N", "ANY"
    );

    private static final java.util.Set<String> NO_PREFERENCE_PHRASES = java.util.Set.of(
            "ANY", "NO", "NONE", "NOPREFERENCE", "DON'T CARE", "DONT CARE", "DOESN'T MATTER",
            "DOESNT MATTER", "WHATEVER", "I DON'T CARE", "IDC"
    );

    private String storageReaskBody(String prevText, boolean storageRecovery, Map<String, Object> context) {
        if (prevText != null && !prevText.isBlank()) {
            return prevText.trim();
        }
        if (storageRecovery) {
            if (lastFilteringQuestionStore != null && context != null) {
                String key = ContextUtils.getSessionKey(context);
                var stored = lastFilteringQuestionStore.getLastQuestion(key);
                if (stored.isPresent()) {
                    return stored.get();
                }
            }
            return ClarifyingFollowUpPolicy.DEFAULT_STORAGE_CLARIFYING_REASK;
        }
        return "What would you like to try next?";
    }

    private static boolean isNoPreference(String input) {
        if (input == null || input.isBlank()) return false;
        String n = input.trim().toUpperCase().replaceAll("\\s+", " ");
        if (NO_PREFERENCE_PHRASES.contains(n)) return true;
        if (n.replace("'", "").replace(" ", "").equals("NOPREFERENCE")) return true;
        return n.equals("NO PREFERENCE") || n.startsWith("NO PREFERENCE");
    }

    /**
     * True when the turn is a storage/stock chip or equivalent (S, R, D, …), aligned with
     * {@link com.conversationalcommerce.agent.orchestration.VaisrRetailProductResolver}: if
     * {@code previousRefinedQuery} is set, we do not require {@code previousSuggestedAnswers}.
     */
    private static boolean isStorageTypeSelection(String expandedQuery, String originalUserInput, Map<String, Object> context) {
        if (expandedQuery == null || expandedQuery.isBlank() || !STORAGE_TYPE_VALUES.contains(expandedQuery.trim().toUpperCase())) {
            return false;
        }
        Object pr = context != null ? context.get("previousRefinedQuery") : null;
        String prevRefined = pr != null ? pr.toString().trim() : "";
        if (!prevRefined.isEmpty()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        var prevList = context != null ? (List<Map<String, String>>) context.get("previousSuggestedAnswers") : null;
        if (prevList == null) return false;
        String trimmed = originalUserInput != null ? originalUserInput.trim() : "";
        for (var m : prevList) {
            String displayText = m.getOrDefault("displayText", "").trim();
            String value = m.getOrDefault("value", "").trim();
            if (trimmed.equalsIgnoreCase(displayText) || trimmed.equals(value)) return true;
        }
        return false;
    }

    /**
     * In-memory pool refine is the wrong tool for storage chips: the pool is only the current page/slice,
     * and {@link ProductPoolNarrower} does not apply {@code attributes.stockType} filters.
     */
    private boolean shouldSkipProductPoolForStorageSelection(String message, Map<String, Object> context) {
        if (message == null || message.isBlank() || context == null) {
            return false;
        }
        String userInput = message.trim();
        String query = expandShortAttributeValue(userInput, context);
        return isStorageTypeSelection(query, userInput, context);
    }

    /** Build filter for stock/storage type. Uses config attribute (stockType default); canonical mode emits letter + long-form ANY(). */
    private String buildStorageTypeFilter(String expandedStorageValue) {
        if (expandedStorageValue == null || expandedStorageValue.isBlank()) {
            return null;
        }
        return StockTypeRetailFilter.buildStorageAttributeAnyClause(
                expandedStorageValue.trim(), config.stockTypeFilterAttribute(), config);
    }

    private static final java.util.Map<String, String> STORAGE_DISPLAY_DEFAULTS = java.util.Map.of(
            "S", "Ambient", "R", "Refrigerated", "D", "Dry storage",
            "F", "Frozen", "C", "Refrigerated",
            "N", "Non-refrigerated"
    );

    /** Apply storageType display mapping so S/R/D show as Ambient/Refrigerated/Dry storage. */
    private List<ConversationalCommerceClient.SuggestedAnswer> applyStorageTypeDisplayMapping(List<ConversationalCommerceClient.SuggestedAnswer> list) {
        if (list == null || list.isEmpty()) return list;
        var storageMap = config.getAttributeDisplayMapping() != null ? config.getAttributeDisplayMapping().get("storageType") : null;
        return list.stream()
                .map(sa -> {
                    String v = sa.value();
                    if (v == null) return sa;
                    String display = (storageMap != null && storageMap.containsKey(v)) ? storageMap.get(v) : STORAGE_DISPLAY_DEFAULTS.get(v);
                    if (display == null || display.equals(sa.displayText())) return sa;
                    return new ConversationalCommerceClient.SuggestedAnswer(display, v);
                })
                .toList();
    }

    /** Try to run a search for a single suggestion; returns null if not applicable or search fails. */
    private AutoRunResult tryAutoRunSingleSuggestion(String baseQuery, String value, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers,
                                                     Map<String, Object> context, String visitorId) {
        try {
            String session = sanitizeSessionProductFilter((String) context.get("previousProductFilter"));
            String normalized = (value != null ? value.trim().toUpperCase() : "");
            if (STORAGE_TYPE_VALUES.contains(normalized) || (value != null && STORAGE_TYPE_VALUES.contains(value.trim()))) {
                String filterValue = expandStorageTypeValue(value);
                if (filterValue == null) {
                    filterValue = value != null ? value.trim() : null;
                }
                String newFilter = buildStorageTypeFilter(filterValue);
                session = RetailSessionFilterUtils.stripStorageAttributeAnyFilters(session);
                String merged = combineRetailFilters(session, newFilter);
                SearchResult sr = initialCatalogAggregator.searchCatalog(
                        config.placement(), config.branch(), baseQuery, visitorId, merged, null, null);
                var prods = enrichmentService.enrich(sr.products());
                String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
                return new AutoRunResult(prods, msg, merged);
            }
            String brandFilter = buildBrandFilterWhenApplicable(value, suggestedAnswers, context);
            if (brandFilter != null) {
                String merged = combineRetailFilters(session, brandFilter);
                SearchResult sr = initialCatalogAggregator.searchCatalog(
                        config.placement(), config.branch(), baseQuery, visitorId, merged, null, null);
                var prods = enrichmentService.enrich(sr.products());
                String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
                return new AutoRunResult(prods, msg, merged);
            }
            String query = (baseQuery + " " + value).trim();
            SearchResult sr = initialCatalogAggregator.searchCatalog(
                    config.placement(), config.branch(), query, visitorId, session, null, null);
            var prods = enrichmentService.enrich(sr.products());
            String msg = prods.isEmpty() ? "No products found." : (prods.size() == 1 ? "I found 1 product matching your request." : "I found " + prods.size() + " products matching your request.");
            return new AutoRunResult(prods, msg, session);
        } catch (Exception e) {
            log.warn("Auto-run single suggestion failed: {}", e.getMessage());
            return null;
        }
    }

    private record AutoRunResult(List<AgentResponse.ProductResult> products, String text, String filterUsed) {}

    /** Expand short storage code or display text to the value used in Retail {@code attributes.*: ANY("…")}. */
    private String expandStorageTypeValue(String value) {
        return StockTypeRetailFilter.attributeValueForFilter(value, config);
    }

    /** Add brand filter only when user selected a suggested answer that looks like a brand (not storage type, not a search term like "shrimp"). */
    private String buildBrandFilterWhenApplicable(String canonicalValue, List<ConversationalCommerceClient.SuggestedAnswer> suggestedAnswers, Map<String, Object> context) {
        if (canonicalValue == null || canonicalValue.isBlank()) return null;
        String trimmed = canonicalValue.trim();
        if (trimmed.length() < 2 || trimmed.length() > 50) return null;
        if (NON_BRAND_VALUES.contains(trimmed.toUpperCase())) return null;
        if (BrandDisplayResolver.formatNumericDoubleUnderscoreUom(trimmed) != null) return null;
        boolean fromSelection = false;
        @SuppressWarnings("unchecked")
        var prevList = (List<Map<String, String>>) context.get("previousSuggestedAnswers");
        if (prevList != null) {
            for (var m : prevList) {
                if (trimmed.equals(m.getOrDefault("value", "").trim()) || trimmed.equalsIgnoreCase(m.getOrDefault("displayText", "").trim())) {
                    fromSelection = true;
                    break;
                }
            }
        }
        boolean matchesCurrentSuggested = suggestedAnswers != null && suggestedAnswers.stream().anyMatch(sa -> trimmed.equals(sa.value()));
        if ((fromSelection || matchesCurrentSuggested)
                && trimmed.matches("\\d{1,4}")) {
            return null;
        }
        if ((fromSelection || matchesCurrentSuggested)) {
            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
            return "brands: ANY(\"" + escaped + "\")";
        }
        return null;
    }

    /** Merge session-scoped Retail filter with the filter for this turn (AND). */
    static String combineRetailFilters(String previous, String next) {
        String a = previous != null ? previous.trim() : "";
        String b = next != null ? next.trim() : "";
        if (a.isEmpty()) return b.isEmpty() ? null : b;
        if (b.isEmpty()) return a;
        return a + " AND " + b;
    }

    /** Treat in-memory pool marker as absent for Retail filter merge. */
    static String sanitizeSessionProductFilter(String filter) {
        if (filter == null || filter.isBlank()) return null;
        if ("in_memory_pool_refine".equals(filter.trim())) return null;
        return filter.trim();
    }

    /** Hide follow-up chips when the catalog reports a total that fits in one page (and total is known). */
    private boolean shouldSuppressSuggestedAnswersForSinglePage(
            List<AgentResponse.ProductResult> products,
            long catalogTotalSize,
            Integer productPageSizeOverride) {
        if (products == null || products.isEmpty() || catalogTotalSize < 0) return false;
        int effPage = getEffectivePageSize(productPageSizeOverride);
        return catalogTotalSize <= effPage;
    }
}
