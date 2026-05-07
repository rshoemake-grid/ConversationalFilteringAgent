package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.SearchResult;
import com.conversationalcommerce.agent.agent.ConversationalCommerceAdapter;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.agent.ProductEnrichmentService;
import com.conversationalcommerce.agent.agent.RetailSearchClient;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConvoCommerceOrchestratorTest {

    private ConvoCommerceOrchestrator orchestrator;
    private StubConversationalCommerceClient stubClient;
    private StubRetailSearchClient stubSearchClient;

    @BeforeEach
    void setUp() {
        var config = new ConversationalCommerceConfig();
        config.setPlacement("projects/p/placements/default");
        config.setBranch("projects/p/branches/default");

        stubClient = new StubConversationalCommerceClient();
        stubSearchClient = new StubRetailSearchClient();
        var adapter = new ConversationalCommerceAdapter(stubClient, stubSearchClient, new ProductEnrichmentService(Optional.empty()), config, Optional.empty());
        orchestrator = new ConvoCommerceOrchestrator(adapter);
    }

    @Test
    void process_returnsConvoResponseForProductSearch() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "Here are shoes", "conv-1", "shoes", "SIMPLE_PRODUCT_SEARCH", "agent", null, List.of()));
        stubSearchClient.setProducts(List.of(
                AgentResponse.ProductResult.of("p1", "Nike Run", "Running shoes", "$99", null)
        ));

        AgentResponse r = orchestrator.process("shoes", "", Map.of());

        assertThat(r.text()).contains("Here are shoes");
    }

    @Test
    void process_returnsConvoResponseForGeneralQuestion() {
        stubClient.setNextResult(new ConversationalCommerceClient.ConversationalCommerceResult(
                "General question", "conv-1", null, "GENERAL_QUESTION", "agent", null, List.of()));

        AgentResponse r = orchestrator.process("store hours?", "", Map.of());

        assertThat(r.text()).contains("General question");
    }

    private static class StubConversationalCommerceClient implements ConversationalCommerceClient {
        ConversationalCommerceResult nextResult;

        void setNextResult(ConversationalCommerceResult r) {
            this.nextResult = r;
        }

        @Override
        public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
            return nextResult != null ? nextResult : new ConversationalCommerceResult("", "", null, null, "agent", null, List.of());
        }
    }

    private static class StubRetailSearchClient implements RetailSearchClient {
        List<AgentResponse.ProductResult> products = List.of();

        void setProducts(List<AgentResponse.ProductResult> products) {
            this.products = products;
        }

        @Override
        public SearchResult searchWithPagination(String placement, String branch, String query, String visitorId, String filter, String pageToken, Integer pageSizeOverride) {
            return SearchResult.of(products);
        }
    }
}
