package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.config.GeneralQuestionAgentConfig;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Approach A specialist: Handles general questions (store hours, policies, etc.)
 * when Conversational Commerce returns GENERAL_QUESTION intent.
 * Uses ADK LlmAgent for natural language handling.
 */
@Service
public class GeneralQuestionSpecialist implements GeneralQuestionHandler {

    private final GeneralQuestionAgentConfig.GeneralQuestionSpecialistRunner runner;

    public GeneralQuestionSpecialist(GeneralQuestionAgentConfig.GeneralQuestionSpecialistRunner runner) {
        this.runner = runner;
    }

    public AgentResponse handle(String message, Map<String, Object> context) {
        String userId = ContextUtils.getVisitorId(context, null);
        return runner.run(message, userId);
    }
}
