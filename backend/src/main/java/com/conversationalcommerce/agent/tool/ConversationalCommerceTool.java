package com.conversationalcommerce.agent.tool;

import com.conversationalcommerce.agent.agent.ConversationalCommerceClient;
import com.conversationalcommerce.agent.orchestration.AdkToolRequestContext;
import com.conversationalcommerce.agent.orchestration.ContextUtils;
import com.google.adk.tools.Annotations.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(ConversationalCommerceTool.class);

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
            String query,
            @Schema(
                    description = "VAISR conversation id from the previous searchProducts response (field conversationId). Omit on the first search.",
                    optional = true)
            String vaisrConversationId) {
        if (client == null || placement == null || branch == null) {
            return Map.of("status", "error", "message", "Conversational Commerce not configured");
        }
        if (query == null || query.isBlank()) {
            log.warn("searchProducts invoked with empty query (vaisrConversationId present={})", vaisrConversationId != null && !vaisrConversationId.isBlank());
            return Map.of("status", "error", "message", "Search query was empty; pass the user's product request as the query argument.");
        }
        String q = query.trim();
        try {
            String visitorId = resolveVisitorId();
            String conv = resolveVaisrConversationId(vaisrConversationId);
            var request = new ConversationalCommerceClient.ConversationalCommerceRequest(
                    placement, branch, q, visitorId, conv, null);
            var result = client.search(request);
            return searchSuccessPayload(result, q);
        } catch (Exception e) {
            log.warn("searchProducts GCP call failed for query \"{}\": {}", q, e.toString(), e);
            String hint = Objects.toString(e.getMessage(), "Unknown error");
            if (hint.length() > 600) {
                hint = hint.substring(0, 600) + "...";
            }
            return Map.of("status", "error", "message", hint);
        }
    }

    private static String resolveVisitorId() {
        var holder = AdkToolRequestContext.current();
        if (holder == null || holder.context() == null) {
            return defaultVisitorId;
        }
        return ContextUtils.getVisitorId(holder.context(), defaultVisitorId);
    }

    private static String resolveVaisrConversationId(String toolParam) {
        if (toolParam != null && !toolParam.isBlank()) {
            return toolParam.trim();
        }
        var holder = AdkToolRequestContext.current();
        if (holder == null || holder.bridge() == null) {
            return "";
        }
        String fromBridge = holder.bridge().getGcpConversationId(holder.adkSessionId());
        return fromBridge != null ? fromBridge : "";
    }

    /**
     * Payload returned to Gemini must include {@code suggestedAnswers} from VAISR so the model can mirror real
     * filter options instead of inventing its own (e.g. wrong rice varieties). Same structure is read from tool
     * events in {@link com.conversationalcommerce.agent.orchestration.AdkOrchestrator} for the HTTP response.
     */
    static Map<String, Object> searchSuccessPayload(
            ConversationalCommerceClient.ConversationalCommerceResult result, String query) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "success");
        m.put("text", result.text());
        m.put("conversationId", result.conversationId() != null ? result.conversationId() : "");
        m.put("refinedQuery", result.refinedQuery() != null ? result.refinedQuery() : query);
        m.put("submittedQuery", query);
        m.put("queryType", result.queryType() != null ? result.queryType() : "UNKNOWN");
        List<Map<String, String>> answers = new ArrayList<>();
        if (result.suggestedAnswers() != null) {
            for (ConversationalCommerceClient.SuggestedAnswer s : result.suggestedAnswers()) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("displayText", s.displayText());
                row.put("value", s.value());
                answers.add(row);
            }
        }
        m.put("suggestedAnswers", answers);
        return m;
    }
}
