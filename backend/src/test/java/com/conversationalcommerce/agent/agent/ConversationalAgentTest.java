package com.conversationalcommerce.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ConversationalAgent interface contract.
 * Uses a simple stub implementation to verify the interface behavior.
 */
class ConversationalAgentTest {

    @Test
    void agentReturnsResponseWithRequiredFields() {
        ConversationalAgent agent = new StubConversationalAgent("stub-1");
        AgentResponse response = agent.sendMessage("", "hello", Map.of());

        assertThat(agent.getAgentId()).isEqualTo("stub-1");
        assertThat(response).isNotNull();
        assertThat(response.text()).isNotNull();
    }

    @Test
    void agentPreservesConversationIdAcrossTurns() {
        ConversationalAgent agent = new StubConversationalAgent("stub-1");
        AgentResponse first = agent.sendMessage("", "first message", Map.of());
        AgentResponse second = agent.sendMessage(first.conversationId(), "follow-up", Map.of());

        assertThat(first.conversationId()).isNotNull();
        assertThat(second.conversationId()).isEqualTo(first.conversationId());
    }

    private static class StubConversationalAgent implements ConversationalAgent {
        private final String agentId;
        private String conversationId = "conv-" + System.currentTimeMillis();

        StubConversationalAgent(String agentId) {
            this.agentId = agentId;
        }

        @Override
        public AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context) {
            if (conversationId != null && !conversationId.isEmpty()) {
                this.conversationId = conversationId;
            }
            return AgentResponse.builder()
                    .text("Echo: " + message)
                    .conversationId(this.conversationId)
                    .build();
        }

        @Override
        public String getAgentId() {
            return agentId;
        }
    }
}
