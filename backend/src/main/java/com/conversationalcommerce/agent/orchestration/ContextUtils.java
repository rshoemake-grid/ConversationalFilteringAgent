package com.conversationalcommerce.agent.orchestration;

import java.util.Map;

/**
 * Shared utilities for extracting context values from orchestrator context maps.
 */
public final class ContextUtils {

    private ContextUtils() {}

    private static final String DEFAULT_USER_PREFIX = "user-";

    /**
     * Extracts visitorId from context, or generates a default if absent.
     */
    public static String getVisitorId(Map<String, Object> context, String defaultVisitorId) {
        if (context != null && context.containsKey("visitorId")) {
            Object v = context.get("visitorId");
            return v != null ? v.toString() : defaultVisitorId;
        }
        return defaultVisitorId != null ? defaultVisitorId : DEFAULT_USER_PREFIX + System.currentTimeMillis();
    }

    /**
     * Chat session key for server-side per-session state (matches {@link OrchestratorService} context).
     */
    public static String getSessionKey(Map<String, Object> context) {
        if (context == null) {
            return "";
        }
        Object sid = context.get("sessionId");
        if (sid != null && !sid.toString().isBlank()) {
            return sid.toString().trim();
        }
        Object vid = context.get("visitorId");
        if (vid != null && !vid.toString().isBlank()) {
            return vid.toString().trim();
        }
        return "";
    }
}
