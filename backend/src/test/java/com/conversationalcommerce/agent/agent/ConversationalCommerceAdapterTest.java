package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.gemini.ClarifyingQuestionGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceAdapterTest {

    private ConversationalCommerceAdapter adapter;
    private StubConversationalCommerceClient stubClient;
    private StubRetailSearchClient stubSearchClient;
    private ConversationalCommerceConfig config;

    @BeforeEach
    void setUp() {
        config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/locations/global/catalogs/default_catalog/placements/default_search");
        config.setBranch("projects/p/locations/global/catalogs/default_catalog/branches/default_branch");
        config.setDefaultVisitorId("test-visitor");

        stubClient = new StubConversationalCommerceClient();
        stubSearchClient = new StubRetailSearchClient();
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());
    }

    @Test
    void getAgentId_returnsConversationalCommerceId() {
        assertThat(adapter.getAgentId()).isEqualTo(ConversationalCommerceAdapter.AGENT_ID);
    }

    @Test
    void sendMessage_returnsResponseFromClient() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are some options",
                "conv-123",
                "refined query",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse response = adapter.sendMessage("", "show me shoes", Map.of("orchestrationMode", "convo_commerce"));

        assertThat(response.text()).isEqualTo("Here are some options");
        assertThat(response.conversationId()).isEqualTo("conv-123");
        assertThat(response.refinedQuery()).isEqualTo("refined query");
        assertThat(response.queryType()).isEqualTo("SIMPLE_PRODUCT_SEARCH");
    }

    @Test
    void sendMessage_usesVisitorIdFromContext() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Response", "conv-1", null, null, "agent", null, List.of()
        ));

        adapter.sendMessage("", "hi", Map.of("visitorId", "custom-visitor"));

        assertThat(stubClient.lastRequest.visitorId()).isEqualTo("custom-visitor");
    }

    @Test
    void sendMessage_fetchesProductsWhenRefinedQueryPresent() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are shoes", "conv-1", "running shoes", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse response = adapter.sendMessage("", "running shoes", Map.of());

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Nike Run");
    }

    @Test
    void sendMessage_returnsHelpfulMessageWhenNoProductsAndSearchingFallback() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Searching for: fresh jumbo shrimp options", "conv-1", "fresh jumbo shrimp options", "SIMPLE_PRODUCT_SEARCH", "app", null, List.of()
        ));
        stubSearchClient.setProducts(List.of());

        AgentResponse response = adapter.sendMessage("", "what options do I have?", Map.of());

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("No products found.");
    }

    @Test
    void sendMessage_usesPreviousAgentResponseWhenSimpleProductSearchNoProductsAndNoFollowup() {
        // User clicked suggested answer "Nike" but no products; we have previous assistant context
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Searching for: Nike",
                "conv-1",
                "Nike",
                "SIMPLE_PRODUCT_SEARCH",
                "app",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of());

        var context = Map.<String, Object>of(
                "previousAssistantText", "Which brand would you prefer?",
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "Nike", "value", "Nike"),
                        Map.of("displayText", "Adidas", "value", "Adidas"),
                        Map.of("displayText", "Puma", "value", "Puma")
                )
        );

        AgentResponse response = adapter.sendMessage("", "Nike", context);

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("Which brand would you prefer?");
        assertThat(response.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::value).containsExactlyInAnyOrder("Adidas", "Puma");
        assertThat(response.source()).isEqualTo("app");
    }

    @Test
    void sendMessage_expandsShortStorageTypeBeforeSendingToApi() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("F", "FROZEN", "C", "REFRIGERATED")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are frozen shrimp options",
                "conv-1",
                "frozen shrimp",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Frozen Shrimp", "Frozen", "$10", null)
        ));

        adapter.sendMessage("", "F", Map.of());

        assertThat(stubClient.lastRequest.query()).isEqualTo("FROZEN");
    }

    @Test
    void sendMessage_sendsShortCodeD_WhenUserSelectsDryStorage() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of(
                "dry storage", "D", "Dry storage", "D",
                "F", "FROZEN", "C", "REFRIGERATED"
        )));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are dry storage rice options",
                "conv-1",
                "rice dry storage",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Long Grain Rice", "Dry", "$5", null)
        ));

        adapter.sendMessage("", "D", Map.of());

        assertThat(stubClient.lastRequest.query()).isEqualTo("D");
    }

    @Test
    void sendMessage_chainsExpansionWhenPreviousSuggestedAnswerHasShortValue() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("C", "REFRIGERATED")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are refrigerated options",
                "conv-1",
                "refrigerated shrimp",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Refrigerated Shrimp", "Cold", "$12", null)
        ));

        var context = Map.<String, Object>of(
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "Refrigerated", "value", "C")
                )
        );

        adapter.sendMessage("", "Refrigerated", context);

        assertThat(stubClient.lastRequest.query()).isEqualTo("REFRIGERATED");
    }

    @Test
    void sendMessage_showsNoProductsFoundWithAgentContextWhenNoProductsButHasSuggestedAnswers() {
        var suggestedAnswers = List.of(
                new ConversationalCommerceClient.SuggestedAnswer("BHB/NPM", "BHB/NPM"),
                new ConversationalCommerceClient.SuggestedAnswer("Bbrlcls", "Bbrlcls")
        );
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Which brand would you prefer?",
                "conv-1",
                "beef",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                suggestedAnswers
        ));
        stubSearchClient.setProducts(List.of());

        AgentResponse response = adapter.sendMessage("", "I'm looking for beef", Map.of());

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("Which brand would you prefer?\n\nNo products found.");
        assertThat(response.suggestedAnswers()).containsExactlyElementsOf(suggestedAnswers);
        assertThat(response.source()).isEqualTo("app");
    }

    @Test
    void sendMessage_usesGeneratedClarifyingQuestionWhenGeneratorAvailable() {
        config.setProductCountThreshold(8);
        var stubGenerator = new ClarifyingQuestionGenerator(null, null) {
            @Override
            public String generate(String refinedQuery, int productCount) {
                return "What type of shrimp are you looking for? Raw, cooked, or peeled?";
            }
        };
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.of(stubGenerator));

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are options", "conv-1", "Shrimp", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        var manyProducts = List.of(
                AgentResponse.ProductResult.of("p1", "Shrimp 1", "Desc", "$10", null),
                AgentResponse.ProductResult.of("p2", "Shrimp 2", "Desc", "$12", null),
                AgentResponse.ProductResult.of("p3", "Shrimp 3", "Desc", "$14", null),
                AgentResponse.ProductResult.of("p4", "Shrimp 4", "Desc", "$16", null),
                AgentResponse.ProductResult.of("p5", "Shrimp 5", "Desc", "$18", null),
                AgentResponse.ProductResult.of("p6", "Shrimp 6", "Desc", "$20", null),
                AgentResponse.ProductResult.of("p7", "Shrimp 7", "Desc", "$22", null),
                AgentResponse.ProductResult.of("p8", "Shrimp 8", "Desc", "$24", null),
                AgentResponse.ProductResult.of("p9", "Shrimp 9", "Desc", "$26", null)
        );
        stubSearchClient.setProducts(manyProducts);

        AgentResponse response = adapter.sendMessage("", "what other options?", Map.of());

        assertThat(response.products()).hasSize(9);
        assertThat(response.text()).contains("Showing 9 of");
        assertThat(response.clarifyingQuestion()).contains("What type of shrimp are you looking for? Raw, cooked, or peeled?");
    }

    @Test
    void sendMessage_asksClarifyingQuestionWhenManyProductsReturned() {
        config.setProductCountThreshold(8);
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are options", "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        var manyProducts = List.of(
                AgentResponse.ProductResult.of("p1", "Shoe 1", "Desc", "$50", null),
                AgentResponse.ProductResult.of("p2", "Shoe 2", "Desc", "$60", null),
                AgentResponse.ProductResult.of("p3", "Shoe 3", "Desc", "$70", null),
                AgentResponse.ProductResult.of("p4", "Shoe 4", "Desc", "$80", null),
                AgentResponse.ProductResult.of("p5", "Shoe 5", "Desc", "$90", null),
                AgentResponse.ProductResult.of("p6", "Shoe 6", "Desc", "$100", null),
                AgentResponse.ProductResult.of("p7", "Shoe 7", "Desc", "$110", null),
                AgentResponse.ProductResult.of("p8", "Shoe 8", "Desc", "$120", null),
                AgentResponse.ProductResult.of("p9", "Shoe 9", "Desc", "$130", null)
        );
        stubSearchClient.setProducts(manyProducts);

        AgentResponse response = adapter.sendMessage("", "shoes", Map.of());

        assertThat(response.products()).hasSize(9);
        assertThat(response.text()).contains("Showing 9 of");
        assertThat(response.clarifyingQuestion()).contains("narrow it down");
    }

    @Test
    void sendMessage_convoCommerceMode_usesApiResponseWhenManyProducts() {
        config.setProductCountThreshold(8);
        var stubGenerator = new ClarifyingQuestionGenerator(null, null) {
            @Override
            public String generate(String refinedQuery, int productCount) {
                return "Gemini-generated question";
            }
        };
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.of(stubGenerator));

        String apiResponse = "I found many shoes. Could you tell me more about what you're looking for?";
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                apiResponse, "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        var manyProducts = List.of(
                AgentResponse.ProductResult.of("p1", "Shoe 1", "Desc", "$50", null),
                AgentResponse.ProductResult.of("p2", "Shoe 2", "Desc", "$60", null),
                AgentResponse.ProductResult.of("p3", "Shoe 3", "Desc", "$70", null),
                AgentResponse.ProductResult.of("p4", "Shoe 4", "Desc", "$80", null),
                AgentResponse.ProductResult.of("p5", "Shoe 5", "Desc", "$90", null),
                AgentResponse.ProductResult.of("p6", "Shoe 6", "Desc", "$100", null),
                AgentResponse.ProductResult.of("p7", "Shoe 7", "Desc", "$110", null),
                AgentResponse.ProductResult.of("p8", "Shoe 8", "Desc", "$120", null),
                AgentResponse.ProductResult.of("p9", "Shoe 9", "Desc", "$130", null)
        );
        stubSearchClient.setProducts(manyProducts);

        var context = Map.<String, Object>of("orchestrationMode", "convo_commerce");
        AgentResponse response = adapter.sendMessage("", "shoes", context);

        assertThat(response.products()).hasSize(9);
        assertThat(response.text()).contains("Showing 9 of");
        assertThat(response.clarifyingQuestion()).isEqualTo(apiResponse);
    }

    @Test
    void sendMessage_noPreferenceRecovery_whenSimpleProductSearchRegression() {
        // User picked D (stock), then "Any" for rice type; API regresses to stock question with empty refinedQuery
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "What type of stock do you prefer?",
                "conv-1",
                null,
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of(new ConversationalCommerceClient.SuggestedAnswer("S", "S"),
                        new ConversationalCommerceClient.SuggestedAnswer("R", "R"),
                        new ConversationalCommerceClient.SuggestedAnswer("D", "D"))
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Long Grain Rice", "White rice", "$5", null),
                AgentResponse.ProductResult.of("p2", "Basmati Rice", "Aromatic rice", "$7", null)
        ));

        var context = Map.<String, Object>of(
                "previousRefinedQuery", "long grain white rice"
        );

        AgentResponse response = adapter.sendMessage("conv-1", "Any", context);

        assertThat(response.products()).hasSize(2);
        assertThat(response.text()).contains("Showing 2 of");
        assertThat(response.refinedQuery()).isEqualTo("long grain white rice");
    }

    @Test
    void sendMessage_noPreferenceRecovery_returnsProductsEvenWhenOverThreshold_inConvoCommerceMode() {
        // RETAIL_IRRELEVANT + "Any" for brand: 20 products, convo_commerce mode - must show products (not clear)
        var manyProducts = List.of(
                AgentResponse.ProductResult.of("p1", "Rice 1", "Desc", "$5", null),
                AgentResponse.ProductResult.of("p2", "Rice 2", "Desc", "$6", null),
                AgentResponse.ProductResult.of("p3", "Rice 3", "Desc", "$7", null),
                AgentResponse.ProductResult.of("p4", "Rice 4", "Desc", "$8", null),
                AgentResponse.ProductResult.of("p5", "Rice 5", "Desc", "$9", null),
                AgentResponse.ProductResult.of("p6", "Rice 6", "Desc", "$10", null),
                AgentResponse.ProductResult.of("p7", "Rice 7", "Desc", "$11", null),
                AgentResponse.ProductResult.of("p8", "Rice 8", "Desc", "$12", null),
                AgentResponse.ProductResult.of("p9", "Rice 9", "Desc", "$13", null),
                AgentResponse.ProductResult.of("p10", "Rice 10", "Desc", "$14", null),
                AgentResponse.ProductResult.of("p11", "Rice 11", "Desc", "$15", null),
                AgentResponse.ProductResult.of("p12", "Rice 12", "Desc", "$16", null),
                AgentResponse.ProductResult.of("p13", "Rice 13", "Desc", "$17", null),
                AgentResponse.ProductResult.of("p14", "Rice 14", "Desc", "$18", null),
                AgentResponse.ProductResult.of("p15", "Rice 15", "Desc", "$19", null),
                AgentResponse.ProductResult.of("p16", "Rice 16", "Desc", "$20", null),
                AgentResponse.ProductResult.of("p17", "Rice 17", "Desc", "$21", null),
                AgentResponse.ProductResult.of("p18", "Rice 18", "Desc", "$22", null),
                AgentResponse.ProductResult.of("p19", "Rice 19", "Desc", "$23", null),
                AgentResponse.ProductResult.of("p20", "Rice 20", "Desc", "$24", null)
        );
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "I didn't understand your response.",
                "conv-1",
                null,
                "RETAIL_IRRELEVANT",
                "app",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(manyProducts);

        var context = Map.<String, Object>of(
                "previousRefinedQuery", "white medium grain rice",
                "orchestrationMode", "convo_commerce"
        );

        AgentResponse response = adapter.sendMessage("conv-1", "Any", context);

        assertThat(response.products()).hasSize(20);
        assertThat(response.text()).contains("Showing 20 of");
    }

    @Test
    void sendMessage_convoCommerceMode_passesThroughAgentResponse() {
        config.setProductCountThreshold(8);
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Searching for: shrimp", "conv-1", "shrimp", "SIMPLE_PRODUCT_SEARCH", "app", null, List.of()
        ));
        var manyProducts = List.of(
                AgentResponse.ProductResult.of("p1", "Shrimp 1", "Desc", "$10", null),
                AgentResponse.ProductResult.of("p2", "Shrimp 2", "Desc", "$12", null),
                AgentResponse.ProductResult.of("p3", "Shrimp 3", "Desc", "$14", null),
                AgentResponse.ProductResult.of("p4", "Shrimp 4", "Desc", "$16", null),
                AgentResponse.ProductResult.of("p5", "Shrimp 5", "Desc", "$18", null),
                AgentResponse.ProductResult.of("p6", "Shrimp 6", "Desc", "$20", null),
                AgentResponse.ProductResult.of("p7", "Shrimp 7", "Desc", "$22", null),
                AgentResponse.ProductResult.of("p8", "Shrimp 8", "Desc", "$24", null),
                AgentResponse.ProductResult.of("p9", "Shrimp 9", "Desc", "$26", null)
        );
        stubSearchClient.setProducts(manyProducts);

        var context = Map.<String, Object>of("orchestrationMode", "convo_commerce");
        AgentResponse response = adapter.sendMessage("", "shrimp", context);

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).isEqualTo("Searching for: shrimp");
    }

    private static class StubConversationalCommerceClient implements ConversationalCommerceClient {
        ConversationalCommerceRequest lastRequest;
        ConversationalCommerceResult nextResult;

        void setNextResult(ConversationalCommerceResult result) {
            this.nextResult = result;
        }

        @Override
        public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
            this.lastRequest = request;
            return nextResult != null ? nextResult : new ConversationalCommerceResult("", "", null, null, "agent", null, List.of());
        }
    }

    private static class StubRetailSearchClient implements RetailSearchClient {
        List<AgentResponse.ProductResult> products = List.of();
        String lastQuery;
        String lastFilter;
        /** When filter contains this substring, return empty; else return products. For testing auto-run when first search fails. */
        String returnEmptyWhenFilterContains;

        void setProducts(List<AgentResponse.ProductResult> products) {
            this.products = products;
        }

        void setReturnEmptyWhenFilterContains(String substring) {
            this.returnEmptyWhenFilterContains = substring;
        }

        @Override
        public SearchResult searchWithPagination(String placement, String branch, String query, String visitorId, String filter, String pageToken, Integer pageSizeOverride) {
            this.lastQuery = query;
            this.lastFilter = filter;
            if (returnEmptyWhenFilterContains != null && filter != null && filter.contains(returnEmptyWhenFilterContains)) {
                return SearchResult.of(List.of());
            }
            return SearchResult.of(products);
        }
    }

    @Test
    void sendMessage_oneSuggestedAnswerFromApi_autoRunsIt() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("S", "AMBIENT")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "What type of stock do you prefer?",
                "conv-1",
                "rice",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of(new ConversationalCommerceClient.SuggestedAnswer("S", "S"))
        ));
        stubSearchClient.setProducts(List.of(AgentResponse.ProductResult.of("p1", "Long Grain Rice", "Shelf", "$5", null)));

        AgentResponse response = adapter.sendMessage("", "rice", Map.of());

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Long Grain Rice");
        assertThat(response.text()).contains("Showing 1 of");
        assertThat(response.suggestedAnswers()).isEmpty();
        assertThat(stubSearchClient.lastFilter).satisfiesAnyOf(
                f -> assertThat(f).contains("stockType"),
                f -> assertThat(f).contains("storageType")
        );
    }

    @Test
    void sendMessage_storageTypeSelectionUsesPreviousRefinedQueryAndFilter() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("D", "DRY_STORAGE")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are dry storage options",
                "conv-1",
                "DRY_STORAGE",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                // GCP often echoes the same storage chips in the conversational payload even after selection;
                // adapter strips these when product search succeeded via storage recovery.
                List.of(
                        new ConversationalCommerceClient.SuggestedAnswer("S", "S"),
                        new ConversationalCommerceClient.SuggestedAnswer("R", "R"),
                        new ConversationalCommerceClient.SuggestedAnswer("D", "D")
                )
        ));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Long Grain Rice", "Dry rice", "$5", null)
        ));

        var context = Map.<String, Object>of(
                "previousRefinedQuery", "rice",
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "D", "value", "D")
                )
        );

        AgentResponse response = adapter.sendMessage("", "D", context);

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Long Grain Rice");
        assertThat(response.suggestedAnswers()).isEmpty();
        assertThat(stubSearchClient.lastQuery).isEqualTo("rice");
        assertThat(stubSearchClient.lastFilter).contains("attributes.").contains("D");
    }

    @Test
    void sendMessage_storageTypeRecoveryWithProducts_keepsNonStorageSuggestionsFromApi() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("R", "REFRIGERATED")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Pick a brand?",
                "conv-1",
                "REFRIGERATED",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of(
                        new ConversationalCommerceClient.SuggestedAnswer("R", "R"),
                        new ConversationalCommerceClient.SuggestedAnswer("NIKE", "NIKE")
                )
        ));
        stubSearchClient.setProducts(List.of(AgentResponse.ProductResult.of("p1", "Milk", "Cold", "$3", null)));

        var context = Map.<String, Object>of(
                "previousRefinedQuery", "milk",
                "previousSuggestedAnswers", List.of(Map.of("displayText", "R", "value", "R"))
        );

        AgentResponse response = adapter.sendMessage("", "R", context);

        assertThat(response.products()).hasSize(1);
        assertThat(response.suggestedAnswers()).hasSize(1);
        assertThat(response.suggestedAnswers().get(0).value()).isEqualTo("NIKE");
    }

    @Test
    void sendMessage_storageTypeRecoveryNoProducts_reasksPreviousQuestionWithRemainingOptions() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("D", "DRY_STORAGE")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are dry storage options",
                "conv-1",
                "DRY_STORAGE",
                "SIMPLE_PRODUCT_SEARCH",
                "agent",
                null,
                List.of()
        ));
        stubSearchClient.setProducts(List.of());

        var context = Map.<String, Object>of(
                "previousRefinedQuery", "rice",
                "previousAssistantText", "What type of stock do you prefer?",
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "S", "value", "S"),
                        Map.of("displayText", "R", "value", "R"),
                        Map.of("displayText", "D", "value", "D")
                )
        );

        AgentResponse response = adapter.sendMessage("", "D", context);

        assertThat(response.products()).isEmpty();
        assertThat(response.text()).startsWith("No products found for that option.");
        assertThat(response.text()).contains("What type of stock do you prefer?");
        assertThat(response.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactlyInAnyOrder("Ambient", "Refrigerated");
    }

    @Test
    void sendMessage_storageTypeRecoveryNoProducts_oneOptionRemaining_autoRunsQuery() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("S", "AMBIENT", "R", "REFRIGERATED", "D", "DRY_STORAGE")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Options", "conv-1", "REFRIGERATED", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        stubSearchClient.setProducts(List.of(AgentResponse.ProductResult.of("p1", "Long Grain Rice", "Shelf", "$5", null)));
        stubSearchClient.setReturnEmptyWhenFilterContains("R");  // R search fails, auto-run S succeeds

        // After D failed, we showed S and R. User clicks R, it fails; remaining = [S]. We auto-run S.
        var context = Map.<String, Object>of(
                "previousRefinedQuery", "rice",
                "previousAssistantText", "No products found for that option.\n\nWhat type of stock do you prefer?",
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "S", "value", "S"),
                        Map.of("displayText", "R", "value", "R")
                )
        );

        AgentResponse response = adapter.sendMessage("", "R", context);

        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).isEqualTo("Long Grain Rice");
        assertThat(response.text()).contains("Showing 1 of");
        assertThat(response.suggestedAnswers()).isEmpty();
        assertThat(stubSearchClient.lastQuery).isEqualTo("rice");
        assertThat(stubSearchClient.lastFilter).contains("stockType").contains("S");
    }

    @Test
    void sendMessage_storageTypeRecoveryNoProducts_doesNotDuplicatePrefixWhenPrevTextAlreadyHasIt() {
        config.setAttributeValueExpansion(Map.of("storageType", Map.of("S", "AMBIENT", "R", "REFRIGERATED", "D", "DRY_STORAGE")));
        adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());

        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Options", "conv-1", "REFRIGERATED", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()
        ));
        stubSearchClient.setProducts(List.of());

        // After D failed we showed S, R. User clicks R; remaining = [S, D] so we don't auto-run (2 options left)
        var context = Map.<String, Object>of(
                "previousRefinedQuery", "rice",
                "previousAssistantText", "No products found for that option.\n\nWhat type of stock do you prefer?",
                "previousSuggestedAnswers", List.of(
                        Map.of("displayText", "S", "value", "S"),
                        Map.of("displayText", "R", "value", "R"),
                        Map.of("displayText", "D", "value", "D")
                )
        );

        AgentResponse response = adapter.sendMessage("", "R", context);

        assertThat(response.text()).isEqualTo("No products found for that option.\n\nWhat type of stock do you prefer?");
        assertThat(response.suggestedAnswers()).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactlyInAnyOrder("Ambient", "Dry storage");
    }
}
