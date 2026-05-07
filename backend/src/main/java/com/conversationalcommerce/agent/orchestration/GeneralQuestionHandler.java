package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;

import java.util.Map;

/**
 * Handles general questions (store hours, policies). Implemented by ADK specialist or stubs.
 */
public interface GeneralQuestionHandler {

    AgentResponse handle(String message, Map<String, Object> context);
}
