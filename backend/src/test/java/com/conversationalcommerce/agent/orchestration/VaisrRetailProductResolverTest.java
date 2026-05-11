package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.agent.ProductEnrichmentService;
import com.conversationalcommerce.agent.agent.RetailSearchClient;
import com.conversationalcommerce.agent.agent.SearchResult;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaisrRetailProductResolverTest {

    @Mock
    RetailSearchClient searchClient;

    ConversationalCommerceConfig config = new ConversationalCommerceConfig();

    VaisrRetailProductResolver resolver;

    @BeforeEach
    void setUp() {
        config.setPlacement("p");
        config.setBranch("b");
        config.setProductCountThreshold(50);
        resolver = new VaisrRetailProductResolver(
                searchClient,
                new ProductEnrichmentService(Optional.empty()),
                config,
                Optional.empty());
    }

    @Test
    void resolvesAmbientSelectionUsingPreviousRefinedQueryAndRunsRetailSearch() {
        var pr = AgentResponse.ProductResult.of("id1", "Rice A", "d", "$1", "");
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"S\")"), eq(null), eq(null)))
                .thenReturn(SearchResult.of(List.of(pr), null, 3));

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "ok", "c-gcp", "rice", "SIMPLE_PRODUCT_SEARCH", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of(
                "visitorId", "v1",
                "previousRefinedQuery", "rice",
                "previousSuggestedAnswers",
                List.of(Map.of("displayText", "Ambient", "value", "S")));

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "Ambient", ctx);

        assertThat(aug.products()).hasSize(1);
        assertThat(aug.refinedQuery()).isEqualTo("rice");
        assertThat(aug.productFilter()).contains("stockType");
    }

    @Test
    void storageRecoveryRunsWithOnlyPreviousRefinedQueryAndNoSuggestedAnswersList() {
        var pr = AgentResponse.ProductResult.of("id1", "Rice A", "d", "$1", "");
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"D\")"), eq(null), eq(null)))
                .thenReturn(SearchResult.of(List.of(pr), null, 3));

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "ignore va isr fluff", "c-gcp", "D", "INTENT_REFINEMENT", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of(
                "visitorId", "v1",
                "previousRefinedQuery", "rice");

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "D", ctx);

        assertThat(aug.products()).hasSize(1);
        assertThat(aug.refinedQuery()).isEqualTo("rice");
        assertThat(aug.text()).doesNotContain("different types of rice");
    }
}
