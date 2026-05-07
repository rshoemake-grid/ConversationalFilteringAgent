package com.conversationalcommerce.agent.agent;

import java.util.Map;

/**
 * Generic interface for conversational agents (GCP Conversational Commerce, ADK agents, etc.).
 * Enables dependency inversion and pluggable agent implementations.
 */
public interface ConversationalAgent {

    /**
     * Send a message to the agent and receive a response.
     *
     * @param conversationId session identifier for multi-turn (empty for first message)
     * @param message        user message
     * @param context        optional context (visitorId, userId, etc.)
     * @return agent response
     */
    AgentResponse sendMessage(String conversationId, String message, Map<String, Object> context);

    /**
     * Unique identifier for this agent.
     */
    String getAgentId();
}
