package com.conversationalcommerce.agent.orchestration;

import com.conversationalcommerce.agent.agent.AgentResponse;
import com.conversationalcommerce.agent.web.ChatRequest;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Routes chat requests to the appropriate orchestrator based on mode.
 */
@Service
public class OrchestratorService {

    private final ChatOrchestrator convoCommerceOrchestrator;
    private final ChatOrchestrator adkOrchestrator;

    public OrchestratorService(ConvoCommerceOrchestrator convoCommerceOrchestrator, AdkOrchestrator adkOrchestrator) {
        this.convoCommerceOrchestrator = convoCommerceOrchestrator;
        this.adkOrchestrator = adkOrchestrator;
    }

    public AgentResponse process(ChatRequest.OrchestrationMode mode, String message, String conversationId, String sessionId,
                                 String imageBase64, Integer maxSuggestedAnswers, String previousAssistantText,
                                 java.util.List<ChatRequest.SuggestedAnswerInput> previousSuggestedAnswers,
                                 String previousRefinedQuery, String productPageToken, String previousProductFilter,
                                 Integer productPageSize) {
        var context = new java.util.HashMap<String, Object>(Map.of("visitorId", sessionId, "sessionId", sessionId));
        context.put("orchestrationMode", mode.name());
        if (imageBase64 != null && !imageBase64.isBlank()) {
            context.put("imageBase64", imageBase64);
        }
        if (maxSuggestedAnswers != null && maxSuggestedAnswers > 0) {
            context.put("maxSuggestedAnswers", maxSuggestedAnswers);
        }
        if (previousAssistantText != null && !previousAssistantText.isBlank()) {
            context.put("previousAssistantText", previousAssistantText);
        }
        if (previousSuggestedAnswers != null && !previousSuggestedAnswers.isEmpty()) {
            context.put("previousSuggestedAnswers", previousSuggestedAnswers.stream()
                    .map(sa -> Map.<String, String>of("displayText", sa.displayText() != null ? sa.displayText() : "", "value", sa.value() != null ? sa.value() : ""))
                    .toList());
        }
        if (previousRefinedQuery != null && !previousRefinedQuery.isBlank()) {
            context.put("previousRefinedQuery", previousRefinedQuery.trim());
        }
        if (productPageToken != null && !productPageToken.isBlank()) {
            context.put("productPageToken", productPageToken.trim());
        }
        if (previousProductFilter != null && !previousProductFilter.isBlank()) {
            context.put("previousProductFilter", previousProductFilter.trim());
        }
        if (productPageSize != null && productPageSize > 0) {
            context.put("productPageSize", productPageSize);
        }

        return switch (mode) {
            case convo_commerce -> convoCommerceOrchestrator.process(message, conversationId, context);
            case adk_orchestrator -> adkOrchestrator.process(message, conversationId, context);
        };
    }
}
