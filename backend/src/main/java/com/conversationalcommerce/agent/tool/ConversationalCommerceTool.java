package com.conversationalcommerce.agent.tool;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.google.adk.tools.Annotations.Schema;

import java.util.Map;
import java.util.Objects;

/**
 * ADK FunctionTool that invokes the Conversational Commerce API for product search.
 * Used by the ADK orchestrator agent when it needs to search products.
 * <p>
 * Uses static configuration (set once at startup via {@link #configure}) because
 * ADK FunctionTool.create() requires static methods. Configured in AdkConfig @PostConstruct.
 */
public class ConversationalCommerceTool {

    private static ConversationalCommerceClient client;
    private static String placement;
    private static String branch;
    private static String defaultVisitorId = "default-visitor";

    public static void configure(ConversationalCommerceClient c, String p, String b, String v) {
        client = c;
        placement = p;
        branch = b;
        defaultVisitorId = v != null ? v : defaultVisitorId;
    }

    @Schema(description = "Search for products using the Conversational Commerce API. Use this when the user wants to find, browse, or search for products. Provide the user's search query.")
    public static Map<String, Object> searchProducts(
            @Schema(description = "The product search query from the user", name = "query")
            String query) {
        if (client == null || placement == null || branch == null) {
            return Map.of("status", "error", "message", "Conversational Commerce not configured");
        }
        try {
            var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                    placement, branch, query, defaultVisitorId, "", null);
            var result = client.search(request);
            return Map.of(
                    "status", "success",
                    "text", result.text(),
                    "refinedQuery", result.refinedQuery() != null ? result.refinedQuery() : query,
                    "queryType", result.queryType() != null ? result.queryType() : "UNKNOWN"
            );
        } catch (Exception e) {
            return Map.of("status", "error", "message", Objects.toString(e.getMessage(), "Unknown error"));
        }
    }
}
