package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.orchestration.RetailProductApiGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitialCatalogAggregatorTest {

    @Mock
    RetailSearchClient searchClient;

    ConversationalCommerceConfig config = new ConversationalCommerceConfig();
    InitialCatalogAggregator aggregator;

    @BeforeEach
    void setUp() {
        config.setPlacement("pl");
        config.setBranch("br");
        config.setInitialCatalogPageSize(10);
        config.setInitialCatalogFetchAllPages(false);
        config.setInitialCatalogParallelPageFetches(false);
        config.setProductSearchPageSize(5);
        config.setRetailSingleShotPerConversation(false);
        aggregator = new InitialCatalogAggregator(searchClient, config, new RetailProductApiGate(config));
    }

    @Test
    void whenRetailSingleShotBlocks_skipsSearchClient() {
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);
        gate.beginChatTurn("c-block", "s-block");
        gate.noteRetailProductListingCommitted("rice", null);
        gate.endChatTurn();
        gate.beginChatTurn("c-block", "s-block");
        aggregator = new InitialCatalogAggregator(searchClient, config, gate);

        SearchResult out = aggregator.searchCatalog("pl", "br", "rice", "v", null, null, null);

        assertThat(out.products()).isEmpty();
        verifyNoInteractions(searchClient);
        gate.endChatTurn();
    }

    @Test
    void whenRetailSingleShotAllowsNewCatalogQueryAfterFirstFingerprint() {
        config.setRetailSingleShotPerConversation(true);
        var gate = new RetailProductApiGate(config);
        gate.beginChatTurn("c-newq", "s-newq");
        gate.noteRetailProductListingCommitted("rice", null);
        gate.endChatTurn();
        gate.beginChatTurn("c-newq", "s-newq");
        aggregator = new InitialCatalogAggregator(searchClient, config, gate);
        var p = AgentResponse.ProductResult.of("u1", "Uncle Bens", "", "", null);
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("uncle bens"), any(), isNull(), isNull(), any(), isNull()))
                .thenReturn(SearchResult.of(List.of(p), null, 1));

        SearchResult out = aggregator.searchCatalog("pl", "br", "uncle bens", "v", null, null, null);

        assertThat(out.products()).hasSize(1);
        verify(searchClient).searchWithPagination(eq("pl"), eq("br"), eq("uncle bens"), any(), isNull(), isNull(), any(), isNull());
        gate.endChatTurn();
    }

    @Test
    void firstPageUsesInitialCatalogPageSizeAndIgnoresClientPageOverride() {
        config.setInitialCatalogPageSize(100);
        var p = AgentResponse.ProductResult.of("x", "T", "", "", null);
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("rice"), any(), isNull(), isNull(), eq(100), isNull()))
                .thenReturn(SearchResult.of(List.of(p), null, 50));

        SearchResult out = aggregator.searchCatalog("pl", "br", "rice", "v", null, null, 20);

        assertThat(out.products()).hasSize(1);
        assertThat(out.nextPageToken()).isNull();
        verify(searchClient, times(1)).searchWithPagination(any(), any(), any(), any(), any(), isNull(), eq(100), isNull());
    }

    @Test
    void pageTokenIgnored_stillUsesInitialCatalogPathOnly() {
        config.setInitialCatalogFetchAllPages(false);
        config.setInitialCatalogPageSize(100);
        var p = AgentResponse.ProductResult.of("x", "T", "", "", null);
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("rice"), any(), isNull(), isNull(), eq(100), isNull()))
                .thenReturn(SearchResult.of(List.of(p), null, 50));

        SearchResult out = aggregator.searchCatalog("pl", "br", "rice", "v", null, "stale-token", 7);

        assertThat(out.products()).hasSize(1);
        verify(searchClient, times(1)).searchWithPagination(any(), any(), any(), any(), any(), isNull(), eq(100), isNull());
    }

    @Test
    void fetchAllPages_mergesAndDedupesById() {
        config.setInitialCatalogFetchAllPages(true);
        config.setInitialCatalogParallelPageFetches(false);
        config.setInitialCatalogSuppressNextPageToken(true);
        config.setInitialCatalogMaxProducts(100);
        config.setInitialCatalogMaxPageRequests(10);
        var p1 = AgentResponse.ProductResult.of("id1", "A", "", "", null);
        var p2 = AgentResponse.ProductResult.of("id2", "B", "", "", null);
        var p1Dup = AgentResponse.ProductResult.of("id1", "A2", "", "", null);
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("q"), any(), isNull(), isNull(), eq(10), isNull()))
                .thenReturn(SearchResult.of(List.of(p1, p2), "next1", 100));
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("q"), any(), isNull(), eq("next1"), eq(10), isNull()))
                .thenReturn(SearchResult.of(List.of(p1Dup), null, 100));

        SearchResult out = aggregator.searchCatalog("pl", "br", "q", "v", null, null, null);

        assertThat(out.products()).hasSize(2);
        assertThat(out.products().stream().map(AgentResponse.ProductResult::id)).containsExactlyInAnyOrder("id1", "id2");
        assertThat(out.nextPageToken()).isNull();
    }

    @Test
    void fetchAllPages_parallelOffsets_mergesInOffsetOrder() {
        config.setInitialCatalogFetchAllPages(true);
        config.setInitialCatalogParallelPageFetches(true);
        config.setInitialCatalogSuppressNextPageToken(true);
        config.setInitialCatalogMaxProducts(25);
        config.setInitialCatalogMaxPageRequests(10);
        config.setInitialCatalogPageSize(10);
        var a = AgentResponse.ProductResult.of("a", "A", "", "", null);
        var b = AgentResponse.ProductResult.of("b", "B", "", "", null);
        var c = AgentResponse.ProductResult.of("c", "C", "", "", null);
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("q"), any(), isNull(), isNull(), eq(10), eq(0)))
                .thenReturn(SearchResult.of(List.of(a), null, 30));
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("q"), any(), isNull(), isNull(), eq(10), eq(10)))
                .thenReturn(SearchResult.of(List.of(b), null, 30));
        when(searchClient.searchWithPagination(eq("pl"), eq("br"), eq("q"), any(), isNull(), isNull(), eq(10), eq(20)))
                .thenReturn(SearchResult.of(List.of(c), null, 30));

        SearchResult out = aggregator.searchCatalog("pl", "br", "q", "v", null, null, null);

        assertThat(out.products()).extracting(AgentResponse.ProductResult::id).containsExactly("a", "b", "c");
        verify(searchClient, times(3)).searchWithPagination(any(), any(), eq("q"), any(), isNull(), isNull(), eq(10), any(Integer.class));
    }
}
