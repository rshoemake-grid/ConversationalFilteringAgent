package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.agent.ConversationalAgent;
import com.conversationalcommerce.agent.agent.ConversationalCommerceAdapter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Approach A: Conversational Commerce API as primary orchestrator.
 * Uses only the Conversational Commerce agent—no Gemini/ADK specialist delegation.
 */
@Service
public class ConvoCommerceOrchestrator implements ChatOrchestrator {

    private final ConversationalAgent conversationalCommerceAgent;

    public ConvoCommerceOrchestrator(ConversationalCommerceAdapter conversationalCommerceAgent) {
        this.conversationalCommerceAgent = conversationalCommerceAgent;
    }

    public AgentResponse process(String message, String conversationId, Map<String, Object> context) {
        return conversationalCommerceAgent.sendMessage(
                conversationId != null ? conversationId : "",
                message,
                context
        );
    }
}
