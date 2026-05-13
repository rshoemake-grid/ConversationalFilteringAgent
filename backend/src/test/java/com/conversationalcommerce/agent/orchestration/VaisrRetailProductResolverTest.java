package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.agent.ProductEnrichmentService;
import com.conversationalcommerce.agent.agent.InitialCatalogAggregator;
import com.conversationalcommerce.agent.agent.RetailSearchClient;
import com.conversationalcommerce.agent.agent.SearchResult;
import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.conversationalcommerce.agent.orchestration.RetailProductApiGate;
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
    InitialCatalogAggregator aggregator;
    LastFilteringQuestionStore lastQuestionStore;
    VaisrRetailProductResolver resolver;

    @BeforeEach
    void setUp() {
        config.setPlacement("p");
        config.setBranch("b");
        config.setProductCountThreshold(50);
        config.setInitialCatalogFetchAllPages(false);
        config.setRetailSingleShotPerConversation(false);
        var gate = new RetailProductApiGate(config);
        aggregator = new InitialCatalogAggregator(searchClient, config, gate);
        lastQuestionStore = new LastFilteringQuestionStore();
        resolver = new VaisrRetailProductResolver(
                aggregator,
                new ProductEnrichmentService(Optional.empty(), config),
                config,
                Optional.empty(),
                lastQuestionStore);
    }

    @Test
    void resolvesAmbientSelectionUsingPreviousRefinedQueryAndRunsRetailSearch() {
        var pr = AgentResponse.ProductResult.of("id1", "Rice A", "d", "$1", "");
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"AMBIENT\", \"S\")"), eq(null), any(), eq(null)))
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
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")"), eq(null), any(), eq(null)))
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

    @Test
    void storageRecoveryWithNoProducts_reasksWithRemainingSuggestedAnswers() {
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")"), eq(null), any(), eq(null)))
                .thenReturn(SearchResult.of(List.of(), null, 0));

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "No products found.", "c-gcp", "rice", "INTENT_REFINEMENT", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of(
                "visitorId", "v1",
                "previousRefinedQuery", "rice",
                "previousAssistantText", "What type of stock do you prefer?",
                "previousSuggestedAnswers",
                List.of(
                        Map.of("displayText", "Ambient", "value", "S"),
                        Map.of("displayText", "Refrigerated", "value", "R"),
                        Map.of("displayText", "Dry storage", "value", "D")));

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "D", ctx);

        assertThat(aug.products()).isEmpty();
        assertThat(aug.text()).contains("What type of stock do you prefer?");
        assertThat(aug.text()).contains("No products found for that option.");
        assertThat(aug.suggestedAnswersOverride()).isNotNull();
        assertThat(aug.suggestedAnswersOverride()).hasSize(2);
        assertThat(aug.suggestedAnswersOverride())
                .extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactlyInAnyOrder("S", "R");
    }

    @Test
    void storageRecoveryWithNoProducts_withoutPreviousAssistantText_usesStoredQuestionWhenAvailable() {
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")"), eq(null), any(), eq(null)))
                .thenReturn(SearchResult.of(List.of(), null, 0));

        lastQuestionStore.rememberFromResponse("v1", AgentResponse.builder()
                .text("x")
                .conversationId("c")
                .clarifyingQuestion("Which storage condition should we use?")
                .build());

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "No products found.", "c-gcp", "rice", "INTENT_REFINEMENT", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of(
                "visitorId", "v1",
                "previousRefinedQuery", "rice",
                "previousSuggestedAnswers",
                List.of(
                        Map.of("displayText", "Ambient", "value", "S"),
                        Map.of("displayText", "Dry storage", "value", "D")));

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "D", ctx);

        assertThat(aug.products()).isEmpty();
        assertThat(aug.text()).contains("Which storage condition should we use?");
        assertThat(aug.text()).doesNotContain("What type of stock do you prefer?");
        assertThat(aug.text()).contains("No products found for that option.");
        assertThat(aug.suggestedAnswersOverride()).isNotNull();
        assertThat(aug.suggestedAnswersOverride()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("S");
    }

    @Test
    void storageRecoveryWithNoProducts_withoutPreviousAssistantText_usesDefaultWhenStoreEmpty() {
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq("attributes.stockType: ANY(\"DRY_STORAGE\", \"D\")"), eq(null), any(), eq(null)))
                .thenReturn(SearchResult.of(List.of(), null, 0));

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "No products found.", "c-gcp", "rice", "INTENT_REFINEMENT", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of(
                "visitorId", "v1",
                "previousRefinedQuery", "rice",
                "previousSuggestedAnswers",
                List.of(
                        Map.of("displayText", "Ambient", "value", "S"),
                        Map.of("displayText", "Dry storage", "value", "D")));

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "D", ctx);

        assertThat(aug.products()).isEmpty();
        assertThat(aug.text()).contains("What type of stock do you prefer?");
        assertThat(aug.text()).contains("No products found for that option.");
        assertThat(aug.suggestedAnswersOverride()).isNotNull();
        assertThat(aug.suggestedAnswersOverride()).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("S");
    }

    @Test
    void vaisrAlreadySaysNoProductsDoesNotAppendAgain() {
        when(searchClient.searchWithPagination(
                eq("p"), eq("b"), eq("rice"), any(), eq(null), eq(null), any(), eq(null)))
                .thenReturn(SearchResult.of(List.of(), null, 0));

        var vaisr = new ConversationalCommerceClient.ConversationalCommerceResult(
                "No products found.", "c-gcp", "rice", "PRODUCT_DETAILS", "agent", "{}", List.of());
        var ctx = Map.<String, Object>of("visitorId", "v1");

        VaisrRetailProductResolver.Augmentation aug = resolver.resolve(vaisr, "detail", ctx);

        assertThat(aug.products()).isEmpty();
        assertThat(aug.text()).isEqualTo("No products found.");
    }
}
