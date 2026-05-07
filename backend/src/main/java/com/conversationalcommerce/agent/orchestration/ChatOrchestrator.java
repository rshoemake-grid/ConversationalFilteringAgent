package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;

import java.util.Map;

/**
 * Interface for chat orchestration. Enables testability without mocking.
 */
public interface ChatOrchestrator {

    AgentResponse process(String message, String conversationId, Map<String, Object> context);
}
