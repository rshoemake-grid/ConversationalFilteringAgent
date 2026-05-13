package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LastFilteringQuestionStoreTest {

    LastFilteringQuestionStore store;

    @BeforeEach
    void setUp() {
        store = new LastFilteringQuestionStore();
    }

    @Test
    void rememberFromResponse_prefersClarifyingQuestion() {
        store.rememberFromResponse("s1", AgentResponse.builder()
                .text("noise")
                .conversationId("c")
                .clarifyingQuestion("  Pick a storage type?  ")
                .build());

        assertThat(store.getLastQuestion("s1")).contains("Pick a storage type?");
    }

    @Test
    void rememberFromResponse_withStorageSuggestions_storesStockRelatedLineFromText() {
        var sas = List.of(new ConversationalCommerceClient.SuggestedAnswer("Ambient", "S"));
        store.rememberFromResponse("s2", AgentResponse.builder()
                .text("Line one.\nWhat stock do you need?\n")
                .conversationId("c")
                .suggestedAnswers(sas)
                .build());

        assertThat(store.getLastQuestion("s2")).contains("What stock do you need?");
    }

    @Test
    void getLastQuestion_unknownSession_empty() {
        assertThat(store.getLastQuestion("nope")).isEmpty();
    }

    @Test
    void rememberFromResponse_blankSession_noOp() {
        store.rememberFromResponse("  ", AgentResponse.builder()
                .clarifyingQuestion("Q?")
                .conversationId("c")
                .build());
        assertThat(store.getLastQuestion("")).isEmpty();
    }
}
