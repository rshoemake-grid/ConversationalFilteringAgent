package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdkOrchestratorSuggestedAnswersTest {

    @Test
    void replaceSuggestedAnswersFromToolMap_parsesVaisrShape() {
        List<ConversationalCommerceClient.SuggestedAnswer> out = new ArrayList<>();
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put(
                "suggestedAnswers",
                List.of(
                        Map.of("displayText", "Ambient", "value", "S"),
                        Map.of("displayText", "Frozen", "value", "F")));

        AdkOrchestrator.replaceSuggestedAnswersFromToolMap(tool, out);

        assertThat(out).extracting(ConversationalCommerceClient.SuggestedAnswer::displayText)
                .containsExactly("Ambient", "Frozen");
        assertThat(out).extracting(ConversationalCommerceClient.SuggestedAnswer::value)
                .containsExactly("S", "F");
    }

    @Test
    void replaceSuggestedAnswersFromToolMap_clearsOnEmptyList() {
        List<ConversationalCommerceClient.SuggestedAnswer> out = new ArrayList<>();
        out.add(new ConversationalCommerceClient.SuggestedAnswer("Old", "X"));
        AdkOrchestrator.replaceSuggestedAnswersFromToolMap(Map.of("suggestedAnswers", List.of()), out);
        assertThat(out).isEmpty();
    }

    @Test
    void toolMapToResult_fallsBackToSubmittedQueryWhenRefinedEmpty() {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("text", "x");
        tool.put("conversationId", "c");
        tool.put("refinedQuery", "");
        tool.put("submittedQuery", "rice");
        tool.put("queryType", "GENERAL_QUESTION");
        tool.put("suggestedAnswers", List.of());

        var r = AdkOrchestrator.toolMapToResult(tool);
        assertThat(r.refinedQuery()).isEqualTo("rice");
    }

    @Test
    void mergeAdkTextWithSearchFailure_replacesModelTextWhenSearchFailedWithoutRetry() {
        String t = AdkOrchestrator.mergeAdkTextWithSearchFailure(
                "I am sorry, an error occurred while searching for products.",
                "403",
                null);
        assertThat(t)
                .doesNotContain("sorry")
                .contains("Google Retail Conversational Commerce API")
                .contains("GOOGLE_APPLICATION_CREDENTIALS")
                .contains("From the service:")
                .contains("403");
    }

    @Test
    void mergeAdkTextWithSearchFailure_keepsModelTextWhenLaterSearchSucceeded() {
        String t = AdkOrchestrator.mergeAdkTextWithSearchFailure(
                "Here are some options.",
                "first call failed",
                Map.of("status", "success"));
        assertThat(t).isEqualTo("Here are some options.");
    }

    @Test
    void mergeAdkTextWithSearchFailure_notConfiguredUsesDedicatedMessage() {
        String t = AdkOrchestrator.mergeAdkTextWithSearchFailure(
                "x", "Conversational Commerce not configured", null);
        assertThat(t).contains("not configured on the server").contains("application.yml");
    }

    @Test
    void mergeAdkTextWithSearchFailure_emptyQueryUsesDedicatedMessage() {
        String t = AdkOrchestrator.mergeAdkTextWithSearchFailure(
                "x", "Search query was empty; pass the user's product request as the query argument.", null);
        assertThat(t).contains("without a query");
    }

    @Test
    void mergeAdkTextWithSearchFailure_defaultWhenEmptyModelTextAndNoSearchError() {
        String t = AdkOrchestrator.mergeAdkTextWithSearchFailure("", null, null);
        assertThat(t).contains("I'm here to help");
    }
}
