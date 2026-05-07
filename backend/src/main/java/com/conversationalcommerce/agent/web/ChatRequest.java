package com.conversationalcommerce.agent.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Chat request payload")
public record ChatRequest(
        @NotNull @Schema(description = "Orchestration mode", example = "adk_orchestrator") OrchestrationMode mode,
        @Schema(description = "User message (required if no image)", example = "What running shoes do you have?") String message,
        @Schema(description = "Conversation ID for multi-turn") String conversationId,
        @Schema(description = "Session/visitor ID") String sessionId,
        @Schema(description = "Optional image as base64 (multimodal search)") String imageBase64,
        @Schema(description = "Max suggested answers to return (null = no limit; frontend slices for display)") Integer maxSuggestedAnswers,
        @Schema(description = "Previous assistant message text (for no-products fallback when user retries a suggested answer)") String previousAssistantText,
        @Schema(description = "Previous assistant suggested answers (for no-products fallback)") java.util.List<SuggestedAnswerInput> previousSuggestedAnswers,
        @Schema(description = "Previous refined query (for RETAIL_IRRELEVANT recovery when user says Any/no preference)") String previousRefinedQuery,
        @Schema(description = "Token for load-more (next page of products)") String productPageToken,
        @Schema(description = "Filter from previous product response (for load-more)") String previousProductFilter,
        @Schema(description = "Products per page (overrides config; null = use config default)") Integer productPageSize
) {
    public ChatRequest {
        message = message != null ? message : "";
        imageBase64 = imageBase64 != null && imageBase64.isBlank() ? null : imageBase64;
        previousSuggestedAnswers = previousSuggestedAnswers != null ? previousSuggestedAnswers : java.util.List.of();
        productPageToken = productPageToken != null && productPageToken.isBlank() ? null : productPageToken;
        previousProductFilter = previousProductFilter != null && previousProductFilter.isBlank() ? null : previousProductFilter;
    }

    @Schema(description = "Suggested answer for context (displayText, value)")
    public record SuggestedAnswerInput(String displayText, String value) {}

    @AssertTrue(message = "Either message, imageBase64, or productPageToken must be provided")
    public boolean isValidInput() {
        return (message != null && !message.isBlank()) || (imageBase64 != null && !imageBase64.isBlank())
                || (productPageToken != null && !productPageToken.isBlank());
    }

    public enum OrchestrationMode {
        convo_commerce,   // Approach A: Convo Commerce as orchestrator
        adk_orchestrator  // Approach B: ADK agent as orchestrator
    }
}
