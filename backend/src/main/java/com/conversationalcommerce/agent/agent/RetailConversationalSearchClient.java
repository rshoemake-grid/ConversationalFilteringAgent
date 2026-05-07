package com.conversationalcommerce.agent.agent;

import com.conversationalcommerce.agent.config.ConversationalCommerceConfig;
import com.google.cloud.retail.v2beta.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Real implementation using GCP Retail ConversationalSearchServiceClient.
 * Requires conversational-commerce.enabled=true and valid GCP credentials.
 */
@Component
@ConditionalOnExpression("@environment.getProperty('conversational-commerce.transport', 'rest') != 'rest'")
public class RetailConversationalSearchClient implements ConversationalCommerceClient {

    private final ConversationalSearchServiceClient client;
    private final ConversationalCommerceConfig config;

    public RetailConversationalSearchClient(ConversationalCommerceConfig config) throws Exception {
        this.config = config;
        this.client = ConversationalSearchServiceClient.create();
    }

    @Override
    public ConversationalCommerceResult search(ConversationalCommerceRequest request) {
        var req = ConversationalSearchRequest.newBuilder()
                .setPlacement(request.placement())
                .setBranch(request.branch())
                .setQuery(request.query())
                .setVisitorId(request.visitorId())
                .setConversationId(request.conversationId() != null ? request.conversationId() : "")
                .setConversationalFilteringSpec(
                        ConversationalSearchRequest.ConversationalFilteringSpec.getDefaultInstance())
                .build();

        List<String> textParts = new ArrayList<>();
        String conversationId = "";
        String refinedQuery = "";
        String queryType = "";

        try {
            Iterable<ConversationalSearchResponse> stream = client.conversationalSearchCallable().call(req);
            for (ConversationalSearchResponse response : stream) {
                if (!response.getConversationId().isEmpty()) {
                    conversationId = response.getConversationId();
                }
                if (response.getRefinedSearchCount() > 0 && !response.getRefinedSearch(0).getQuery().isEmpty()) {
                    refinedQuery = response.getRefinedSearch(0).getQuery();
                }
                if (!response.getUserQueryTypesList().isEmpty()) {
                    queryType = response.getUserQueryTypes(0);
                }
                if (!response.getConversationalTextResponse().isEmpty()) {
                    String part = response.getConversationalTextResponse().trim();
                    if (!part.isEmpty() && (textParts.isEmpty() || !textParts.get(textParts.size() - 1).equals(part))) {
                        textParts.add(part);
                    }
                }
            }
        } finally {
            // Client is long-lived, don't close here
        }

        String text = String.join(" ", textParts);
        String source = "agent";
        if (text.isEmpty()) {
            text = refinedQuery != null && !refinedQuery.isEmpty()
                    ? "Searching for: " + refinedQuery
                    : "I didn't understand your response.";
            source = "app";
        }

        return new ConversationalCommerceResult(text, conversationId, refinedQuery, queryType, source, null, List.of());
    }
}
