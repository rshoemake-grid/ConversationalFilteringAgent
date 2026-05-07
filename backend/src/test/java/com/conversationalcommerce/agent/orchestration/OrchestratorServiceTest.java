package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.web.ChatRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorServiceTest {

    private StubChatOrchestrator convoOrchestrator;
    private StubChatOrchestrator adkOrchestrator;
    private OrchestratorService orchestratorService;

    @BeforeEach
    void setUp() {
        convoOrchestrator = new StubChatOrchestrator();
        adkOrchestrator = new StubChatOrchestrator();
        orchestratorService = new OrchestratorService(
                new ConvoCommerceOrchestratorWrapper(convoOrchestrator),
                new AdkOrchestrator(null, adkOrchestrator)
        );
    }

    @Test
    void process_convoCommerceMode_delegatesToConvoCommerceOrchestrator() {
        convoOrchestrator.setNextResponse(AgentResponse.builder()
                .text("Convo response")
                .conversationId("conv-1")
                .build());

        AgentResponse result = orchestratorService.process(
                ChatRequest.OrchestrationMode.convo_commerce,
                "hello",
                "conv-0",
                "session-123",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(result.text()).isEqualTo("Convo response");
        assertThat(result.conversationId()).isEqualTo("conv-1");
        assertThat(convoOrchestrator.lastMessage).isEqualTo("hello");
        assertThat(convoOrchestrator.lastConversationId).isEqualTo("conv-0");
    }

    @Test
    void process_adkOrchestratorMode_delegatesToAdkOrchestrator() {
        adkOrchestrator.setNextResponse(AgentResponse.builder()
                .text("ADK response")
                .conversationId("adk-session")
                .build());

        AgentResponse result = orchestratorService.process(
                ChatRequest.OrchestrationMode.adk_orchestrator,
                "search shoes",
                "conv-1",
                "visitor-456",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(result.text()).isEqualTo("ADK response");
        assertThat(result.conversationId()).isEqualTo("adk-session");
        assertThat(adkOrchestrator.lastMessage).isEqualTo("search shoes");
        assertThat(adkOrchestrator.lastConversationId).isEqualTo("conv-1");
    }

    @Test
    void process_passesContextWithVisitorIdAndSessionId() {
        convoOrchestrator.setNextResponse(AgentResponse.builder().text("ok").conversationId("x").build());

        orchestratorService.process(
                ChatRequest.OrchestrationMode.convo_commerce,
                "hi",
                null,
                "my-session-id",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(convoOrchestrator.lastContext.get("visitorId")).isEqualTo("my-session-id");
        assertThat(convoOrchestrator.lastContext.get("sessionId")).isEqualTo("my-session-id");
    }

    @Test
    void process_passesOrchestrationModeInContext() {
        convoOrchestrator.setNextResponse(AgentResponse.builder().text("ok").conversationId("x").build());

        orchestratorService.process(
                ChatRequest.OrchestrationMode.convo_commerce,
                "hi",
                null,
                "session-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(convoOrchestrator.lastContext.get("orchestrationMode")).isEqualTo("convo_commerce");
    }

    @Test
    void process_passesMaxSuggestedAnswersInContextWhenProvided() {
        convoOrchestrator.setNextResponse(AgentResponse.builder().text("ok").conversationId("x").build());

        orchestratorService.process(
                ChatRequest.OrchestrationMode.convo_commerce,
                "hi",
                null,
                "session-1",
                null,
                5,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(convoOrchestrator.lastContext.get("maxSuggestedAnswers")).isEqualTo(5);
    }

    private static class StubChatOrchestrator implements ChatOrchestrator {
        String lastMessage;
        String lastConversationId;
        Map<String, Object> lastContext;
        AgentResponse nextResponse;

        void setNextResponse(AgentResponse r) {
            this.nextResponse = r;
        }

        @Override
        public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
            this.lastMessage = message;
            this.lastConversationId = conversationId;
            this.lastContext = context;
            return nextResponse != null ? nextResponse : AgentResponse.builder().text("").conversationId("").build();
        }
    }

    /** Wraps ChatOrchestrator as ConvoCommerceOrchestrator for OrchestratorService constructor. */
    private static class ConvoCommerceOrchestratorWrapper extends ConvoCommerceOrchestrator {
        private final ChatOrchestrator delegate;

        ConvoCommerceOrchestratorWrapper(ChatOrchestrator delegate) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
            return delegate.process(message, conversationId, context);
        }
    }

}
