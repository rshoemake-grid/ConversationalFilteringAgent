package com.conversationalcommerce.agent.tool;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalCommerceToolPayloadTest {

    @Test
    void searchSuccessPayload_includesSuggestedAnswersForAdk() {
        var result = new ConversationalCommerceClient.ConversationalCommerceResult(
                "What kind?",
                "c1",
                "rice",
                "GENERAL_QUESTION",
                "agent",
                "{}",
                List.of(
                        new ConversationalCommerceClient.SuggestedAnswer("Long grain", "LONG"),
                        new ConversationalCommerceClient.SuggestedAnswer("Basmati", "BASMATI")));

        Map<String, Object> m = ConversationalCommerceTool.searchSuccessPayload(result, "rice");

        assertThat(m.get("status")).isEqualTo("success");
        assertThat(m.get("text")).isEqualTo("What kind?");
        assertThat(m.get("conversationId")).isEqualTo("c1");
        assertThat(m.get("refinedQuery")).isEqualTo("rice");
        assertThat(m.get("submittedQuery")).isEqualTo("rice");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> sa = (List<Map<String, String>>) m.get("suggestedAnswers");
        assertThat(sa).hasSize(2);
        assertThat(sa.get(0).get("displayText")).isEqualTo("Long grain");
        assertThat(sa.get(0).get("value")).isEqualTo("LONG");
        assertThat(sa.get(1).get("displayText")).isEqualTo("Basmati");
    }
}
