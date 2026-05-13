package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarifyingFollowUpPolicyTest {

    @Test
    void effectiveCount_usesTotalSizeWhenKnown() {
        var products = List.of(AgentResponse.ProductResult.of("1", "a", "", "$1", null));
        var sr = SearchResult.of(products, null, 120);
        assertThat(ClarifyingFollowUpPolicy.effectiveProductCountForClarifying(products, sr)).isEqualTo(120);
    }

    @Test
    void effectiveCount_fallsBackToPageSizeWhenTotalUnknown() {
        var products = List.of(
                AgentResponse.ProductResult.of("1", "a", "", "$1", null),
                AgentResponse.ProductResult.of("2", "b", "", "$2", null));
        var sr = SearchResult.of(products);
        assertThat(ClarifyingFollowUpPolicy.effectiveProductCountForClarifying(products, sr)).isEqualTo(2);
    }

    @Test
    void withoutStorageSuggestions_removesStockCodes() {
        var list = List.of(
                new ConversationalCommerceClient.SuggestedAnswer("Ambient", "S"),
                new ConversationalCommerceClient.SuggestedAnswer("Nike", "NIKE"));
        var kept = ClarifyingFollowUpPolicy.withoutStorageSuggestions(list);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).value()).isEqualTo("NIKE");
    }

    @Test
    void withoutStorageSuggestions_removesNonRefrigeratedCode() {
        var list = List.of(new ConversationalCommerceClient.SuggestedAnswer("N", "N"));
        assertThat(ClarifyingFollowUpPolicy.withoutStorageSuggestions(list)).isEmpty();
    }

    @Test
    void clarifyingQuestionImpliesStorageChoice_detectsStockAndStorageWording() {
        assertThat(ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice("What type of stock do you prefer?"))
                .isTrue();
        assertThat(ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice("  Dry STORAGE preference?  "))
                .isTrue();
        assertThat(ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice("What style or color?")).isFalse();
        assertThat(ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice(null)).isFalse();
        assertThat(ClarifyingFollowUpPolicy.clarifyingQuestionImpliesStorageChoice("   ")).isFalse();
    }
}
